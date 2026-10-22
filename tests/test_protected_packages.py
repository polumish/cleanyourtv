"""tools/protected-packages.txt: packages no list may disable (Play Store, Play Services, the shell, ...).

disable.sh skips a listed protected package and disables the rest; FORCE_PROTECTED=1 turns the guard
off; a missing list file stops the run before anything reaches the TV; check.py --fix applies the same
list; and no list in examples/ names one, so the guard never changes what a community list does.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import check  # noqa: E402

PLAY = "com.android.vending"
LIST = f"# My TV\ncom.example.before\n{PLAY}  # store\ncom.example.after\n"
BATCH = ["pm disable-user --user 0 com.example.before",
         f"pm disable-user --user 0 {PLAY}",
         "pm disable-user --user 0 com.example.after",
         "pm list packages -d"]


class ProtectedPackagesTest(unittest.TestCase):
    def run_disable(self, extra_env=None, with_file=True):
        """Run the real disable.sh against a fake tv.py client that answers like a healthy TV."""
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "repo"
            tools = repo / "tools"
            tools.mkdir(parents=True)
            shutil.copy2(ROOT / "tools" / "disable.sh", tools / "disable.sh")
            if with_file:
                shutil.copy2(ROOT / "tools" / "protected-packages.txt", tools / "protected-packages.txt")
            client = tools / "venv" / "bin" / "python"
            client.parent.mkdir(parents=True)
            client.write_text(
                "#!/bin/sh\n"
                'case "$*" in\n'
                "  *TV_INPUT_HARDWARE*) true ;;\n"
                '  *resolve-activity*) echo "com.google.android.tvlauncher/.MainActivity" ;;\n'
                '  *query-activities*) echo "  com.google.android.tvlauncher/.MainActivity" ;;\n'
                '  *getprop*) echo 1 ;;\n'
                '  *) cat > "$CAPTURE" ;;\n'
                "esac\n")
            client.chmod(0o755)
            (repo / "debloat.txt").write_text(LIST)
            capture = Path(tmp) / "commands.txt"
            env = dict(os.environ, CAPTURE=str(capture), **(extra_env or {}))
            result = subprocess.run(["bash", str(tools / "disable.sh"), "debloat.txt"], cwd=repo,
                                    env=env, capture_output=True, text=True, stdin=subprocess.DEVNULL)
            commands = capture.read_text().splitlines() if capture.exists() else []
            return result, commands

    def test_protected_package_is_skipped_and_the_rest_is_disabled(self):
        result, commands = self.run_disable()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(commands, ["pm disable-user --user 0 com.example.before",
                                    "pm disable-user --user 0 com.example.after",
                                    "pm list packages -d"])
        self.assertIn(f"Skipping {PLAY}", result.stderr)
        self.assertIn("FORCE_PROTECTED=1", result.stderr)

    def test_force_disables_it_anyway(self):
        result, commands = self.run_disable({"FORCE_PROTECTED": "1"})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(commands, BATCH)
        self.assertNotIn("Skipping", result.stderr)

    def test_missing_file_stops_before_the_tv_is_touched(self):
        result, commands = self.run_disable(with_file=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(commands, [])
        self.assertIn("protected-packages.txt", result.stderr)

    def test_only_exact_names_are_matched(self):
        # A vendor package that merely contains a protected name must not be caught.
        self.assertNotIn("com.android.vending.billing.fake", check.protected_packages())
        self.assertIn(PLAY, check.protected_packages())
        self.assertIn("com.google.android.gms", check.protected_packages())

    def test_file_is_clean(self):
        names = check.protected_packages()
        self.assertGreaterEqual(len(names), 10)
        for name in names:
            self.assertRegex(name, r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")

    def test_check_fix_applies_the_same_list(self):
        skipped = check.safety_checks(_NoDevice(), [PLAY, "com.example.after"])
        self.assertEqual([name for name, _ in skipped], [PLAY])
        self.assertIn("FORCE_PROTECTED=1", skipped[0][1])
        old = os.environ.get("FORCE_PROTECTED")
        os.environ["FORCE_PROTECTED"] = "1"
        try:
            self.assertEqual(check.safety_checks(_NoDevice(), [PLAY]), [])
        finally:
            if old is None:
                del os.environ["FORCE_PROTECTED"]
            else:
                os.environ["FORCE_PROTECTED"] = old

    def test_no_example_list_names_a_protected_package(self):
        protected = check.protected_packages()
        hits = []
        for path in sorted((ROOT / "examples").glob("*/debloat.txt")):
            for line in path.read_text(encoding="utf-8-sig").splitlines():
                name = line.split("#", 1)[0].strip().split(" ")[0]
                if name in protected:
                    hits.append(f"{path.relative_to(ROOT)}: {name}")
        self.assertEqual(hits, [])


class _NoDevice:
    """A device that answers every query with nothing, so only the protected-list check has a say."""

    def shell(self, *args, **kwargs):
        return ""


if __name__ == "__main__":
    unittest.main()

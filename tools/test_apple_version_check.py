from pathlib import Path
import plistlib
import tempfile
import unittest

from apple_version_check import declared_version, verify


class AppleVersionCheckTests(unittest.TestCase):
    def test_project_version_matches_built_bundle(self):
        project = Path(__file__).resolve().parents[1] / "ios/project.yml"
        version, build = declared_version(project)
        self.assertRegex(version, r"^[0-9]+(?:\.[0-9]+){2}$")
        self.assertGreater(int(build), 0)
        with tempfile.TemporaryDirectory() as directory:
            plist = Path(directory) / "Info.plist"
            with plist.open("wb") as stream:
                plistlib.dump({"CFBundleShortVersionString": version, "CFBundleVersion": build}, stream)
            self.assertEqual(verify(project, plist), (version, build))
            with plist.open("wb") as stream:
                plistlib.dump({"CFBundleShortVersionString": version, "CFBundleVersion": "0"}, stream)
            with self.assertRaisesRegex(ValueError, "does not match"):
                verify(project, plist)

    def test_rejects_ambiguous_version_declarations(self):
        with tempfile.TemporaryDirectory() as directory:
            project = Path(directory) / "project.yml"
            project.write_text("    MARKETING_VERSION: '1.2.3'\n    MARKETING_VERSION: '2.0.0'\n    CURRENT_PROJECT_VERSION: '1'\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "exactly one"):
                declared_version(project)


if __name__ == "__main__":
    unittest.main()

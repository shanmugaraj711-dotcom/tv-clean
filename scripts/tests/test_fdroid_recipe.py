"""Checks that keep F-Droid's update check working (run in CI by .github/workflows/pr.yml).

F-Droid's recipe for this app (fdroiddata metadata/tv.cleanyour.app.yml, our copy in
docs/fdroid/tv.cleanyour.app.yml) finds a new version with

  UpdateCheckData: app/build.gradle.kts|versionCode\\s*=\\s*(\\d+)|.|versionName\\s*=\\s*"([^"]+)"

that is: on each new v* tag, checkupdates reads app/build.gradle.kts and takes versionCode and
versionName from the first match of each regex. If a change to app/build.gradle.kts made either
regex stop matching (the version moved to another file, a variable, a different quote), F-Droid
would quietly stop seeing new releases. So this test fails first, here.

Run: python3 -m unittest discover -s scripts/tests"""
import os
import re
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
RECIPE = os.path.join(ROOT, "docs", "fdroid", "tv.cleanyour.app.yml")
# The line F-Droid's recipe carries, as written in fdroiddata.
UPDATE_CHECK_DATA = r'app/build.gradle.kts|versionCode\s*=\s*(\d+)|.|versionName\s*=\s*"([^"]+)"'


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def top_level(text, key):
    """The value of a top-level `key: value` line of the recipe (no YAML parser needed)."""
    for line in text.splitlines():
        if line.startswith(key + ":"):
            return line[len(key) + 1:].strip()
    return None


class UpdateCheckData(unittest.TestCase):
    def test_our_recipe_carries_fdroids_line(self):
        self.assertEqual(UPDATE_CHECK_DATA, top_level(read(RECIPE), "UpdateCheckData"))

    def test_both_regexes_match_app_build_gradle(self):
        code_file, code_re, name_file, name_re = UPDATE_CHECK_DATA.split("|")
        self.assertEqual("app/build.gradle.kts", code_file)
        self.assertEqual(".", name_file, "'.' means the same file")
        gradle = read(os.path.join(ROOT, code_file))
        code = re.search(code_re, gradle)
        name = re.search(name_re, gradle)
        self.assertIsNotNone(code, f"F-Droid's versionCode regex no longer matches {code_file}")
        self.assertIsNotNone(name, f"F-Droid's versionName regex no longer matches {code_file}")
        # The first match is the app's own version (the line checkupdates reads), the same numbers
        # the recipe's newest build entry and CurrentVersion name.
        recipe = read(RECIPE)
        self.assertEqual(top_level(recipe, "CurrentVersionCode"), code.group(1))
        self.assertEqual(top_level(recipe, "CurrentVersion"), name.group(1))
        self.assertRegex(name.group(1), r"^[0-9]+\.[0-9]+\.[0-9]+(\.[0-9]+)?$")


class AntiFeatures(unittest.TestCase):
    # F-Droid moved NonFreeNet to the app level: it is said once for the app, not per build entry.
    def test_nonfreenet_is_app_level(self):
        recipe = read(RECIPE)
        self.assertTrue(recipe.startswith("AntiFeatures:\n  NonFreeNet:\n"), recipe[:80])
        self.assertNotIn("    antifeatures:", recipe)


if __name__ == "__main__":
    unittest.main()

import os
import unittest
import zipfile

out_dir = None
dist_dir = None
build = None
aswb = False


class StudioTests(unittest.TestCase):
  """Performs basic tests on studio artifacts.

  This will eventually replace /tools/idea/test_studio.py, currently
  only checking file lists.
  """

  def test_studio_files(self):

    platforms = ["linux", "win", "mac"]
    actual = {}
    for platform in platforms:
      name = "tools/adt/idea/studio/android-studio.%s.zip" % platform
      with zipfile.ZipFile(name) as file:
        actual[platform] = sorted(file.namelist())

    expected = {}
    for platform in platforms:
      with open("tools/adt/idea/studio/tests/expected_%s.txt" % platform, "r") as txt:
        expected[platform] = [line.strip() for line in sorted(txt.readlines())]

    for platform in platforms:
      if expected != actual:
        undeclared_dir = os.getenv("TEST_UNDECLARED_OUTPUTS_DIR")
        with open("%s/expected_%s.txt" % (undeclared_dir, platform), "w") as new_ex:
          new_ex.writelines([line + "\n" for line in actual[platform]])
        print("You can find the newly expected file in the undeclared output directory.")

    for platform in platforms:
      i = 0
      while i < len(actual[platform]) and i < len(expected[platform]):
        self.assertEqual(actual[platform][i], expected[platform][i], "#%d - Expected \"%s\", got \"%s\"" % (i, expected[platform][i], actual[platform][i]))
        i += 1
      self.assertEqual(i, len(expected[platform]), "Expected item did not appear")
      self.assertEqual(i, len(actual[platform]), "Unexpected item")

if __name__ == "__main__":
  unittest.main()

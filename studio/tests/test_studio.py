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

  def test_linux_files(self):

    for platform in ["linux", "win", "mac"]:
      name = "tools/adt/idea/studio/android-studio.%s.zip" % platform
      actual = []
      with zipfile.ZipFile(name) as file:
        actual = sorted(file.namelist())

      expected = []
      with open("tools/adt/idea/studio/tests/expected_%s.txt" % platform, "r") as txt:
        expected = [line.strip() for line in sorted(txt.readlines())]

      if expected != actual:
        undeclared_dir = os.getenv("TEST_UNDECLARED_OUTPUTS_DIR")
        with open("%s/expected_%s.txt" % (undeclared_dir, platform), "w") as new_ex:
          new_ex.writelines([line + "\n" for line in actual])
        print("You can find the newly expected file in the undeclared output directory.")

      i = 0
      while i < len(actual) and i < len(expected):
        self.assertEqual(actual[i], expected[i], "#%d - Expected \"%s\", got \"%s\"" % (i, expected[i], actual[i]))
        i += 1
      self.assertEqual(i, len(expected), "Expected item did not appear")
      self.assertEqual(i, len(actual), "Unexpected item")

if __name__ == "__main__":
  unittest.main()

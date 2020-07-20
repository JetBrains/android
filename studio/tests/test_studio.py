import argparse
import glob
import os
import re
import shutil
import subprocess
import sys
import tarfile
import unittest
import zipfile

out_dir = None
dist_dir = None
build = None
aswb = False

# To move all tests from /tools/idea/test_studio.py
class StudioTests(unittest.TestCase):

  def test_linux_files(self):

    for platform in ["linux", "win", "mac"]:
      name = os.path.join("tools/adt/idea/studio/android-studio.%s.zip" % platform)
      actual = []
      with zipfile.ZipFile(name) as file:
        actual = sorted(file.namelist())

      expected = []
      with open("tools/adt/idea/studio/tests/expected_%s.txt" % platform, "r") as txt:
        expected = [line.strip() for line in sorted(txt.readlines())]

      i = 0
      while i < len(actual) and i < len(expected):
        self.assertEqual(actual[i], expected[i], "#%d - Expected \"%s\", got \"%s\"" % (i, expected[i], actual[i]))
        i += 1
      self.assertEqual(i, len(expected), "Expected item did not appear")
      self.assertEqual(i, len(actual), "Unexpected item")
    

if __name__ == '__main__':
    unittest.main()

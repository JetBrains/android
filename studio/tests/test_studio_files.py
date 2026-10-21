#  Copyright (C) 2026 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import os
import unittest
import zipfile

PLATFORMS = ["linux", "win", "mac", "mac_arm"]

class TestStudioFiles(unittest.TestCase):
  """Performs basic tests on studio artifacts.
  """

  def test_studio_files(self):

    ide = os.getenv("ide")
    configuration = os.getenv("configuration")

    actual = {}
    for platform in PLATFORMS:
      name = "%s.%s.%s.lst" % (ide, configuration, platform)
      with open(name, "r") as txt:
        actual[platform] = sorted(txt.read().splitlines())

    ide_path_prefix = "/".join(ide.split("/")[:-1])
    ide_name = ide.split("/")[-1]
    expected = {}
    for platform in PLATFORMS:
      with open("%s/tests/expected_studio_files/%s/expected_%s.txt" % (ide_path_prefix,ide_name,platform), "r") as txt:
        expected_lines = set(txt.read().splitlines())
      
      diff_path = "%s/tests/expected_studio_files/%s/%s/expected_diff_%s.txt" % (ide_path_prefix,ide_name,configuration,platform)
      if os.path.exists(diff_path):
        with open(diff_path, "r") as txt:
          for line in txt.read().splitlines():
            if line.startswith("+++"):
              expected_lines.add(line[3:])
            elif line.startswith("---"):
              expected_lines.remove(line[3:])
      expected[platform] = sorted(list(expected_lines))

    for platform in PLATFORMS:
      if expected != actual:
        undeclared_dir = os.getenv("TEST_UNDECLARED_OUTPUTS_DIR")
        with open("%s/expected_%s.txt" % (undeclared_dir, platform), "w") as new_ex:
          new_ex.writelines([line + "\n" for line in actual[platform]])

    if expected != actual:
      print(f"You can run the bazel target //tools/adt/idea/studio:update_expected_studio_files to update expected studio files of all studio flavors or run target //{ide_path_prefix}:{ide_name}.update_expected_studio_files to just update for {ide_name}. Alternatively you can find the newly expected file in the undeclared output directory.")

    for platform in PLATFORMS:
      i = 0
      while i < len(actual[platform]) and i < len(expected[platform]):
        self.assertEqual(actual[platform][i], expected[platform][i], "Platform %s #%d - Expected \"%s\", got \"%s\"" % (platform, i, expected[platform][i], actual[platform][i]))
        i += 1
      self.assertEqual(i, len(expected[platform]), "Expected item did not appear")
      self.assertEqual(i, len(actual[platform]), "Unexpected item")

if __name__ == "__main__":
  unittest.main()
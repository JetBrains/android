import filecmp
import glob
import os
import platform
import re
import shutil
import unittest
import zipfile
import update_searchable_options


class SearchableOptionTests(unittest.TestCase):
  """Tests searchable options to be up-to-date.

  This test purpose is to generate these files, so whenever a
  configurable or an action description changes we can use this
  test to keep the files up-to-date.
  """

  def test_searchable_options(self):

    work_dir = os.getenv("TEST_TMPDIR")
    expected_dir = os.path.join(work_dir, "expected")

    plugin_list = update_searchable_options.generate_searchable_options(work_dir, expected_dir)

    # Create actual tree
    plugin_path = {
      "Windows": "android-studio/plugins",
      "Linux": "android-studio/plugins",
      "Darwin": "Android Studio*.app/Contents/plugins",
    }
    actual_dir = os.path.join(work_dir, "actual")
    [plugins_dir] = glob.glob(os.path.join(work_dir, plugin_path[platform.system()]))
    for plugin in os.listdir(plugins_dir):
      if plugin in plugin_list:
        lib_dir = os.path.join(plugins_dir, plugin, "lib")
        for jar in os.listdir(lib_dir):
          if jar.endswith(".jar"):
            with zipfile.ZipFile(os.path.join(lib_dir, jar)) as jar_file:
              for name in jar_file.namelist():
                if re.match(r"search/.*searchableOptions\.xml", name):
                  jar_file.extract(name, path=os.path.join(actual_dir, plugin, jar))

    eq = self.same_folders(filecmp.dircmp(expected_dir, actual_dir))
    if not eq:
      print("Searchable options comparison failed.")
      print("The expected output is in outputs.zip, please update tools/adt/idea/searchable-options with it.")
      print("Alternatively, if you are on Linux you can run: bazel run //tools/adt/idea/searchable-options:update_searchable_options")
      undeclared_outputs = os.getenv("TEST_UNDECLARED_OUTPUTS_DIR")
      for name in os.listdir(expected_dir):
        shutil.copytree(os.path.join(expected_dir, name), os.path.join(undeclared_outputs, name))

      self.fail("Searchable options differ")

  def same_folders(self, diff):
    if diff.diff_files:
      return False
    for sub_diff in diff.subdirs.values():
      if not self.same_folders(sub_diff):
        return False
    return True

if __name__ == "__main__":
  unittest.main()

import argparse
import filecmp
import glob
import os
import platform
import re
import shutil
import sys
import unittest
import zipfile
import update_searchable_options

ide_path = None
plugins = None

class SearchableOptionTests(unittest.TestCase):
  """Tests searchable options to be up-to-date.

  This test purpose is to generate these files, so whenever a
  configurable or an action description changes we can use this
  test to keep the files up-to-date.
  """

  def test_searchable_options(self):
    work_dir = os.getenv("TEST_TMPDIR")
    expected_dir = os.path.join(work_dir, "expected")

    plugin_list = update_searchable_options.generate_searchable_options(work_dir, expected_dir, ide_path, plugins)
    if plugins:
      plugin_list = {dir: id for dir, id in plugin_list.items() if id in plugins}
    print(plugin_list)

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
          if jar.endswith(".so.jar"):
            with zipfile.ZipFile(os.path.join(lib_dir, jar)) as jar_file:
              for name in jar_file.namelist():
                if name.endswith(".json"):
                  jar_file.extract(name, path=actual_dir)

    eq = self.same_folders(filecmp.dircmp(expected_dir, actual_dir, ignore = ["content.bzl"]))
    if not eq:
      print("Searchable options comparison failed.")
      print("The expected output is in outputs.zip, please update tools/adt/idea/searchable-options with it.")
      print("Alternatively, if you are on Linux you can run: bazel run //tools/adt/idea/searchable-options:update_searchable_options")
      undeclared_outputs = os.getenv("TEST_UNDECLARED_OUTPUTS_DIR")
      for name in os.listdir(expected_dir):
        shutil.copyfile(os.path.join(expected_dir, name), os.path.join(undeclared_outputs, name))

      self.fail("Searchable options differ")

  def same_folders(self, diff):
    if diff.diff_files or diff.left_only or diff.right_only:
      diff.report()
      return False
    return True

if __name__ == "__main__":
  parser = argparse.ArgumentParser()
  parser.add_argument(
      "--ide",
      dest="ide",
      required=True,
      help="The path (prefix) to the ide artifacts")
  parser.add_argument(
      "--plugins",
      dest="plugins",
      nargs="*",
      default=[],
      help="The plugins to export, if none chosen all plugins are exported")

  args, left = parser.parse_known_args()
  ide_path = args.ide
  plugins = args.plugins
  left.insert(0, sys.argv[0])
  unittest.main(argv = left)

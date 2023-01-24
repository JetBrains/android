import enum
import filecmp
import os
import platform
import subprocess
import tempfile
import unittest
import zipfile

UPDATER_PATH: str = "tools/adt/idea/studio/"


@enum.unique
class Platform(str, enum.Enum):
  """Enum representing the platforms for which we produce .zip files."""

  LINUX = "linux"
  WIN = "win"
  MAC = "mac"
  MAC_ARM = "mac_arm"

  def get_path_to_zip(self) -> str:
    return "tools/adt/idea/studio/android-studio.%s.zip" % self.value


def get_current_platform() -> Platform:
  major_win_ver = platform.win32_ver()[0]
  if major_win_ver:
    return Platform.WIN

  major_mac_ver = platform.mac_ver()[0]
  if major_mac_ver:
    is_arm = platform.processor() == "arm"
    return Platform.MAC_ARM if is_arm else Platform.MAC

  return Platform.LINUX


class UpdaterTests(unittest.TestCase):
  """Performs basic tests on the updater/patcher."""

  def setUp(self):
    super().setUp()
    self.updater_script_path: str = os.path.join(UPDATER_PATH, "updater")

  def fail_dircmp(self, dircmp: filecmp.dircmp, msg: str):
    """Fails the test using dircmp to form the full message.

    Args:
      dircmp: the dircmp causing the test failure.
      msg: additional information about the failure.

    Raises:
      AssertionError: this is always raised.
    """
    self.fail(
        "Difference found comparing \"%s\" (left) and \"%s\" (right): %s" %
        (dircmp.left, dircmp.right, msg))

  def fail_on_dircmp_diffs(self, dircmp: filecmp.dircmp):
    """Examines dircmp for differences and fails the test if any are found.

    If the contents of two files are identical but one is executable and the
    other isn't, dircmp will still consider them to be the same. The only user
    scenario this would impact is running files from a command-line.

    Args:
      dircmp: the dircmp to examine for differences.

    Raises:
      AssertionError: the directories differ.
    """
    if dircmp.left_only:
      self.fail_dircmp(dircmp,
                       "files found only in left dir: %s" % dircmp.left_only)
    if dircmp.right_only:
      self.fail_dircmp(dircmp,
                       "files found only in right dir: %s" % dircmp.right_only)
    if dircmp.common_funny:
      # Note: differing symlinks fall into this category of "common_funny".
      self.fail_dircmp(
          dircmp, "files found which differ in types or os.stat() results: %s" %
          dircmp.common_funny)
    if dircmp.funny_files:
      self.fail_dircmp(dircmp,
                       "files could not be compared: %s" % dircmp.funny_files)
    if dircmp.diff_files:
      self.fail_dircmp(dircmp,
                       "files whose contents differ: %s" % dircmp.diff_files)

    # Recurse through subdirectories.
    for sub_dircmp in dircmp.subdirs.values():
      self.fail_on_dircmp_diffs(sub_dircmp)

  def validate_patcher(self, old_zip: str, new_zip: str):
    """Ensure that we can patch one platform to another.

    The patcher should be able to take any arbitrary inputs "old" and "new" and
    produce a patch, P, which represents the diff from "old" → "new". This
    method generates P, then ensures that P(old) == new.

    Args:
      old_zip: a relative or absolute path to the "old" zip file (see
        description above for "old" vs. "new" terminology).
      new_zip: a relative or absolute path to the "new" zip file

    Raises:
      AssertionError: some part of the process failed.
    """
    print("Validating that the patcher works on \"%s\" → \"%s\"" %
          (old_zip, new_zip))

    with tempfile.TemporaryDirectory() as tempdir:
      old_version_description = "Old version"
      new_version_description = "New version"

      old_folder_path = os.path.join(tempdir, "old")
      new_folder_path = os.path.join(tempdir, "new")
      patch_folder_path = os.path.join(tempdir, "patch")

      for folder_path in [old_folder_path, new_folder_path, patch_folder_path]:
        os.makedirs(folder_path)

      print("Extracting .zip files")
      with zipfile.ZipFile(old_zip, "r") as zip_ref:
        zip_ref.extractall(old_folder_path)
      with zipfile.ZipFile(new_zip, "r") as zip_ref:
        zip_ref.extractall(new_folder_path)

      # Before continuing, ensure that the input directories differ, that way we
      # know the patcher is actually removing differences.
      failed_as_expected = False
      dircmp = filecmp.dircmp(old_folder_path, new_folder_path)
      try:
        self.fail_on_dircmp_diffs(dircmp)
      except AssertionError:
        failed_as_expected = True
      if not failed_as_expected:
        self.fail(
            "The extracted .zip files contain no differences to begin with.")

      patch_file_path = os.path.join(tempdir, "patch.jar")

      print("Running updater")
      args = [
          self.updater_script_path,
          "--wrapper_script_flag=--jvm_flag=-Xmx8G",  # 233245811
          "create",
          old_version_description,
          new_version_description,
          old_folder_path,
          new_folder_path,
          patch_file_path,

          # Treat .zip and .jar files as binary files. Without this, it's
          # possible that the resulting patch may use a different compression
          # strategy from the original files. When that happens, the .zip or
          # .jar files themselves will appear different despite containing
          # equivalent contents.
          "--zip_as_binary",

          # The "strict" flag makes it so that the created patch contains extra
          # information to fully validate an installation.
          #
          # This isn't strictly necessary for the test to pass, but it matches
          # how Android Studio produces patches for production uses.
          "--strict",
      ]
      process = subprocess.Popen(
          args, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
      stdout, stderr = process.communicate()
      print("stdout: %s" % stdout)
      print("stderr: %s" % stderr)
      return_code = process.returncode
      if return_code != 0:
        self.fail("Running the updater (args=%s) failed. Return code: %s" %
                  (str(args), return_code))

      # The patcher runs in-place, meaning the old folder will have its contents
      # directly modified rather than producing a copy.
      print("Running the patcher")
      args = [
          "java",
          "-jar",
          patch_file_path,
          old_folder_path,
      ]
      process = subprocess.Popen(
          args, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
      stdout, stderr = process.communicate()
      print("stdout: %s" % stdout)
      print("stderr: %s" % stderr)
      return_code = process.returncode
      if return_code != 0:
        self.fail("Running the patcher (args=%s) failed. Return code: %s" %
                  (str(args), return_code))

      print("Comparing the results")
      dircmp = filecmp.dircmp(old_folder_path, new_folder_path)
      self.fail_on_dircmp_diffs(dircmp)

  def test_patch_platforms(self):
    current_platform = get_current_platform()

    if current_platform == Platform.LINUX or current_platform == Platform.MAC_ARM:
      self.validate_patcher(current_platform.get_path_to_zip(),
                            Platform.WIN.get_path_to_zip())
    else:
      self.validate_patcher(Platform.MAC.get_path_to_zip(),
                            Platform.WIN.get_path_to_zip())


if __name__ == "__main__":
  unittest.main()

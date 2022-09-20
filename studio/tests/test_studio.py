import os
import re
import unittest
import zipfile

PLATFORMS = ["linux", "win", "mac", "mac_arm"]

def _is_symlink(zipinfo):
  return (zipinfo.external_attr & 0x20000000) > 0

class StudioTests(unittest.TestCase):
  """Performs basic tests on studio artifacts.
  """

  def test_studio_files(self):

    actual = {}
    for platform in PLATFORMS:
      name = "tools/adt/idea/studio/android-studio.%s.zip" % platform
      with zipfile.ZipFile(name) as file:
        actual[platform] = sorted(file.namelist())

    expected = {}
    for platform in PLATFORMS:
      with open("tools/adt/idea/studio/tests/expected_%s.txt" % platform, "r") as txt:
        expected[platform] = [line.strip() for line in sorted(txt.readlines())]

    for platform in PLATFORMS:
      if expected != actual:
        undeclared_dir = os.getenv("TEST_UNDECLARED_OUTPUTS_DIR")
        with open("%s/expected_%s.txt" % (undeclared_dir, platform), "w") as new_ex:
          new_ex.writelines([line + "\n" for line in actual[platform]])
        print("You can find the newly expected file in the undeclared output directory.")

    for platform in PLATFORMS:
      i = 0
      while i < len(actual[platform]) and i < len(expected[platform]):
        self.assertEqual(actual[platform][i], expected[platform][i], "#%d - Expected \"%s\", got \"%s\"" % (i, expected[platform][i], actual[platform][i]))
        i += 1
      self.assertEqual(i, len(expected[platform]), "Expected item did not appear")
      self.assertEqual(i, len(actual[platform]), "Unexpected item")

  def test_mac_contents_clean(self):
    name = "tools/adt/idea/studio/android-studio.mac.zip"
    file = zipfile.ZipFile(name)
    for f in file.namelist():
      m = re.search("Android Studio.*\\.app/Contents/([^/]+)$", f)
      if m:
        self.assertEqual(m.group(1), "Info.plist", "Only Info.plist should be present in Contents (Found " + m.group(1) + ")")

  def test_mac_filelist(self):
    """Tests whether the _codesign/filelist file is up to date.

    When building the macOS distribution, //tools/adt/idea/studio/codesign/filelist
    is copied to _codesign/filelist in the final zip. This test ensures that
    the entries in filelist are up to date.
    """
    name = "tools/adt/idea/studio/android-studio.mac.zip"
    macos_zip = zipfile.ZipFile(name)
    namelist = macos_zip.namelist()
    filelist = macos_zip.open("_codesign/filelist")
    for line in filelist:
      line = line.decode("utf-8").strip()
      if line.startswith("#"):
        continue
      # ignore ARM specific files
      if "arm64" in line:
        continue
      self.assertIn(line, namelist, "%s is in _codesign/filelist but is not present in distribution" % line)

  def test_no_build_files(self):
    for platform in PLATFORMS:
      name = "tools/adt/idea/studio/android-studio.%s.zip" % platform
      with zipfile.ZipFile(name) as file:
        for f in file.infolist():
          self.assertFalse(f.filename.endswith("/BUILD") or f.filename.endswith("/BUILD.bazel"),
                           "Unexpected BUILD file in zip " + name + ": " + f.filename)

  def test_game_tools_artifacts_are_present(self):
    all_required = [
        "plugins/android/lib/game-tools.jar",
        "plugins/android/lib/game-tools-protos.jar",
    ]

    archive_files_required = {
        "linux": [
            "bin/game-tools.sh",
            "bin/profiler.sh",
        ],
        "win": [
            "bin/game-tools.bat",
            "bin/profiler.bat",
        ],
    }

    for platform in archive_files_required.keys():
      name = "tools/adt/idea/studio/android-studio.%s.zip" % platform
      with zipfile.ZipFile(name) as file:
        files = file.namelist()
      for req in all_required + archive_files_required[platform]:
        if not any(file_name.endswith(req) for file_name in files):
          self.fail("Required file " + req + " not found in " + platform  + " distribution.")

  def test_profiler_artifacts_are_present(self):
    all_required = [
        "plugins/android/resources/perfa.jar",
        "plugins/android/resources/profilers-transform.jar",
    ]
    for abi in ["x86", "arm64-v8a", "armeabi-v7a"]:
      all_required += [
          "plugins/android/resources/perfetto/%s/libperfetto.so" % abi,
          "plugins/android/resources/perfetto/%s/perfetto" % abi,
          "plugins/android/resources/perfetto/%s/traced" % abi,
          "plugins/android/resources/perfetto/%s/traced_probes" % abi,
          "plugins/android/resources/simpleperf/%s/simpleperf" % abi,
          "plugins/android/resources/transport/%s/transport" % abi,
          "plugins/android/resources/transport/native/agent/%s/libjvmtiagent.so" % abi,
      ]

    archive_files_required = {
        "linux": [
            "plugins/android/resources/trace_processor_daemon/trace_processor_daemon"
        ],
        "mac": [
            "plugins/android/resources/trace_processor_daemon/trace_processor_daemon"
        ],
        "win": [
            "plugins/android/resources/trace_processor_daemon/trace_processor_daemon.exe"
        ],
    }

    for platform in archive_files_required.keys():
      name = "tools/adt/idea/studio/android-studio.%s.zip" % platform
      with zipfile.ZipFile(name) as file:
        files = file.namelist()
      for req in all_required + archive_files_required[platform]:
        if not any(file_name.endswith(req) for file_name in files):
          self.fail("Required file " + req + "not found in " + platform  + " distribution.")

  def test_trace_agent_jar_present(self):
    """Tests that trace_agent.jar is included in distribution"""
    expected = "plugins/android/resources/trace_agent.jar"

    for platform in PLATFORMS:
      name = "tools/adt/idea/studio/android-studio.%s.zip" % platform
      with zipfile.ZipFile(name) as file:
        files = file.namelist()
      if not any(file_name.endswith(expected) for file_name in files):
        self.fail("Required file not found in distribution: " + expected)

  def test_mac_attributes(self):
    name = "tools/adt/idea/studio/android-studio.mac.zip"
    with zipfile.ZipFile(name) as file:
      found = False
      for f in file.infolist():
        is_symlink = _is_symlink(f)
        if f.filename.endswith("Contents/jbr/Contents/MacOS/libjli.dylib"):
          found = True
          self.assertFalse(is_symlink, "Contents/jbr/Contents/MacOS/libjli.dylib should not be symlink")
        elif f.filename.endswith("Contents/MacOS/studio"):
          self.assertFalse(f.external_attr == 0x1ED0000, "studio should be \"-rwxr-xr-x\"")
          self.assertFalse(is_symlink, f.filename + " should not be a symlink")
        elif f.filename.endswith(".app/Contents/Info.plist"):
          self.assertTrue(f.external_attr == 0x81B40000, "Info.plist should be \"-rw-r--r--\"")
        else:
          self.assertFalse(f.external_attr == 0, "Unix attributes are missing from the entry")
          self.assertFalse(is_symlink, f.filename + " should not be a symlink")
      self.assertTrue(found, "Android Studio.*.app/Contents/jbr/Contents/MacOS/libjli.dylib not found")

  def test_all_files_writable(self):
    for platform in PLATFORMS:
      name = "tools/adt/idea/studio/android-studio.%s.zip" % platform
      with zipfile.ZipFile(name) as file:
        for f in file.infolist():
          if f.external_attr & 0x1800000 != 0x1800000:
            self.fail("Found file without full read/write permissions: %s %x" % (f.filename, f.external_attr))

  def test_kotlin_plugin_not_duplicated(self):
    # Motive: bundling the Kotlin plugin is handled specially in BaseIdeaProperties.groovy
    name = "tools/adt/idea/studio/android-studio.mac.zip"
    with zipfile.ZipFile(name) as mac_zip:
      kotlin_plugin_count = sum(name.endswith("kotlin-plugin.jar") for name in mac_zip.namelist())
      self.assertEqual(kotlin_plugin_count, 1)

if __name__ == "__main__":
  unittest.main()

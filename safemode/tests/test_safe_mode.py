import os
import re
import unittest
import zipfile
import json

class SafeModeTests(unittest.TestCase):
  """Performs basic tests on safe mode artifacts.
  """

  def test_jars_parity(self):
    mac_jars = set()
    linux_jars = set()
    win_jars = set()
    mac_safe_jars = set()
    linux_safe_jars = set()
    win_safe_jars = set()
    mac_jvm_args = set()
    mac_safe_jvm_args = set()

    file = zipfile.ZipFile("tools/adt/idea/studio/android-studio.linux.zip")
    for f in file.namelist():
      m = re.search("studio_safe.sh", f)
      if m:
        lines = open(file.extract(f), encoding="utf8").readlines()
        for line in lines:
          if line.lower().startswith("class_path"):
            linux_safe_jars.add(line)
      m = re.search("studio.sh", f)
      if m:
        lines = open(file.extract(f), encoding="utf8").readlines()
        for line in lines:
          if line.lower().startswith("class_path"):
            linux_jars.add(line)

    file = zipfile.ZipFile("tools/adt/idea/studio/android-studio.win.zip")
    for f in file.namelist():
      m = re.search("studio_safe.bat", f)
      if m:
        lines = open(file.extract(f), encoding="utf8").readlines()
        for line in lines:
          if "class_path=" in line.lower():
            win_safe_jars.add(line)
      m = re.search("studio.bat", f)
      if m:
        lines = open(file.extract(f), encoding="utf8").readlines()
        for line in lines:
          if "class_path=" in line.lower():
            win_jars.add(line)

    file = zipfile.ZipFile("tools/adt/idea/studio/android-studio.mac.zip")
    for f in file.namelist():
      m = re.search("studio_safe.sh", f)
      if m:
        lines = open(file.extract(f), encoding="utf8").readlines()
        for line in lines:
          if line.lower().startswith("class_path"):
            l = line.split("IDE_HOME/lib/",1)[1].replace('"', '').strip()
            mac_safe_jars.add(l)

          if "Djava" in line:
            args = line.replace("\\", "").strip().split(" ")
            for arg in args:
              if "idea.paths.selector" in arg:
                continue
              mac_safe_jvm_args.add(arg)

      m = re.search("product-info.json", f)
      if m:
        data = json.load(open(file.extract(f), encoding="utf8"))
        for i in data["launch"]:
          for jar in i["bootClassPathJarNames"]:
            mac_jars.add(jar)

        for arg in i["additionalJvmArguments"]:
          if "idea.paths.selector" in arg:
            continue
          mac_jvm_args.add(arg)

    self.assertEqual(mac_jvm_args, mac_safe_jvm_args)
    self.assertEqual(mac_jars, mac_safe_jars)
    self.assertEqual(linux_jars, linux_safe_jars)
    self.assertEqual(win_jars, win_safe_jars)


if __name__ == "__main__":
  unittest.main()
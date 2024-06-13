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
    win_jvm_args = set()
    lin_jvm_args = set()
    mac_safe_jvm_args = set()
    win_safe_jvm_args = set()
    lin_safe_jvm_args = set()

    file = zipfile.ZipFile("tools/adt/idea/studio/android-studio.linux.zip")
    for f in file.namelist():
      m = re.search("studio_safe.sh", f)
      if m:
        lines = open(file.extract(f), encoding="utf8").readlines()
        for line in lines:
          if line.lower().startswith("class_path"):
            linux_safe_jars.add(line)

          if "Djava" in line:
            args = line.replace("\\", "").strip().split(" ")
            for arg in args:
              if "idea.paths.selector" in arg or "studio.safe.mode" in arg or not arg:
                continue
              lin_safe_jvm_args.add(arg)

      m = re.search("studio.sh", f)
      if m:
        lines = open(file.extract(f), encoding="utf8").readlines()
        for line in lines:
          if line.lower().startswith("class_path"):
            linux_jars.add(line)

          if "Djava" in line:
            args = line.replace("\\", "").strip().split(" ")
            for arg in args:
              if "idea.paths.selector" in arg:
                continue
              lin_jvm_args.add(arg)

    file = zipfile.ZipFile("tools/adt/idea/studio/android-studio.win.zip")
    for f in file.namelist():
      m = re.search("studio_safe.bat", f)
      if m:
        lines = open(file.extract(f), encoding="utf8").readlines()
        for line in lines:
          if "class_path=" in line.lower():
            win_safe_jars.add(line)

          if "Djava" in line:
            args = line.replace("\\", "").strip().split(" ")
            for arg in args:
              if "idea.paths.selector" in arg or "studio.safe.mode" in arg or not arg:
                continue
              win_safe_jvm_args.add(arg)

      m = re.search("studio.bat", f)
      if m:
        lines = open(file.extract(f), encoding="utf8").readlines()
        for line in lines:
          if "class_path=" in line.lower():
            win_jars.add(line)

          if "Djava" in line:
            args = line.replace("\\", "").strip().split(" ")
            for arg in args:
              if "idea.paths.selector" in arg:
                continue
              win_jvm_args.add(arg)

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
              if "idea.paths.selector" in arg or "studio.safe.mode" in arg or not arg:
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

    try:
      self.assertEqual(mac_jvm_args, mac_safe_jvm_args)
      self.assertEqual(lin_jvm_args, lin_safe_jvm_args)
      self.assertEqual(win_jvm_args, win_safe_jvm_args)
      self.assertEqual(mac_jars, mac_safe_jars)
      self.assertEqual(linux_jars, linux_safe_jars)
      self.assertEqual(win_jars, win_safe_jars)
    except AssertionError as e:
      print("""
        There is a mismatch in the safe mode scripts. To regenerate the content of the scripts,
        run the following script manually:

        //tools/adt/idea/safemode/script-generation/generate_safe_mode_scripts.py
      """)
      print(win_jvm_args)
      print(win_safe_jvm_args)
      raise e

if __name__ == "__main__":
  unittest.main()

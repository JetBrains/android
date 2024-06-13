from pathlib import Path
import os
import json
import sys
import zipfile
import re
from safe_mode_scripts import mac_script, lin_script, win_script

#  Generates the safe mode scripts for mac
def generate_mac_scripts():
    mac_jars = set()
    mac_jvm_args = set()
    zip = zipfile.ZipFile("tools/adt/idea/studio/android-studio.mac.zip")
    found = False
    for f in zip.namelist():
      m = re.search("product-info.json", f)
      if not m:
        continue
      found = True
      data = json.load(open(zip.extract(f), encoding="utf8"))
      for i in data["launch"]:
        for jar in i["bootClassPathJarNames"]:
          mac_jars.add(jar)

      for arg in i["additionalJvmArguments"]:
        if "idea.paths.selector" in arg:
          continue
        mac_jvm_args.add(arg)
    if not found:
      raise Exception("Unable to find product-info.json file")
    # create the included jars and JVM argument strings
    jars = jars_string(mac_jars, "CLASS_PATH=\"$CLASS_PATH:$IDE_HOME/lib/", "\"\n")
    jvm_args = jvm_string(mac_jvm_args, "-Didea.paths.selector=\"${STUDIO_VERSION}.safe\" -Dstudio.safe.mode=true") + " \\"
    # concat them with the script template
    safe_mode_script_content = mac_script[0] + jars + mac_script[1] + jvm_args + mac_script[2]
    # create safe mode script
    gen_script("mac", safe_mode_script_content, "sh")

#  Generates the safe mode scripts for linux or windows
def generate_lin_win_scripts(platform, zip_file, studio_file):
    jars = set()
    jvm_args = set()
    zip = zipfile.ZipFile(zip_file)
    found = False
    for f in zip.namelist():
      m = re.search(studio_file, f)
      if not m:
        continue
      found = True
      lines = open(zip.extract(f), encoding="utf8")
      for line in lines:
        if "class_path=" in line.lower():
          jars.add(line)

        if "Djava" in line:
          args = line.replace("\\", "").strip().split(" ")
          for arg in args:
            if "idea.paths.selector" in arg:
              continue
            jvm_args.add(arg)
    if not found:
      raise Exception("Unable to find studio file")
    # create the included jars and JVM argument strings
    jars = jars_string(jars, "", "")
    # concat them with the script
    suffix = ""
    scripts = []
    jvm_args_string = ""
    if platform == "win":
      suffix = "bat"
      scripts = win_script
      jvm_args_string = jvm_string(jvm_args, "-Didea.paths.selector=%STUDIO_VERSION%.safe -Dstudio.safe.mode=true") + " ^"
    else:
      suffix = "sh"
      scripts = lin_script
      jvm_args_string = jvm_string(jvm_args, "-Didea.paths.selector=\"${STUDIO_VERSION}.safe\" -Dstudio.safe.mode=true") + " \\"

    safe_mode_script_content = scripts[0] + jars + scripts[1] + jvm_args_string + scripts[2]
    # create safe mode script
    gen_script(platform, safe_mode_script_content, suffix)

def jars_string(jars, prefix, suffix):
  plat_loader_jar = ""
  jars_string = ""
  for j in jars:
    if "platform-loader" in j:
      plat_loader_jar = prefix + j + suffix
    else:
      jars_string += prefix + j + suffix
  return plat_loader_jar + jars_string

def jvm_string(jvm_args, path_selector):
  d_args = [path_selector]
  other_args = []
  for a in jvm_args:
    if a.startswith("-D"):
      d_args.append(a)
    else:
      other_args.append(a)
  return " ".join(d_args + other_args)

def gen_script(platform, content, suffix):
    dir = os.path.dirname(os.path.abspath(sys.argv[0]))
    os.mkdir(dir + "/" + platform)
    name = dir + "/" + platform + "/studio_safe." + suffix
    f = open(name, "w")
    f.write(content)
    f.close()
    os.chmod(name, 0o755)
    print("safe mode script created: " + name + "\n")

if __name__ == "__main__" :
  generate_mac_scripts()
  generate_lin_win_scripts("linux","tools/adt/idea/studio/android-studio.linux.zip", "studio.sh" )
  generate_lin_win_scripts("win", "tools/adt/idea/studio/android-studio.win.zip", "studio.bat")

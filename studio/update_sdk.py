#!/usr/bin/env python3
import os
import argparse
import tempfile
import sys
import zipfile
import tarfile
import re
import shutil

# A list of files not included in the SDK because they are maked by files in the root lib directory
# This should be sorted out at a different leve, but for now removing them here
HIDDEN = [
    "/plugins/Kotlin/lib/kotlin-stdlib-jdk8.jar",
    "/plugins/Kotlin/lib/kotlin-stdlib.jar",
    "/plugins/Kotlin/lib/kotlin-stdlib-jdk7.jar",
    "/plugins/Kotlin/lib/kotlin-stdlib-common.jar",
    "/lib/annotations-java5.jar",
]

ALL = "all"
LINUX = "linux"
WIN = "windows"
MAC = "darwin"

PLATFORMS = [LINUX, WIN, MAC]

HOME_PATHS = {
    LINUX: "/linux/android-studio",
    MAC: "/darwin/android-studio/Contents",
    WIN: "/windows/android-studio",
}

def list_sdk_jars(sdk):
  sets = {}
  for platform in PLATFORMS:
    idea_home = sdk + HOME_PATHS[platform]
    jars = ["/lib/" + jar for jar in os.listdir(idea_home + "/lib") if jar.endswith(".jar")]
    # Java plugin sdk are included as part of the platform as there are references to it.
    jars += ["/plugins/java/lib/" + jar for jar in os.listdir(idea_home + "/plugins/java/lib/") if jar.endswith(".jar")]
    jars = [jar for jar in jars if jar not in HIDDEN]
    sets[platform] = set(jars)

  sets[ALL] = sets[WIN] & sets[MAC] & sets[LINUX]
  sets[LINUX] = sets[LINUX] - sets[ALL]
  sets[WIN] = sets[WIN] - sets[ALL]
  sets[MAC] = sets[MAC] - sets[ALL]

  sdk_jars = {}
  for platform in [ALL] + PLATFORMS:
    sdk_jars[platform] = sorted(sets[platform])

  return sdk_jars


def list_plugin_jars(sdk):
  all = {}
  for platform in PLATFORMS:
    idea_home = sdk + HOME_PATHS[platform]
    all[platform] = {}
    for plugin in os.listdir(idea_home + "/plugins"):
      if plugin == "java":
        # The plugin java is added as part of the platform
        continue
      path = "/plugins/" + plugin + "/lib/"
      jars = [path + jar for jar in os.listdir(idea_home + path) if jar.endswith(".jar")]
      jars = [jar for jar in jars if jar not in HIDDEN]
      all[platform][plugin] = set(jars)

  plugins = sorted(set(all[MAC].keys()) | set(all[WIN].keys()) | set(all[LINUX].keys()))
  plugin_jars = {}
  plugin_jars[ALL] = {}
  plugin_jars[MAC] = {}
  plugin_jars[WIN] = {}
  plugin_jars[LINUX] = {}
  for p in plugins:
    common = all[LINUX][p] & all[MAC][p] & all[WIN][p]
    plugin_jars[ALL][p] = sorted(common)
    plugin_jars[MAC][p] = sorted(all[MAC][p] - common)
    plugin_jars[WIN][p] = sorted(all[WIN][p] - common)
    plugin_jars[LINUX][p] = sorted(all[LINUX][p] - common)

  return plugin_jars


def write_spec_file(workspace, sdk_rel, version, sdk_jars, plugin_jars):
 
  suffix = {
    ALL: "",
    MAC: "_darwin",
    WIN: "_windows",
    LINUX: "_linux",
  }

  with open(workspace + sdk_rel + "/spec.bzl", "w") as file:
    name = version.replace("-", "").replace(".", "_")
    file.write("# Auto-generated file, do not edit manually.\n")
    file.write(name  + " = struct(\n" )
    for platform in [ALL] + PLATFORMS:
      file.write(f"    jars{suffix[platform]} = [\n")
      for jar in sdk_jars[platform]:
        file.write("        \"" + jar + "\",\n")
      file.write("    ],\n")

    for platform in [ALL] + PLATFORMS:
      file.write(f"    plugin_jars{suffix[platform]} = {{\n")
      for plugin, jars in plugin_jars[platform].items():
        if jars:
          file.write("        \"" + plugin + "\": [\n")
          for jar in jars:
            file.write("            \"" + os.path.basename(jar) + "\",\n")
          file.write("        ],\n")
      file.write("    },\n")
    file.write(")\n")


def gen_lib(project_dir, name, jars, srcs):
  xml = f'<component name="libraryTable">\n  <library name="{name}">\n    <CLASSES>\n'
  for rel_path in jars:
    xml += f'      <root url="jar://$PROJECT_DIR$/{rel_path}!/" />\n'

  xml += f'    </CLASSES>\n    <JAVADOC />\n    <SOURCES>\n'
  for src in srcs:
    rel_path = os.path.relpath(src, project_dir)
    xml += f'      <root url="jar://$PROJECT_DIR$/{rel_path}!/" />\n'

  xml += f'    </SOURCES>\n  </library>\n</component>'

  filename = name.replace("-", "_")
  with open(project_dir + "/.idea/libraries/" + filename + ".xml", "w") as file:
    file.write(xml)


def write_xml_files(workspace, sdk, sdk_jars, plugin_jars):
  project_dir = os.path.join(workspace, "tools/adt/idea")
  rel_workspace = os.path.relpath(workspace, project_dir)

  # Add all jars, IJ will ignore the ones that don't exist
  all_jars = sdk_jars[ALL] + sorted(set(sdk_jars[MAC] + sdk_jars[WIN] + sdk_jars[LINUX]))
  paths = [rel_workspace + sdk + "/$SDK_PLATFORM$" + j for j in all_jars]
  gen_lib(project_dir, "studio-sdk", paths, [workspace + sdk + "/android-studio-sources.zip"])

  lib_dir = project_dir + "/.idea/libraries/"
  for lib in os.listdir(lib_dir):
    if (lib.startswith("studio_plugin_") and lib.endswith(".xml")) or lib == "intellij_updater.xml":
      os.remove(lib_dir + lib)


  for plugin, jars in plugin_jars[ALL].items():
    add = sorted(set(plugin_jars[WIN][plugin] + plugin_jars[MAC][plugin] + plugin_jars[LINUX][plugin]))
    paths = [ rel_workspace + sdk + f"/$SDK_PLATFORM$" + j for j in jars + add]
    gen_lib(project_dir, "studio-plugin-" + plugin, paths, [workspace + sdk + "/android-studio-sources.zip"])

  updater_jar = rel_workspace + sdk + "/updater-full.jar"
  if os.path.exists(project_dir + "/" + updater_jar):
    gen_lib(project_dir, "intellij-updater", [updater_jar], [workspace + sdk + "/android-studio-sources.zip"])


def update_files(workspace, version):
  sdk = "/prebuilts/studio/intellij-sdk/" + version

  sdk_jars = list_sdk_jars(workspace + sdk)
  plugin_jars = list_plugin_jars(workspace + sdk)

  write_xml_files(workspace, sdk, sdk_jars, plugin_jars)
  write_spec_file(workspace, sdk, version, sdk_jars, plugin_jars)


def check_artifacts(dir):
  files = sorted(os.listdir(dir))
  if not files:
    sys.exit("There are no artifacts in " + dir)
  regex = re.compile("android-studio-([^.]*)\.(.*)\.([^.-]+)(-sources.zip|.mac.zip|.tar.gz|.win.zip)$")
  files = [file for file in files if regex.match(file) or file == "updater-full.jar"]
  if not files:
    sys.exit("No artifacts found in " + dir)
  match = regex.match(files[0])
  version_major = match.group(1)
  version_minor = match.group(2)
  bid = match.group(3)
  expected = [
      "android-studio-%s.%s.%s-sources.zip" % (version_major, version_minor, bid),
      "android-studio-%s.%s.%s.mac.zip" % (version_major, version_minor, bid),
      "android-studio-%s.%s.%s.tar.gz" % (version_major, version_minor, bid),
      "android-studio-%s.%s.%s.win.zip" % (version_major, version_minor, bid),
      "updater-full.jar",
  ]
  if files != expected:
    print("Expected:")
    print(expected)
    print("Got:")
    print(files)
    sys.exit("Unexpected artifacts in " + dir)

  return "AI-" + version_major, files[0], files[1], files[2], files[3], files[4]


def download(workspace, bid):
  ret = os.system("prodcertstatus")
  if ret:
    sys.exit("You need prodaccess to download artifacts")
  if not bid:
    sys.exit("--bid argument needs to be set to download")
  dir = tempfile.mkdtemp(prefix="studio_sdk", suffix=bid)

  for artifact in ["android-studio-*-sources.zip", "android-studio-*.mac.zip", "android-studio-*.tar.gz", "android-studio-*.win.zip", "updater-full.jar"]:
    os.system(
        "/google/data/ro/projects/android/fetch_artifact --bid %s --target studio-sdk '%s' %s"
        % (bid, artifact, dir))

  return dir


def compatible(old_file, new_file):
  if not os.path.isfile(old_file) or not os.path.isfile(new_file):
    return False
  if not old_file.endswith(".jar") or not new_file.endswith(".jar"):
    return False
  old_files = []
  new_files = []
  with zipfile.ZipFile(old_file) as old_zip:
    old_files = [(info.filename, info.CRC) for info in old_zip.infolist()]
  with zipfile.ZipFile(new_file) as new_zip:
    new_files = [(info.filename, info.CRC) for info in new_zip.infolist()]
  return sorted(old_files) == sorted(new_files)


# Compares old_path with new_path and moves files from old
# to new that are compatible. Compatible means jars that
# didn't change content but only timestamps.
# This preserves old files intact, reducing git pressure.
def preserve_old(old_path, new_path):
  if not os.path.isdir(old_path) or not os.path.isdir(new_path):
    return
  for file in os.listdir(new_path):
    old_file = os.path.join(old_path, file)
    new_file = os.path.join(new_path, file)
    if os.path.isdir(new_file):
      if os.path.isdir(old_file):
        preserve_old(old_file, new_file)
    else:
      if compatible(old_file, new_file):
        os.replace(old_file, new_file)


def extract(workspace, dir, delete_after):
  version, sources, mac, linux, win, updater = check_artifacts(dir)
  path = workspace + "/prebuilts/studio/intellij-sdk/" + version

  # Don't delete yet, use for a timestamp-less diff of jars, to reduce git/review pressure
  old_path = None
  if os.path.exists(path):
    old_path = path + ".old"
    os.rename(path, old_path)
  os.mkdir(path)
  shutil.copyfile(dir + "/" + sources, path + "/android-studio-sources.zip")
  shutil.copyfile(dir + "/" + updater, path + "/updater-full.jar")

  print("Unzipping mac distribution...")
  # Call to unzip to preserve mac symlinks
  os.system("unzip -q -d \"%s\" \"%s\"" % (path + "/darwin", dir + "/" + mac))
  # Mac is the only one that contains the version in the directory, rename for
  # consistency with other platforms and easier tooling
  apps = ["/darwin/" + app for app in os.listdir(path + "/darwin") if app.startswith("Android Studio")]
  if len(apps) != 1:
    sys.exit("Only one directory starting with Android Studio expected for Mac")
  os.rename(path + apps[0], path + "/darwin/android-studio")

  print("Unzipping windows distribution...")
  with zipfile.ZipFile(dir + "/" + win, "r") as zip:
    zip.extractall(path + "/windows")

  print("Untaring linux distribution...")
  with tarfile.open(dir + "/" + linux, "r") as tar:
    tar.extractall(path + "/linux")

  if old_path:
    preserve_old(old_path, path)

  if delete_after:
    shutil.rmtree(dir)
  if old_path:
    shutil.rmtree(old_path)
  return version

def main(workspace, args):
  version = args.version
  path = args.path
  bid = args.download
  delete_path = False
  if bid:
    path = download(workspace, bid)
    delete_path = True
  if path:
    version = extract(workspace, path, delete_path)
  
  update_files(workspace, version)

if __name__ == "__main__":
  parser = argparse.ArgumentParser()
  parser.add_argument(
      "--download",
      default="",
      dest="download",
      help="The build id of the studio-sdk to download from go/ab")
  parser.add_argument(
      "--path",
      default="",
      dest="path",
      help="The path of already downloaded, or locally built, artifacts")
  parser.add_argument(
      "--existing_version",
      default="",
      dest="version",
      help="The version of an SDK already in prebuilts to update the project's xmls")
  workspace = os.path.join(
      os.path.dirname(os.path.realpath(__file__)), "../../../..")
  args = parser.parse_args()
  options = [opt for opt in [args.version, args.download, args.path] if opt]
  if len(options) != 1:
    print("You must specify only one option")
    parser.print_usage()
  else:
    main(workspace, args)

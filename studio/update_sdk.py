#!/usr/bin/env python3
import os
import argparse
import tempfile
import sys
import zipfile
import tarfile
import re
import glob
import shutil
import xml.etree.ElementTree as ET
import intellij

# A list of files not included in the SDK because they are made by files in the root lib directory
# This should be sorted out at a different level, but for now removing them here
HIDDEN = [
    "/plugins/Kotlin/lib/kotlin-reflect.jar",
    "/plugins/Kotlin/lib/kotlin-stdlib-jdk8.jar",
    "/plugins/Kotlin/lib/kotlin-stdlib.jar",
    "/plugins/Kotlin/lib/kotlin-stdlib-jdk7.jar",
    "/plugins/Kotlin/lib/kotlin-stdlib-common.jar",
]

ALL = "all"
LINUX = "linux"
WIN = "windows"
MAC = "darwin"
MAC_ARM = "darwin_aarch64"

PLATFORMS = [LINUX, WIN, MAC, MAC_ARM]

HOME_PATHS = {
    LINUX: "/linux/android-studio",
    MAC: "/darwin/android-studio/Contents",
    MAC_ARM: "/darwin_aarch64/android-studio/Contents",
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

  sets[ALL] = sets[WIN] & sets[MAC] & sets[MAC_ARM] & sets[LINUX]
  sets[LINUX] = sets[LINUX] - sets[ALL]
  sets[WIN] = sets[WIN] - sets[ALL]
  sets[MAC] = sets[MAC] - sets[ALL]
  sets[MAC_ARM] = sets[MAC_ARM] - sets[ALL]

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
  plugin_jars[MAC_ARM] = {}
  plugin_jars[WIN] = {}
  plugin_jars[LINUX] = {}
  for p in plugins:
    if p in all[LINUX] and p in all[MAC] and p in all[MAC_ARM] and p in all[WIN]:
      common = all[LINUX][p] & all[MAC][p] & all[MAC_ARM][p] & all[WIN][p]
    else:
      common = set()
    plugin_jars[ALL][p] = sorted(common)
    plugin_jars[MAC][p] = sorted(all[MAC][p] - common) if p in all[MAC] else []
    plugin_jars[MAC_ARM][p] = sorted(all[MAC_ARM][p] - common) if p in all[MAC_ARM] else []
    plugin_jars[WIN][p] = sorted(all[WIN][p] - common) if p in all[WIN] else []
    plugin_jars[LINUX][p] = sorted(all[LINUX][p] - common) if p in all[LINUX] else []

  return plugin_jars


def write_spec_file(workspace, sdk_rel, version, sdk_jars, plugin_jars, mac_bundle_name):

  suffix = {
    ALL: "",
    MAC: "_darwin",
    MAC_ARM: "_darwin_aarch64",
    WIN: "_windows",
    LINUX: "_linux",
  }

  sdk_versions = {}
  for platform in [LINUX, WIN, MAC, MAC_ARM]:
    sdk_version = intellij.extract_sdk_version(
      f'{workspace}{sdk_rel}{HOME_PATHS[platform]}/lib/resources.jar')
    sdk_versions[platform] = sdk_version
  if len(set(sdk_versions.values())) > 1:
    raise ValueError(f'Major and minor versions differ between OS platforms! {sdk_versions}')
  sdk_version = sdk_versions[LINUX]

  with open(workspace + sdk_rel + "/spec.bzl", "w") as file:
    name = version.replace("-", "").replace(".", "_")
    file.write("# Auto-generated file, do not edit manually.\n")
    file.write(name  + " = struct(\n" )
    file.write(f'    major_version="{sdk_version.major}",\n')
    file.write(f'    minor_version="{sdk_version.minor}",\n')

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

    file.write(f"    mac_bundle_name = \"{mac_bundle_name}\",\n")
    file.write(")\n")


# When running in --existing_version mode, the mac bundle name must be extracted
# from the preexisting spec.bzl file (since the original mac bundle artifact
# has already been renamed by this point).
def extract_preexisting_mac_bundle_name(workspace, version):
  with open(workspace + "/prebuilts/studio/intellij-sdk/" + version + "/spec.bzl", "r") as spec:
    search = re.search(r"mac_bundle_name = \"(.*)\"", spec.read())
    return search.group(1) if search else sys.exit("Failed to find existing mac bundle name")


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
  all_jars = sdk_jars[ALL] + sorted(set(sdk_jars[MAC] + sdk_jars[MAC_ARM] + sdk_jars[WIN] + sdk_jars[LINUX]))
  paths = [rel_workspace + sdk + "/$SDK_PLATFORM$" + j for j in all_jars]
  gen_lib(project_dir, "studio-sdk", paths, [workspace + sdk + "/android-studio-sources.zip"])

  lib_dir = project_dir + "/.idea/libraries/"
  for lib in os.listdir(lib_dir):
    if lib == "studio_plugin_Kotlin.xml":
      # Special case: the Kotlin plugin is part of IntelliJ Platform, but it is built and updated
      # separately by prebuilts/tools/common/kotlin-plugin/build.py (see Change I788900228).
      continue
    if (lib.startswith("studio_plugin_") and lib.endswith(".xml")) or lib == "intellij_updater.xml":
      os.remove(lib_dir + lib)


  for plugin, jars in plugin_jars[ALL].items():
    add = sorted(set(plugin_jars[WIN][plugin] + plugin_jars[MAC][plugin] + plugin_jars[MAC_ARM][plugin] + plugin_jars[LINUX][plugin]))
    paths = [ rel_workspace + sdk + f"/$SDK_PLATFORM$" + j for j in jars + add]
    gen_lib(project_dir, "studio-plugin-" + plugin, paths, [workspace + sdk + "/android-studio-sources.zip"])

  updater_jar = rel_workspace + sdk + "/updater-full.jar"
  if os.path.exists(project_dir + "/" + updater_jar):
    gen_lib(project_dir, "intellij-updater", [updater_jar], [workspace + sdk + "/android-studio-sources.zip"])


def update_files(workspace, version, mac_bundle_name):
  sdk = "/prebuilts/studio/intellij-sdk/" + version

  sdk_jars = list_sdk_jars(workspace + sdk)
  plugin_jars = list_plugin_jars(workspace + sdk)

  write_xml_files(workspace, sdk, sdk_jars, plugin_jars)
  write_spec_file(workspace, sdk, version, sdk_jars, plugin_jars, mac_bundle_name)


def check_artifacts(dir):
  files = sorted(os.listdir(dir))
  if not files:
    sys.exit("There are no artifacts in " + dir)
  regex = re.compile("android-studio-([^.]*)\.(.*)\.([^.-]+)(-sources.zip|.mac.x64-no-jdk.zip|.mac.aarch64-no-jdk.zip|-no-jbr.tar.gz|-no-jbr.win.zip)$")
  files = [file for file in files if regex.match(file) or file == "updater-full.jar"]
  if not files:
    sys.exit("No artifacts found in " + dir)
  match = regex.match(files[0])
  version_major = match.group(1)
  version_minor = match.group(2)
  bid = match.group(3)
  expected = [
      "android-studio-%s.%s.%s-no-jbr.tar.gz" % (version_major, version_minor, bid),
      "android-studio-%s.%s.%s-no-jbr.win.zip" % (version_major, version_minor, bid),
      "android-studio-%s.%s.%s-sources.zip" % (version_major, version_minor, bid),
      "android-studio-%s.%s.%s.mac.aarch64-no-jdk.zip" % (version_major, version_minor, bid),
      "android-studio-%s.%s.%s.mac.x64-no-jdk.zip" % (version_major, version_minor, bid),
      "updater-full.jar",
  ]
  if files != expected:
    print("Expected:")
    print(expected)
    print("Got:")
    print(files)
    sys.exit("Unexpected artifacts in " + dir)

  manifest = None
  manifests = glob.glob(dir + "/manifest_*.xml")
  if len(manifests) == 1:
    manifest = os.path.basename(manifests[0])

  return "AI-" + version_major, files[0], files[1], files[2], files[3], files[4], files[5], manifest


def download(workspace, bid):
  fetch_artifact = "/google/data/ro/projects/android/fetch_artifact"
  auth_flag = ""
  if os.path.exists("/usr/bin/prodcertstatus"):
    if os.system("prodcertstatus"):
      sys.exit("You need prodaccess to download artifacts")
  else:
    fetch_artifact = "/usr/bin/fetch_artifact"
    auth_flag = "--use_oauth2"
    if not os.path.exists(fetch_artifact):
      sys.exit("""You need to install fetch_artifact:
sudo glinux-add-repo android stable && \\
sudo apt update && \\
sudo apt install android-fetch-artifact""")

  if not bid:
    sys.exit("--bid argument needs to be set to download")
  dir = tempfile.mkdtemp(prefix="studio_sdk", suffix=bid)

  for artifact in ["android-studio-*-sources.zip", "android-studio-*.mac.x64-no-jdk.zip", "android-studio-*.mac.aarch64-no-jdk.zip", "android-studio-*-no-jbr.tar.gz", "android-studio-*-no-jbr.win.zip", "updater-full.jar", "manifest_%s.xml" % bid]:
    os.system(
        "%s %s --bid %s --target IntelliJ '%s' %s"
        % (fetch_artifact, auth_flag, bid, artifact, dir))

  return dir


def write_metadata(path, data):
  with open(os.path.join(path, "METADATA"), "w") as file:
    for k, v in data.items():
      file.write(k + ": " + str(v) + "\n")

def extract(workspace, dir, delete_after, metadata):
  version, linux, win, sources, mac_arm, mac, updater, manifest = check_artifacts(dir)
  path = workspace + "/prebuilts/studio/intellij-sdk/" + version

  if os.path.exists(path):
    shutil.rmtree(path)

  os.mkdir(path)
  shutil.copyfile(dir + "/" + sources, path + "/android-studio-sources.zip")
  shutil.copyfile(dir + "/" + updater, path + "/updater-full.jar")

  print("Unzipping mac distribution...")
  # Call to unzip to preserve mac symlinks
  os.system("unzip -q -d \"%s\" \"%s\"" % (path + "/darwin", dir + "/" + mac))
  print("Unzipping mac aarch64 distribution...")
  # Call to unzip to preserve mac symlinks
  os.system("unzip -q -d \"%s\" \"%s\"" % (path + "/darwin_aarch64", dir + "/" + mac_arm))
  # Mac is the only one that contains the version in the directory, rename for
  # consistency with other platforms and easier tooling
  apps = ["/darwin/" + app for app in os.listdir(path + "/darwin") if app.startswith("Android Studio")]
  if len(apps) != 1:
    sys.exit("Only one directory starting with Android Studio expected for Mac")
  os.rename(path + apps[0], path + "/darwin/android-studio")
  os.rename(path + apps[0].replace("darwin", "darwin_aarch64"), path + "/darwin_aarch64/android-studio")
  mac_bundle_name = os.path.basename(apps[0])

  print("Unzipping windows distribution...")
  with zipfile.ZipFile(dir + "/" + win, "r") as zip:
    zip.extractall(path + "/windows")

  print("Untaring linux distribution...")
  with tarfile.open(dir + "/" + linux, "r") as tar:
    tar.extractall(path + "/linux")

  if manifest:
    xml = ET.parse(dir + "/" + manifest)
    for project in xml.getroot().findall("project"):
      metadata[project.get("path")] = project.get("revision")

  if delete_after:
    shutil.rmtree(dir)

  write_metadata(path, metadata)

  return version, mac_bundle_name

def main(workspace, args):
  metadata = {}
  mac_bundle_name = None
  version = args.version
  path = args.path
  bid = args.download
  delete_path = False
  if path:
    metadata["path"] = path
  if bid:
    metadata["build_id"] = bid
    path = download(workspace, bid)
    delete_path = not args.debug_download
  if path:
    version, mac_bundle_name = extract(workspace, path, delete_path, metadata)
    if args.debug_download:
      print("Dowloaded artifacts kept at " + path)
  else:
    mac_bundle_name = extract_preexisting_mac_bundle_name(workspace, version)


  update_files(workspace, version, mac_bundle_name)

if __name__ == "__main__":
  parser = argparse.ArgumentParser()
  parser.add_argument(
      "--download",
      default="",
      dest="download",
      help="The AB build to download")
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
  parser.add_argument(
      "--debug_download",
      action="store_true",
      dest="debug_download",
      help="Keeps the downloaded artifacts for debugging")
  workspace = os.path.join(
      os.path.dirname(os.path.realpath(__file__)), "../../../..")
  args = parser.parse_args()
  options = [opt for opt in [args.version, args.download, args.path] if opt]
  if len(options) != 1:
    print("You must specify only one option")
    parser.print_usage()
  else:
    main(workspace, args)

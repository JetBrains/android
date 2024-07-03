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
import json
import xml.etree.ElementTree as ET
import intellij
import mkspec
from collections import defaultdict
from pathlib import Path

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


# Generates a module containing IntelliJ and all its bundled plugins.
# This module helps form the runtime classpath for dev builds.
def gen_platform_module(iml_file, libs):
  xml = ''
  xml += '<?xml version="1.0" encoding="UTF-8"?>\n'
  xml += '<module type="JAVA_MODULE" version="4">\n'
  xml += '  <component name="NewModuleRootManager" inherit-compiler-output="true">\n'
  xml += '    <orderEntry type="inheritedJdk" />\n'
  xml += '    <orderEntry type="sourceFolder" forTests="false" />\n'
  for lib in libs:
    xml += f'    <orderEntry type="library" scope="RUNTIME" name="{lib}" level="project" />\n'
  xml += '  </component>\n'
  xml += '</module>'

  with open(iml_file, "w") as file:
    file.write(xml)


def write_xml_files(workspace, sdk, ides):
  project_dir = os.path.join(workspace, "tools/adt/idea")
  rel_workspace = os.path.relpath(workspace, project_dir)

  # Add all jars, IJ will ignore the ones that don't exist
  all_jars = ides[LINUX].platform_jars & ides[MAC].platform_jars & ides[MAC_ARM].platform_jars & ides[WIN].platform_jars
  sdk_jars = {platform: ides[platform].platform_jars - all_jars for platform in PLATFORMS}
  sdk_jars[ALL] = all_jars

  all_jars = sorted(sdk_jars[ALL]) + sorted(sdk_jars[MAC] | sdk_jars[MAC_ARM] | sdk_jars[WIN] | sdk_jars[LINUX])
  paths = [rel_workspace + sdk + "/$SDK_PLATFORM$" + j for j in all_jars]
  gen_lib(project_dir, "studio-sdk", paths, [workspace + sdk + "/android-studio-sources.zip"])

  lib_dir = project_dir + "/.idea/libraries/"
  for lib in os.listdir(lib_dir):
    if (lib.startswith("studio_plugin_") and lib.endswith(".xml")) or lib == "intellij_updater.xml":
      os.remove(lib_dir + lib)

  all_plugin_ids = set.union(*[set(ide.plugin_jars.keys()) for ide in ides.values()])
  common_plugins_with_jars = set()
  for plugin in all_plugin_ids:
    sets = [ide.plugin_jars[plugin] if plugin in ide.plugin_jars else set() for ide in ides.values()]
    if set.intersection(*sets):
      common_plugins_with_jars.add(plugin)
    jars = sorted(set.union(*sets))
    paths = [ rel_workspace + sdk + f"/$SDK_PLATFORM$" + j for j in jars]
    gen_lib(project_dir, "studio-plugin-" + plugin, paths, [workspace + sdk + "/android-studio-sources.zip"])

  common_platform_libs = ["studio-sdk"] + [f"studio-plugin-{plugin}" for plugin in sorted(common_plugins_with_jars)]
  gen_platform_module(f"{project_dir}/studio/studio-sdk-all-plugins.iml", common_platform_libs)

  updater_jar = rel_workspace + sdk + "/updater-full.jar"
  if os.path.exists(project_dir + "/" + updater_jar):
    gen_lib(project_dir, "intellij-updater", [updater_jar], [workspace + sdk + "/android-studio-sources.zip"])

  test_framework_jar = rel_workspace + sdk + "/$SDK_PLATFORM$/lib/testFramework.jar"
  gen_lib(project_dir, "intellij-test-framework", [test_framework_jar], [workspace + sdk + "/android-studio-sources.zip"])


def update_files(workspace, version, mac_bundle_name):
  sdk = "/prebuilts/studio/intellij-sdk/" + version

  ides = {}
  for platform in PLATFORMS:
    ides[platform] = intellij.IntelliJ.create(platform, Path(f'{workspace}{sdk}/{platform}/android-studio'))

  write_xml_files(workspace, sdk, ides)
  mkspec.write_spec_file(workspace + sdk + "/spec.bzl", mac_bundle_name, ides)


def check_artifacts(dir):
  files = sorted(os.listdir(dir))
  if not files:
    sys.exit("There are no artifacts in " + dir)
  regex = re.compile("android-studio-([^.]*)\.(.*)\.([^.-]+)(-sources.zip|.mac.x64-no-jdk.zip|.mac.aarch64-no-jdk.zip|-no-jbr.tar.gz|-no-jbr.win.zip)(.spdx.json)?$")
  files = [file for file in files if regex.match(file) or file == "updater-full.jar"]
  if not files:
    sys.exit("No artifacts found in " + dir)
  match = regex.match(files[0])
  version_major = match.group(1)
  version_minor = match.group(2)
  bid = match.group(3)
  expected = [
      "android-studio-%s.%s.%s-no-jbr.tar.gz" % (version_major, version_minor, bid),
      "android-studio-%s.%s.%s-no-jbr.tar.gz.spdx.json" % (version_major, version_minor, bid),
      "android-studio-%s.%s.%s-no-jbr.win.zip" % (version_major, version_minor, bid),
      "android-studio-%s.%s.%s-no-jbr.win.zip.spdx.json" % (version_major, version_minor, bid),
      "android-studio-%s.%s.%s-sources.zip" % (version_major, version_minor, bid),
      "android-studio-%s.%s.%s.mac.aarch64-no-jdk.zip" % (version_major, version_minor, bid),
      "android-studio-%s.%s.%s.mac.aarch64-no-jdk.zip.spdx.json" % (version_major, version_minor, bid),
      "android-studio-%s.%s.%s.mac.x64-no-jdk.zip" % (version_major, version_minor, bid),
      "android-studio-%s.%s.%s.mac.x64-no-jdk.zip.spdx.json" % (version_major, version_minor, bid),
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

  return "AI", *files, manifest


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

  artifacts = [
    "android-studio-*-sources.zip",
    "android-studio-*.mac.x64-no-jdk.zip", "android-studio-*.mac.x64-no-jdk.zip.spdx.json",
    "android-studio-*.mac.aarch64-no-jdk.zip", "android-studio-*.mac.aarch64-no-jdk.zip.spdx.json",
    "android-studio-*-no-jbr.tar.gz", "android-studio-*-no-jbr.tar.gz.spdx.json",
    "android-studio-*-no-jbr.win.zip", "android-studio-*-no-jbr.win.zip.spdx.json",
    "updater-full.jar",
    "manifest_%s.xml" % bid,
  ]
  for artifact in artifacts:
    os.system(
        "%s %s --bid %s --target IntelliJ '%s' %s"
        % (fetch_artifact, auth_flag, bid, artifact, dir))

  return dir


def write_metadata(path, data):
  with open(os.path.join(path, "METADATA"), "w") as file:
    for k, v in data.items():
      file.write(k + ": " + str(v) + "\n")

def extract(workspace, dir, delete_after, metadata):
  version, linux, linux_sbom, win, win_sbom, sources, mac_arm, mac_arm_sbom, mac, mac_sbom, updater, manifest = check_artifacts(dir)
  path = workspace + "/prebuilts/studio/intellij-sdk/" + version

  if os.path.exists(path):
    shutil.rmtree(path)

  os.mkdir(path)
  shutil.copyfile(dir + "/" + sources, path + "/android-studio-sources.zip")
  shutil.copyfile(dir + "/" + updater, path + "/updater-full.jar")

  os.mkdir(path + "/sbom")
  shutil.copyfile(dir + "/" + linux_sbom, path + "/sbom/linux.spdx.json")
  shutil.copyfile(dir + "/" + mac_sbom, path + "/sbom/darwin.spdx.json")
  shutil.copyfile(dir + "/" + mac_arm_sbom, path + "/sbom/darwin_aarch64.spdx.json")
  shutil.copyfile(dir + "/" + win_sbom, path + "/sbom/windows.spdx.json")

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

  # Workaround for b/267679210.
  print("Patching app.jar to work around b/267679210")
  for platform in PLATFORMS:
    app_jar_path = path + HOME_PATHS[platform] + "/lib/app.jar"
    os.system(f"unzip {app_jar_path} __index__ -d {workspace}")
    os.system(f"jar uf {app_jar_path} -C {workspace} __index__")
    os.remove(f"{workspace}/__index__")

  # TODO(b/328622823): IntelliJ normally loads plugins by consulting plugin-classpath.txt, but
  # this does not work for Android Studio because plugin-classpath.txt is missing our Android
  # plugins (which we bundle later during the Bazel build).
  for platform in PLATFORMS:
    os.remove(path + HOME_PATHS[platform] + "/plugins/plugin-classpath.txt")

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
  parser.add_argument(
      "--workspace",
      default="../../../..",
      dest="workspace",
      help="The workspace where to save all files")

  args = parser.parse_args()
  workspace = os.path.join(os.path.dirname(os.path.realpath(__file__)), args.workspace)

  options = [opt for opt in [args.version, args.download, args.path] if opt]
  if len(options) != 1:
    print("You must specify only one option")
    parser.print_usage()
  else:
    main(workspace, args)

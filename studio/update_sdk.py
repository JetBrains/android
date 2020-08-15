#!/usr/bin/env python3
import os
import argparse
from lxml import etree as ET
import xml.dom.minidom as minidom
import tempfile
import sys
import zipfile
import tarfile
import re
import shutil

JDK_FILES = [
    "jre/lib/charsets.jar", "jre/lib/ext/cldrdata.jar", "jre/lib/ext/dnsns.jar",
    "jre/lib/ext/jaccess.jar", "jre/lib/ext/localedata.jar",
    "jre/lib/ext/nashorn.jar", "jre/lib/ext/sunec.jar",
    "jre/lib/ext/sunjce_provider.jar", "jre/lib/ext/sunpkcs11.jar",
    "jre/lib/ext/zipfs.jar", "jre/lib/jce.jar", "jre/lib/jsse.jar",
    "jre/lib/management-agent.jar", "jre/lib/resources.jar", "jre/lib/rt.jar",
    "lib/tools.jar"
]

# A list of files not included in the SDK because they are maked by files in the root lib directory
# This should be sorted out at a different leve, but for now removing them here
HIDDEN = [
    "/plugins/Kotlin/lib/kotlin-stdlib-jdk8.jar",
    "/plugins/Kotlin/lib/kotlin-stdlib.jar",
    "/plugins/Kotlin/lib/kotlin-stdlib-jdk7.jar",
    "/plugins/Kotlin/lib/kotlin-stdlib-common.jar",
]


def list_sdk_jars(idea_home):
  jars = ["/lib/" + jar for jar in os.listdir(idea_home + "/lib") if jar.endswith(".jar")]
  return [idea_home + path for path in jars]


def list_plugin_jars(idea_home):
  plugin_jars = {}
  for plugin in os.listdir(idea_home + "/plugins"):
    path = "/plugins/" + plugin + "/lib/"
    jars = [path + jar for jar in os.listdir(idea_home + path) if jar.endswith(".jar")]
    jars = [idea_home + jar for jar in jars if jar not in HIDDEN]
    plugin_jars[plugin] = jars
  return plugin_jars


def define_jdk(table,
               name,
               type,
               home_path,
               version=None,
               additional=None,
               annotations=[],
               files=[],
               sources=[]):
  jdk = ET.SubElement(table, "jdk", {"version": "2"})
  ET.SubElement(jdk, "name", {"value": name})
  ET.SubElement(jdk, "type", {"value": type})
  if version:
    ET.SubElement(jdk, "version", {"value": version})
  ET.SubElement(jdk, "homePath", {"value": "$PROJECT_DIR$/" + home_path})
  roots = ET.SubElement(jdk, "roots")
  annotations_path = ET.SubElement(roots, "annotationsPath")
  a_roots = ET.SubElement(annotations_path, "root", {"type": "composite"})
  for annotation in annotations:
    a = ET.SubElement(a_roots, "root")
    a.set("url", annotation)
    a.set("type", "simple")
  class_path = ET.SubElement(roots, "classPath")
  c_roots = ET.SubElement(class_path, "root", {"type": "composite"})
  for file in files:
    r = ET.SubElement(c_roots, "root")
    r.set("url", "jar://$PROJECT_DIR$/" + file + "!/")
    r.set("type", "simple")

  source_path = ET.SubElement(roots, "sourcePath")
  s_roots = ET.SubElement(source_path, "root", {"type": "composite"})
  for source in sources:
    s = ET.SubElement(s_roots, "root")
    s.set("url", "jar://$PROJECT_DIR$/" + source + "!/")
    s.set("type", "simple")
  ET.SubElement(jdk, "additional", {"sdk": additional} if additional else {})
  return jdk


def write_spec_file(workspace, sdk_dir, version, jars, plugin_jars):
  with open(sdk_dir + "/spec.bzl", "w") as file:
    name = version.replace("-", "").replace(".", "_")
    file.write("# Auto-generated file, do not edit manually.\n")
    file.write(name  + " = struct(\n" )
    file.write("    jar_order = [\n")
    for jar in jars:
      file.write("        \"" + os.path.basename(jar) + "\",\n")
    file.write("    ],\n")
    file.write("    plugin_jars = {\n")
    for plugin, jars in plugin_jars.items():
      file.write("        \"" + plugin + "\": [\n")
      for jar in jars:
        file.write("            \"" + os.path.basename(jar) + "\",\n")
      file.write("        ],\n")
    file.write("    },\n")
    file.write(")\n")

def gen_lib(project_dir, name, jars, srcs):
  component = ET.Element("component", {"name": "libraryTable"})
  library = ET.SubElement(component, "library", {"name": name})
  classes = ET.SubElement(library, "CLASSES")
  for jar in jars:
    rel_path = os.path.relpath(jar, project_dir)
    root = ET.SubElement(classes, "root", { "url": f"jar://$PROJECT_DIR$/{rel_path}!/" })

  sources = ET.SubElement(library, "SOURCES")
  for src in srcs:
    rel_path = os.path.relpath(src, project_dir)
    root = ET.SubElement(sources, "root", { "url": f"jar://$PROJECT_DIR$/{rel_path}!/" })

  filename = name.replace("-", "_")
  with open(project_dir + "/.idea/libraries/" + filename + ".xml", "wb") as file:
    file.write(ET.tostring(component, pretty_print=True))


def update_xml_file(workspace, jdk, sdk, jars, plugin_jars):
  app = ET.Element("application")
  table = ET.SubElement(app, "component", {"name": "ProjectJdkTable"})

  project_dir = os.path.join(workspace, "tools/adt/idea")
  jdk_jars = [os.path.join(jdk, j) for j in JDK_FILES]

  define_jdk(
      table,
      name="IDEA jdk",
      type="JavaSDK",
      version="java version \"1.8.0_242\"",
      home_path=os.path.relpath(jdk, project_dir),
      annotations=["jar://$APPLICATION_HOME_DIR$/lib/jdkAnnotations.jar!/"],
      files=[os.path.relpath(jar, project_dir) for jar in jdk_jars],
      sources=[os.path.relpath(os.path.join(jdk, "src.zip"), project_dir)],
  )

  ex_jars = jdk_jars + jars
  define_jdk(
      table,
      name="Android Studio",
      type="IDEA JDK",
      home_path=os.path.relpath(
          os.path.join(sdk, "linux/android-studio"), project_dir),
      files=[os.path.relpath(jar, project_dir) for jar in ex_jars],
      sources=[
          os.path.relpath(os.path.join(jdk, "src.zip"), project_dir),
          os.path.relpath(
              os.path.join(sdk, "android-studio-sources.zip"), project_dir),
      ],
      additional="IDEA jdk",
  )

  with open(project_dir + "/.idea/jdk.table.lin.xml", "wb") as file:
    file.write(ET.tostring(app, pretty_print=True))

  lib_dir = project_dir + "/.idea/libraries/"
  for lib in os.listdir(lib_dir):
    if (lib.startswith("studio_plugin_") and lib.endswith(".xml")) or lib == "intellij_updater.xml":
      os.remove(lib_dir + lib)

  for plugin, jars in plugin_jars.items():
    gen_lib(project_dir, "studio-plugin-" + plugin, jars, [sdk + "/android-studio-sources.zip"])

  updater_jar = sdk + "/updater-full.jar"
  if os.path.exists(updater_jar):
    gen_lib(project_dir, "intellij-updater", [updater_jar], [sdk + "/android-studio-sources.zip"])

def update_files(workspace, version):
  sdk = workspace + "/prebuilts/studio/intellij-sdk/" + version
  jdk = workspace + "/prebuilts/studio/jdk/linux"

  sdk_jars = list_sdk_jars(sdk + "/linux/android-studio")
  plugin_jars = list_plugin_jars(sdk + "/linux/android-studio")

  update_xml_file(workspace, jdk, sdk, sdk_jars, plugin_jars)
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
  os.system("unzip -d \"%s\" \"%s\"" % (path + "/darwin", dir + "/" + mac))
  # TODO: Decide if we want to rename or not, as we need to rename back on bundling
  os.rename(path + "/darwin/Android Studio 0.0 Preview.app",
            path + "/darwin/android-studio")

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

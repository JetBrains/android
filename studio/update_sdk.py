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


def sdk_files(idea_home):
  jars = ["/lib/" + jar for jar in os.listdir(idea_home + "/lib") if jar.endswith(".jar")]
  return [idea_home + path for path in jars]


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


def write_build_file(workspace, sdk_dir, jars):
  with open(sdk_dir + "/BUILD", "w") as file:
    file.write(
        "load(\"//tools/adt/idea/studio:studio.bzl\", \"studio_data\")\n\n")
    file.write("package(default_visibility = [\"//visibility:public\"])\n\n")

    file.write("java_import(\n")
    file.write("    name = \"studio-sdk\",\n")
    file.write("    jars = [\n")
    for jar in jars:
      file.write("        \"" + os.path.relpath(jar, sdk_dir) + "\",\n")
    file.write("    ],\n)\n\n")

    file.write("studio_data(\n")
    file.write("    name = \"studio-platform\",\n")
    file.write("    files_linux = glob([\"linux/**\"]),\n")
    file.write("    files_mac = glob([\"darwin/**\"]),\n")
    file.write("    files_win = glob([\"windows/**\"]),\n")
    file.write("    mappings = {\n")
    file.write("        \"" + os.path.relpath(sdk_dir, workspace) +
               "/linux/\": \"\",\n")
    file.write("        \"" + os.path.relpath(sdk_dir, workspace) +
               "/darwin/android-studio/\": \"Android Studio.app/\",\n")
    file.write("        \"" + os.path.relpath(sdk_dir, workspace) +
               "/darwin/_codesign/\": \"_codesign/\",\n")
    file.write("        \"" + os.path.relpath(sdk_dir, workspace) +
               "/windows/\": \"\",\n")
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


def update_xml_file(workspace, jdk, sdk, jars):
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

  idea_home = sdk + "/linux/android-studio"
  lib_dir = project_dir + "/.idea/libraries/"
  for lib in os.listdir(lib_dir):
    if lib.startswith("studio_plugin_") and lib.endswith(".xml"):
      os.remove(lib_dir + lib)

  for plugin in os.listdir(idea_home + "/plugins"):
    path = "/plugins/" + plugin + "/lib/"
    jars = [path + jar for jar in os.listdir(idea_home + path) if jar.endswith(".jar")]
    jars = [idea_home + jar for jar in jars if jar not in HIDDEN]
    gen_lib(project_dir, "studio-plugin-" + plugin, jars, [sdk + "/android-studio-sources.zip"])

def update_embedded_sdk_xml(workspace, version):
  project_dir = workspace + "/tools/adt/idea/"
  sdk = workspace + "/prebuilts/studio/intellij-sdk/" + version
  jdk = workspace + "/prebuilts/studio/jdk/linux"

  jars = sdk_files(sdk + "/linux/android-studio")

  update_xml_file(workspace, jdk, sdk, jars)
  write_build_file(workspace, sdk, jars)


def check_artifacts(dir, bid):
  linux = None
  mac = None
  win = None
  sources = None
  files = sorted(os.listdir(dir))
  if not files:
    sys.exit("There are no artifacts in " + dir)
  match = re.match("android-studio-(.*)\.%s-sources.zip" % bid, files[0])
  if not match:
    sys.exit("Missing sources.zip artifact in " + dir)
  version = match.group(1)
  expected = [
      "android-studio-%s.%s-sources.zip" % (version, bid),
      "android-studio-%s.%s.mac.zip" % (version, bid),
      "android-studio-%s.%s.tar.gz" % (version, bid),
      "android-studio-%s.%s.win.zip" % (version, bid),
  ]
  if files != expected:
    sys.exit("Unexpected artifacts in " + dir)
  return "AI-" + version, files[0], files[1], files[2], files[3]


def download(workspace, bid):
  ret = os.system("prodcertstatus")
  if ret:
    sys.exit("You need prodaccess to download artifacts")
  if not bid:
    sys.exit("--bid argument needs to be set to download")
  dir = tempfile.mkdtemp(prefix="studio_sdk", suffix=bid)

  for artifact in ["-sources.zip", ".mac.zip", ".tar.gz", ".win.zip"]:
    os.system(
        "/google/data/ro/projects/android/fetch_artifact --bid %s --target studio-sdk 'android-studio-*.%s%s' %s"
        % (bid, bid, artifact, dir))

  version, sources, mac, linux, win = check_artifacts(dir, bid)

  path = workspace + "/prebuilts/studio/intellij-sdk/" + version
  if os.path.exists(path):
    shutil.rmtree(path)
  os.mkdir(path)
  shutil.copyfile(dir + "/" + sources, path + "/android-studio-sources.zip")

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

  shutil.rmtree(dir)
  return version


def main(workspace, args):
  version = None
  if args.bid:
    version = download(workspace, args.bid)
  else:
    version = args.version
  update_embedded_sdk_xml(workspace, version)


if __name__ == "__main__":
  parser = argparse.ArgumentParser()
  parser.add_argument(
      "--bid",
      default="",
      dest="bid",
      help="The build id of the studio-sdk to download from go/ab")
  parser.add_argument(
      "--version",
      default="",
      dest="version",
      help="The build id of the studio-sdk to download from go/ab")
  workspace = os.path.join(
      os.path.dirname(os.path.realpath(__file__)), "../../../..")
  args = parser.parse_args()
  if not args.bid and not args.version:
    parser.print_usage()
  else:
    main(workspace, args)

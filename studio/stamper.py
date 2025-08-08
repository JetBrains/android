"""A utility to stamp files with build numbers."""
import argparse
import datetime
import json
import os
import re
import shutil
import stat
import sys
from tools.adt.idea.studio import utils


def _read_status_file(info_file):
  with open(info_file) as f:
    ret = {}
    for line in f.read().splitlines():
      parts = line.split(" ", 2)
      ret[parts[0]] = parts[1]
  return ret


def _get_build_id(build_info):
  label = build_info["BUILD_EMBED_LABEL"]
  return label if label else "SNAPSHOT"


def _format_build_date(build_version):
  timestamp = build_version["BUILD_TIMESTAMP"]
  time = datetime.datetime.fromtimestamp(int(timestamp))
  return time.strftime("%Y%m%d%H%M")


def _stamp_app_info(version_file, build_txt, micro, patch, full, eap, content):
  build_version = _read_status_file(version_file)
  build = utils.read_file(build_txt)
  build_date = _format_build_date(build_version)

  content = content.replace("__BUILD__", build[3:]) # Without the product code, e.g. 'AI-'
  content = content.replace("__BUILD_DATE__", build_date)
  content = content.replace("__BUILD_NUMBER__", build)

  version_prop = '(<version[^/]* %s=")([^"]*)(")'

  content = re.sub(version_prop % "micro", '\\g<1>%s\\g<3>' % micro, content)
  content = re.sub(version_prop % "patch", '\\g<1>%s\\g<3>' % patch, content)
  content = re.sub(version_prop % "full", '\\g<1>%s\\g<3>' % full, content)

  # Changing the EAP bit requires rebuilding IntelliJ prebuilts (see b/338090219),
  # so here we just assert that the existing value is what we expect.
  platform_is_eap = re.search(version_prop % "eap", content).group(2)
  if eap != platform_is_eap:
    sys.exit(f"ERROR: IntelliJ Platform was built with EAP={platform_is_eap}, but the Bazel build "
             f"expects EAP={eap} based on the Studio release version (see b/338090219 for details)")

  return content


def _stamp_product_info(build_txt, added_plugins, content):
  build_number = utils.read_file(build_txt)

  json_data = json.loads(content)
  json_data["buildNumber"] = build_number[3:] # Without the product code, e.g. 'AI-'
  json_data["version"] = build_number

  # Add metadata for non-platform plugins built by Bazel.
  for plugin_id, *plugin_files in added_plugins:
    classpath_jars = [f for f in plugin_files if re.fullmatch(r"plugins/[^/]*/lib/[^/]*\.jar", f)]
    if len(classpath_jars) == 0:
      sys.exit(f"ERROR: plugin '{plugin_id}' has no classpath jars?")
    json_data["bundledPlugins"].append(plugin_id)
    json_data["layout"].append(
      {
        "name": plugin_id,
        "kind": "plugin",
        "classPath": sorted(classpath_jars),
      }
    )

  return json.dumps(json_data, indent=2)

def _add_essential_plugins(content, essential_plugins):
  if not essential_plugins:
    return content

  xml = ""

  for plugin in essential_plugins:
    xml += "  <essential-plugin>" + plugin + "</essential-plugin>\n"

  return re.sub("\n</component>", "\n" + xml + "</component>", content)

def _overwrite_plugin_version(build_txt, content):
  """Stamps a plugin.xml with the build ids."""

  build_number = utils.read_file(build_txt)
  build = build_number[3:] # removes the AI- prefix
  api_version = ".".join(build.split(".")[:3]) # first 3 components form the IntelliJ API version

  content = re.sub("<version>.*</version>", "<version>%s</version>" % build, content, 1)
  content = re.sub("<idea-version\\s+since-build=\"\\d+\\.\\d+\"\\s+until-build=\"\\d+\\.\\d+\"",
                   "<idea-version since-build=\"%s\" until-build=\"%s\"" % (api_version, api_version),
                   content, 1)
  content = re.sub("<idea-version\\s+since-build=\"\\d+\\.\\d+\"",
                   "<idea-version since-build=\"%s\"" % api_version,
                   content, 1)

  anchor = "</id>" if "</id>" in content else "</name>"
  if "<version>" not in content:
    content = re.sub(anchor, "%s\n  <version>%s</version>" %(anchor, build), content)
  if "<idea-version since-build" not in content:
    content = re.sub(anchor, "%s\n  <idea-version since-build=\"%s\" until-build=\"%s\"/>" % (anchor, api_version, api_version), content)

  return content

def _overwrite_since_until_builds(build_txt, content):
  build_number = utils.read_file(build_txt)
  build = build_number[3:] # removes the AI- prefix
  major_api_version = build.split(".")[0]

  tag = "<idea-version since-build=\"%s\" until-build=\"%s.*\"/>" % (major_api_version, major_api_version)
  if "<idea-version" in content:
    content = re.sub("<idea-version.*/>", tag, content, 1)
  else:
    anchor = "</id>" if "</id>" in content else "</name>"
    content = re.sub(anchor, "%s\n  %s" % (anchor, tag), content)

  return content

def _replace_build_number(content, info_file, version_component):
  # The first 3 build number components come from IntelliJ Platform.
  # We insert the 4th and 5th components at the '__BUILD_NUMBER__' placeholder position.
  build_info = _read_status_file(info_file)
  bid = _get_build_id(build_info)
  return content.replace("__BUILD_NUMBER__", version_component + "." + bid)

def _replace_selector(content, selector):
  return content.replace("_ANDROID_STUDIO_SYSTEM_SELECTOR_", selector)

def _format_build_day(build_version):
  timestamp = build_version["BUILD_TIMESTAMP"]
  time = datetime.datetime.fromtimestamp(int(timestamp))
  return time.strftime("%Y-%m-%d")

def _replace_build_day(version_file, content):
  build_version = _read_status_file(version_file)
  day = _format_build_day(build_version)
  return content.replace("__BUILD_DAY__", day)

def _replace_subs(subs, content):
  for k,v in subs:
    content = content.replace(k, v)
  return content

def _read_file(file, entry = None, optional_entry = False):
  if entry:
    return utils.read_zip_entry(file, entry, optional_entry)
  else:
    return utils.read_file(file)

def _write_file(src, dst, data, entry = None):
  if entry:
    if data is None:
      shutil.copy(src, dst)
    else:
      utils.change_zip_entry(src, entry, data, dst)
  else:
    utils.write_file(dst, data)

def main(argv):
  parser = argparse.ArgumentParser(fromfile_prefix_chars="@")
  parser.add_argument(
      "--stamp",
      nargs=2,
      metavar=("src", "dst"),
      dest="stamp",
      help="Stamps file <src> and saves it in <dst> performing the indicated actions.")
  parser.add_argument(
      "--entry",
      dest="entry",
      help="Whether to treat the input and output as zips and replace this entry in it.")
  parser.add_argument(
      "--optional_entry",
      action="store_true",
      help="Replaces json")
  parser.add_argument(
      "--replace_build_number",
      action="store_true",
      help="Replaces __BUILD_NUMBER__ on the given file using --version_component and --info_file.")
  parser.add_argument(
      "--replace_selector",
      help="Replaces _ANDROID_STUDIO_SYSTEM_SELECTOR_ with the given value")
  parser.add_argument(
      "--replace_build_day",
      action="store_true",
      help="Replaces __BUILD_DAY__ with the current date")
  parser.add_argument(
      "--stamp_app_info",
      action="store_true",
      help="Replaces xml")
  parser.add_argument(
      "--stamp_product_info",
      action="store_true",
      help="Replaces json")
  parser.add_argument(
      "--added_plugin",
      dest="added_plugins",
      nargs="+",
      action="append",
      default=[],
      help="Plugin ID + plugin files, to be listed in product-info.json")
  parser.add_argument(
      "--essential_plugins",
      action="extend",
      nargs="+",
      default=[],
      help="plugins that should not be disabled by users",
      metavar="ESSENTIAL_PLUGIN")
  parser.add_argument(
      "--overwrite_plugin_version",
      action="store_true",
      help="Whether to set the <version> and <idea-version> tags for this plugin.")
  parser.add_argument(
      "--overwrite_since_until_builds",
      action="store_true",
      help="Whether to set the <idea-version> tags to the major version (ie. 242 to 242.*).")
  parser.add_argument(
      "--build_txt",
      default="",
      required = "--stamp_app_info" in sys.argv or "--stamp_product_info" in sys.argv or "--overwrite_plugin_version" in sys.argv,
      dest="build_txt",
      help="The path to the build.txt file.")
  parser.add_argument(
      "--version_micro",
      default="",
      dest="version_micro",
      required = "--stamp_app_info" in sys.argv,
      help="The 'micro' part of the version number: major.minor.micro.patch")
  parser.add_argument(
      "--version_patch",
      default="",
      dest="version_patch",
      required = "--stamp_app_info" in sys.argv,
      help="The 'patch' part of the version number: major.minor.micro.patch")
  parser.add_argument(
      "--version_full",
      default="",
      dest="version_full",
      required = "--stamp_app_info" in sys.argv,
      help="The descriptive form of the version. Can use {0} to refer to version components, like \"{0}.{1} Canary 2\"")
  parser.add_argument(
      "--version_component",
      required = "--replace_build_number" in sys.argv,
      help="The 4th component of the full 5-component build number, identifying a specific Studio release")
  parser.add_argument(
      "--eap",
      default="",
      dest="eap",
      required = "--stamp_app_info" in sys.argv,
      help="Whether this build is a canary/eap build")
  parser.add_argument(
      "--info_file",
      default="",
      dest="info_file",
      required = "--replace_build_number" in sys.argv,
      help="Path to the bazel build info file (bazel-out/stable-status.txt).")
  parser.add_argument(
      "--version_file",
      default="",
      dest="version_file",
      required = "--stamp_app_info" in sys.argv or "--replace_build_day" in sys.argv,
      help="Path to the bazel version file (bazel-out/volatile-status.txt).")
  parser.add_argument(
    '--substitute',
    nargs=2,
    action='append',
    dest='subs',
    metavar=('KEY', 'VALUE'),
    help="Specify a key and value pair to subsitute.\nThis option can be used multiple times."
  )

  args = parser.parse_args(argv)
  content = _read_file(args.stamp[0], args.entry, args.optional_entry)

  if content:
    if args.replace_build_number:
      content = _replace_build_number(content, args.info_file, args.version_component)

    if args.replace_selector:
      content= _replace_selector(content, args.replace_selector)

    if args.stamp_app_info:
      content = _stamp_app_info(args.version_file, args.build_txt, args.version_micro, args.version_patch, args.version_full, args.eap, content)

    if args.essential_plugins:
      content = _add_essential_plugins(content, args.essential_plugins)

    if args.overwrite_plugin_version:
      content = _overwrite_plugin_version(args.build_txt, content)

    if args.overwrite_since_until_builds:
      content = _overwrite_since_until_builds(args.build_txt, content)

    if args.stamp_product_info:
      content = _stamp_product_info(args.build_txt, args.added_plugins, content)

    if args.subs:
      content = _replace_subs(args.subs, content)

    if args.replace_build_day:
      content = _replace_build_day(args.version_file, content)

  _write_file(args.stamp[0], args.stamp[1], content, args.entry)

if __name__ == "__main__":
  main(sys.argv[1:])

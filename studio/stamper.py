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
    sys.exit(f"IntelliJ prebuilts must be updated to set EAP={eap}; see b/338090219 for details")

  return content


def _stamp_product_info(info_file, build_txt, content):
  build_info = _read_status_file(info_file)
  build_number = utils.read_file(build_txt)
  bid = _get_build_id(build_info)

  json_data = json.loads(content)
  json_data["buildNumber"] = json_data["buildNumber"].replace("__BUILD_NUMBER__", bid)
  json_data["version"] = build_number
  return json.dumps(json_data, sort_keys=True, indent=2)


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


def _replace_build_number(content, info_file):
  build_info = _read_status_file(info_file)
  bid = _get_build_id(build_info)
  return content.replace("__BUILD_NUMBER__", bid)

def _replace_selector(content, selector):
  return content.replace("_ANDROID_STUDIO_SYSTEM_SELECTOR_", selector)


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
  parser = argparse.ArgumentParser()
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
      help="Replaces __BUILD_NUMBER__ on the given file using --info_file.")
  parser.add_argument(
      "--replace_selector",
      help="Replaces _ANDROID_STUDIO_SYSTEM_SELECTOR_ with the given value")
  parser.add_argument(
      "--stamp_app_info",
      action="store_true",
      help="Replaces xml")
  parser.add_argument(
      "--stamp_product_info",
      action="store_true",
      help="Replaces json")
  parser.add_argument(
      "--overwrite_plugin_version",
      action="store_true",
      help="Whether to set the <version> and <idea-version> tags for this plugin.")
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
      "--eap",
      default="",
      dest="eap",
      required = "--stamp_app_info" in sys.argv,
      help="Whether this build is a canary/eap build")
  parser.add_argument(
      "--info_file",
      default="",
      dest="info_file",
      required = "--replace_build_number" in sys.argv or "--stamp_product_info" in sys.argv,
      help="Path to the bazel build info file (bazel-out/stable-status.txt).")
  parser.add_argument(
      "--version_file",
      default="",
      dest="version_file",
      required = "--stamp_app_info" in sys.argv,
      help="Path to the bazel version file (bazel-out/volatile-status.txt).")
  args = parser.parse_args(argv)
  content = _read_file(args.stamp[0], args.entry, args.optional_entry)

  if content:
    if args.replace_build_number:
      content = _replace_build_number(content, args.info_file)
    
    if args.replace_selector:
      content= _replace_selector(content, args.replace_selector)

    if args.stamp_app_info:
      content = _stamp_app_info(args.version_file, args.build_txt, args.version_micro, args.version_patch, args.version_full, args.eap, content)

    if args.overwrite_plugin_version:
      content = _overwrite_plugin_version(args.build_txt, content)

    if args.stamp_product_info:
      content = _stamp_product_info(args.info_file, args.build_txt, content)

  _write_file(args.stamp[0], args.stamp[1], content, args.entry)

if __name__ == "__main__":
  main(sys.argv[1:])

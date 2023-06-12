"""A utility to stamp files with build numbers."""
import argparse
import datetime
import fnmatch
import json
import io
import re
import sys
import zipfile


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


def _stamp_app_info(build_date, build, micro, patch, full, eap, content):
  content = content.replace("__BUILD__", build[3:]) # Without the product code, e.g. 'AI-'
  content = content.replace("__BUILD_DATE__", build_date)
  content = content.replace("__BUILD_NUMBER__", build)

  version_prop = '(<version[^/]* %s=")[^"]*(")'

  content = re.sub(version_prop % "micro", '\\g<1>%s\\2' % micro, content)
  content = re.sub(version_prop % "patch", '\\g<1>%s\\2' % patch, content)
  content = re.sub(version_prop % "full", '\\g<1>%s\\2' % full, content)
  content = re.sub(version_prop % "eap", '\\g<1>%s\\2' % eap, content)

  return content


def _stamp_product_info_json(build, version, content):
  json_data = json.loads(content)
  json_data["buildNumber"] = json_data["buildNumber"].replace("__BUILD_NUMBER__", build)
  json_data["version"] = version
  return json.dumps(json_data, sort_keys=True, indent=2)

def _stamp_plugin_file(build, content):
  """Stamps a plugin.xml with the build ids."""

  # TODO: Start with the IJ way of doing this, but move to a more robust/strict later.
  content = re.sub("<version>[\\d.]*</version>", "<version>%s</version>" % build, content, 1)
  content = re.sub("<idea-version\\s+since-build=\"\\d+\\.\\d+\"\\s+until-build=\"\\d+\\.\\d+\"",
                   "<idea-version since-build=\"%s\" until-build=\"%s\"" % (build, build),
                   content, 1)
  content = re.sub("<idea-version\\s+since-build=\"\\d+\\.\\d+\"",
                   "<idea-version since-build=\"%s\"" % build,
                   content, 1)

  anchor = "</id>" if "</id>" in content else "</name>"
  if "<version>" not in content:
    content = re.sub(anchor, "%s\n  <version>%s</version>" %(anchor, build), content)
  if "<idea-version since-build" not in content:
    content = re.sub(anchor, "%s\n  <idea-version since-build=\"%s\" until-build=\"%s\"/>" % (anchor, build, build), content)

  return content


# Finds a file in a zip of zips. The file to look for is called
# <sub_entry> and it is looked for in all the entries of
# <zip_path> that match the glob regex <entry>. It returns
# a pair with the entry where it was found, and its content.
def _find_file(zip_path, entry, sub_entry):
  entry_name = content = None
  with zipfile.ZipFile(zip_path) as zip:
    for name in zip.namelist():
      if fnmatch.fnmatch(name, entry):
        data = zip.read(name)
        with zipfile.ZipFile(io.BytesIO(data)) as sub:
          if sub_entry in sub.namelist():
            if entry_name:
              print("Multiple " + sub_entry + " found in " + zip_path + "!" + entry)
              sys.exit(1)
            entry_name = name
            content = sub.read(sub_entry)
    if not entry_name:
      print(sub_entry + " not found in " + zip_path)
      sys.exit(1)
  return entry_name, content.decode("utf-8")


def _read_file(zip_path, entry, sub_entry=None):
  with zipfile.ZipFile(zip_path) as zip:
    data = zip.read(entry)
    if sub_entry:
      with zipfile.ZipFile(io.BytesIO(data)) as sub:
        data = sub.read(sub_entry)
  return data.decode("utf-8")


def _write_file(zip_path, mode, data, entry, sub_entry=None):
  if sub_entry:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as sub:
      sub.writestr(sub_entry, data)
    data = buffer.getvalue()
  with zipfile.ZipFile(zip_path, mode) as zip:
    info = zipfile.ZipInfo(entry)
    info.external_attr = 0o100664 << 16
    zip.writestr(info, data)


RES_PATH = {
  "linux": "",
  "win": "",
  "mac": "Contents/Resources/",
  "mac_arm": "Contents/Resources/",
}


BASE_PATH = {
  "linux": "",
  "win": "",
  "mac": "Contents/",
  "mac_arm": "Contents/",
}


# Stamps a plugin's plugin.xml file. if <overwrite_plugin_version> is true it
# performs a full stamping and tag fixing (such as adding missing tags, fixing
# from and to versions etc). Otherwise it simple replaces the build
# number.
def _stamp_plugin(platform, os, build_info, overwrite_plugin_version, src, dst):
  resource_path = RES_PATH[os]
  jar_name, content = _find_file(src, "**/*.jar", "META-INF/plugin.xml")
  bid = _get_build_id(build_info)

  if overwrite_plugin_version:
    build_txt = _read_file(platform, resource_path + "build.txt")
    content = _stamp_plugin_file(build_txt[3:], content)

  content = content.replace("__BUILD_NUMBER__", bid)

  _write_file(dst, "w", content, jar_name, "META-INF/plugin.xml")


def _produce_manifest(platform, os, build_info, build_version, channel,
                      code_name, eap, micro, patch, full, out):
  """Produces a manifest with build information.

  The manifest follows the proto defined at
  wireless/android/devtools/infra/release/studio/proto/build_metadata.proto.

  The build manifest should be platform-independent, but much of the
  information in the manifest has to come from platform files.
  """
  resource_path = RES_PATH[os]
  base_path = BASE_PATH[os]
  bid = _get_build_id(build_info)

  build_txt = _read_file(platform, resource_path + "build.txt")
  app_info = _read_file(platform, base_path + "lib/resources.jar",
                        "idea/AndroidStudioApplicationInfo.xml")

  build_txt = build_txt.replace("__BUILD_NUMBER__", bid)
  build_date = _format_build_date(build_version)

  # Remove the product code (e.g. "AI-")
  build_number = build_txt[3:]

  channel = "CHANNEL_" + channel.upper()

  m = re.search(r'version.*major="(\d+)"', app_info)
  major = m.group(1)

  m = re.search(r'version.*minor="(\d+)"', app_info)
  minor = m.group(1)

  # full may contain "{0} {1} {2}" as placeholders for version components
  full = full.format(major, minor, micro)

  contents = ('major: {major}\n'
             'minor: {minor}\n'
             'micro: {micro}\n'
             'patch: {patch}\n'
             'build_number: "{build_number}"\n'
             'code_name: "{code_name}"\n'
             'full_name: "{full_name}"\n'
             'channel: {channel}\n'
  ).format(major=major, minor=minor, micro=micro, patch=patch,
           build_number=build_number, code_name=code_name,
           full_name=full, channel=channel)

  with open(out, "a") as f:
    f.write(contents)


def _stamp_platform(platform, os, build_info, build_version, eap, micro, patch, full, out):
  resource_path = RES_PATH[os]
  base_path = BASE_PATH[os]
  bid = _get_build_id(build_info)

  build_txt = _read_file(platform, resource_path + "build.txt")
  app_info = _read_file(platform, base_path + "lib/resources.jar", "idea/AndroidStudioApplicationInfo.xml")

  build_txt = build_txt.replace("__BUILD_NUMBER__", bid)
  build_date = _format_build_date(build_version)
  app_info = _stamp_app_info(build_date, build_txt, micro, patch, full, eap, app_info)

  _write_file(out, "w", build_txt, resource_path + "build.txt")
  _write_file(out, "a", app_info, base_path + "lib/resources.jar", "idea/AndroidStudioApplicationInfo.xml")

  if os == "linux" or os == "mac" or os == "mac_arm":
    product_info_json = _read_file(platform, resource_path + "product-info.json")
    product_info_json = _stamp_product_info_json(bid, build_txt, product_info_json)
    _write_file(out, "a", product_info_json, resource_path + "product-info.json")

  if os == "mac" or os == "mac_arm":
    info_plist = _read_file(platform, base_path + "Info.plist")
    info_plist = info_plist.replace("__BUILD_NUMBER__", bid)
    _write_file(out, "a", info_plist, base_path + "Info.plist")


def main(argv):
  parser = argparse.ArgumentParser()
  parser.add_argument(
      "--stamp_plugin",
      nargs=2,
      metavar=("src", "dst"),
      dest="stamp_plugin",
      help="Stamps plugin zip <in> and saves stampped files in an <out> zip.")
  parser.add_argument(
      "--overwrite_plugin_version",
      action="store_true",
      help="Whether to set the <version> and <idea-version> tags for this plugin.")
  parser.add_argument(
      "--stamp_platform",
      default="",
      dest="stamp_platform",
      help="The path to the output zipfile with the stamped platform files.")
  parser.add_argument(
      "--os",
      default="",
      dest="os",
      choices = ["linux", "mac", "mac_arm", "win"],
      help="The operating system the platform belongs to")
  parser.add_argument(
      "--version_micro",
      default="",
      dest="version_micro",
      help="The 'micro' part of the version number: major.minor.micro.patch")
  parser.add_argument(
      "--version_patch",
      default="",
      dest="version_patch",
      help="The 'patch' part of the version number: major.minor.micro.patch")
  parser.add_argument(
      "--version_full",
      default="",
      dest="version_full",
      help="The descriptive form of the version. Can use {0} to refer to version components, like \"{0}.{1} Canary 2\"")
  parser.add_argument(
      "--eap",
      default="",
      dest="eap",
      help="Whether this build is a canary/eap build")
  parser.add_argument(
      "--platform",
      default="",
      required=True,
      dest="platform",
      help="Path to the unstamped platform zip file.")
  parser.add_argument(
      "--info_file",
      default="",
      dest="info_file",
      required=True,
      help="Path to the bazel build info file (bazel-out/stable-status.txt).")
  parser.add_argument(
      "--version_file",
      default="",
      dest="version_file",
      required=True,
      help="Path to the bazel version file (bazel-out/volatile-status.txt).")
  parser.add_argument(
      "--produce_manifest",
      default="",
      dest="produce_manifest",
      help="Path at which this will produce a standalone manifest with build information.")
  parser.add_argument(
      "--channel",
      default="",
      dest="channel",
      help="One of the release channels, e.g. Canary or Beta.")
  parser.add_argument(
      "--code_name",
      default="",
      dest="code_name",
      help="The code name, e.g. Bumblebee or Dolphin.")
  args = parser.parse_args(argv)
  build_info = _read_status_file(args.info_file)
  build_version = _read_status_file(args.version_file)

  if args.stamp_platform:
    _stamp_platform(
        platform = args.platform,
        os = args.os,
        build_info = build_info,
        build_version = build_version,
        eap = args.eap,
        micro = args.version_micro,
        patch = args.version_patch,
        full = args.version_full,
        out = args.stamp_platform)
  if args.stamp_plugin:
    _stamp_plugin(args.platform, args.os, build_info, args.overwrite_plugin_version, args.stamp_plugin[0], args.stamp_plugin[1])
  if args.produce_manifest:
    _produce_manifest(
        platform = args.platform,
        os = args.os,
        build_info = build_info,
        build_version = build_version,
        channel = args.channel,
        code_name = args.code_name,
        eap = args.eap,
        micro = args.version_micro,
        patch = args.version_patch,
        full = args.version_full,
        out = args.produce_manifest)

if __name__ == "__main__":
  main(sys.argv[1:])

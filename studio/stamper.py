"""A utility to stamp files with build numbers."""
import argparse
import datetime
import re
import sys


def _read_status_file(info_file):
  with open(info_file) as f:
    ret = {}
    for line in f.read().splitlines():
      parts = line.split(" ", 2)
      ret[parts[0]] = parts[1]
  return ret


def _read_build_file(build_file):
  with open(build_file) as f:
    data = f.read().strip()
    if not data.startswith("AI-"):
      print("Unexpected product code in build id: " + data)
      sys.exit(1)
    return data[len("AI-"):]


def _stamp_build_number(build_info, data):
  label = build_info["BUILD_EMBED_LABEL"]
  if not label:
    label = "SNAPSHOT"
  return data.replace("__BUILD_NUMBER__", label)


def _format_build_date(build_version):
  timestamp = build_version["BUILD_TIMESTAMP"]
  time = datetime.datetime.fromtimestamp(int(timestamp))
  return time.strftime("%Y%m%d%H%M")


def _stamp_app_info(build_info, build_version, build_id, version, full, eap, src, dst):
  m = re.match("(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)", version)
  if not m:
    print("version must be of the form 1.2.3.4")
    sys.exit(1)
  major, minor, micro, patch = m.groups()
  with open(src) as s:
    content = s.read().replace("__BUILD__", build_id)
    content = content.replace("__BUILD_DATE__", _format_build_date(build_version))
    content = _stamp_build_number(build_info, content)
    arg = "\\s+%s=\".*\""
    content, n = re.subn("<version%s%s%s%s%s%s" % (arg % "major", arg % "minor", arg % "micro", arg % "patch", arg % "full", arg % "eap"),
                         "<version major=\"%s\" minor=\"%s\" micro=\"%s\" patch=\"%s\" full=\"%s\" eap=\"%s\"" % (major, minor, micro, patch, full, eap),
                         content, 1)
    if n != 1:
      print("Cannot replace version number in ApplicationInfo.xml")
      sys.exit(1)
  with open(dst, "w") as d:
    d.write(content)


def _stamp_file(build_info, src, dst):
  with open(src) as s:
    content = _stamp_build_number(build_info, s.read())
  with open(dst, "w") as d:
    d.write(content)


def _stamp_plugin(build_info, build_id, src, dst):
  """Stamps a plugin.xml with the build ids."""
  version = _stamp_build_number(build_info, build_id)
  with open(src) as s:
    content = s.read()

  # TODO: Start with the IJ way of doing this, but move to a more robust/strict later.
  content = re.sub("<version>[\\d.]*</version>", "<version>%s</version>" % version, content, 1)
  content = re.sub("<idea-version\\s+since-build=\"\\d+\\.\\d+\"\\s+until-build=\"\\d+\\.\\d+\"",
                   "<idea-version since-build=\"%s\" until-build=\"%s\"" % (version, version),
                   content, 1)
  content = re.sub("<idea-version\\s+since-build=\"\\d+\\.\\d+\"",
                   "<idea-version since-build=\"%s\"" % version,
                   content, 1)

  anchor = "</id>" if "</id>" in content else "</name>"
  if "<version>" not in content:
    content = re.sub(anchor, "%s\n  <version>%s</version>" %(anchor, version), content)
  if "<idea-version since-build" not in content:
    content = re.sub(anchor, "%s\n  <idea-version since-build=\"%s\" until-build=\"%s\"/>" % (anchor, version, version), content)
  with open(dst, "w") as d:
    d.write(content)


def main(argv):
  parser = argparse.ArgumentParser()
  parser.add_argument(
      "--stamp_plugin",
      default=[],
      nargs=2,
      action="append",
      metavar=("in", "out"),
      dest="stamp_plugin",
      help="Creates a stamped version of the plugin.xml <in> in <out>.")
  parser.add_argument(
      "--version",
      default="",
      dest="version",
      help="The version number in the form 1.2.3.4")
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
      "--stamp_app_info",
      default=[],
      metavar=("in", "out"),
      nargs=2,
      action="append",
      dest="stamp_app_info",
      help="Stamps the ApplicationInfo.xml with the build ids and versions.")
  parser.add_argument(
      "--stamp_build",
      default=[],
      metavar=("in", "out"),
      nargs=2,
      action="append",
      dest="stamp_build",
      help="Replaces __BUILD_NUMBER__ with the final value.")
  parser.add_argument(
      "--build_file",
      default="",
      required=True,
      dest="build_file",
      help="Path to the build.txt distribution file.")
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
  args = parser.parse_args(argv)
  build_id = _read_build_file(args.build_file)
  build_info = _read_status_file(args.info_file)
  build_version = _read_status_file(args.version_file)
  for src, dst in args.stamp_plugin:
    _stamp_plugin(build_info, build_id, src, dst)
  for src, dst in args.stamp_build:
    _stamp_file(build_info, src, dst)
  for src, dst in args.stamp_app_info:
    _stamp_app_info(build_info, build_version, build_id, args.version, args.version_full, args.eap, src, dst)


if __name__ == "__main__":
  main(sys.argv[1:])

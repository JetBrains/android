"""A utility to stamp files with build numbers."""
import argparse
import re
import sys

def _read_info_file(info_file):
  with open(info_file) as f:
    ret = {}
    for line in f.read().splitlines():
      parts = line.split(" ", 2)
      ret[parts[0]] = parts[1]
  return ret


def _read_build_file(build_file):
  with open(build_file) as f:
    return f.read().strip()


def _stamp_build_number(build_info, data):
  label = build_info["BUILD_EMBED_LABEL"]
  if not label:
    label = "SNAPSHOT"
  return data.replace("__BUILD_NUMBER__", label)


def _read_version(build_id, build_info):
  if not build_id.startswith("AI-"):
    print("Unexpected product code in build id: " + build_id)
    sys.exit(1)
  return _stamp_build_number(build_info, build_id[len("AI-"):])


def _stamp_file(build_info, src, dst):
  with open(src) as s:
    content = _stamp_build_number(build_info, s.read())
  with open(dst, "w") as d:
    d.write(content)


def _stamp_plugin(build_info, build_id, src, dst):
  """Stamps a plugin.xml with the build ids."""
  version = _read_version(build_id, build_info)
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
      help="Path to the bazel build info file.")
  args = parser.parse_args(argv)
  build_id = _read_build_file(args.build_file)
  build_info = _read_info_file(args.info_file)
  for src, dst in args.stamp_plugin:
    _stamp_plugin(build_info, build_id, src, dst)
  for src, dst in args.stamp_build:
    _stamp_file(build_info, src, dst)


if __name__ == "__main__":
  main(sys.argv[1:])

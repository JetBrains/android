"""Produces a textproto with build information.

The manifest follows the proto defined at
wireless/android/devtools/infra/release/studio/proto/build_metadata.proto.

The build manifest should be platform-independent, but much of the
information in the manifest has to come from platform files.
"""

import argparse
import re
import sys
from tools.adt.idea.studio import utils


def _produce_manifest(build_txt, resources_jar, channel, code_name, out):
  app_info = utils.read_zip_entry(resources_jar, "idea/AndroidStudioApplicationInfo.xml")
  platform_build_txt = utils.read_file(build_txt)

  # Remove the product code (e.g. "AI-")
  build_number = platform_build_txt[3:]

  channel = "CHANNEL_" + channel.upper()

  m = re.search(r'version.*major="(\d+)"', app_info)
  major = m.group(1)

  m = re.search(r'version.*minor="(\d+)"', app_info)
  minor = m.group(1)

  m = re.search(r'version.*micro="(\d+)"', app_info)
  micro = m.group(1)

  m = re.search(r'version.*patch="(\d+)"', app_info)
  patch = m.group(1)

  m = re.search(r'version.*full="([^\"]+)"', app_info)
  full = m.group(1)

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

  utils.write_file(out, contents)

def main(argv):
  parser = argparse.ArgumentParser()

  parser.add_argument(
      "--build_txt",
      default="",
      dest="build_txt",
      help="The path to the build.txt file.")
  parser.add_argument(
      "--resources_jar",
      default="",
      dest="resources_jar",
      help="The path to the resources.jar file.")
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
  parser.add_argument(
      "--out",
      default="",
      dest="out",
      help="Path at which this will produce a standalone manifest with build information.")


  args = parser.parse_args(argv)

  _produce_manifest(
      build_txt = args.build_txt,
      resources_jar = args.resources_jar,
      channel = args.channel,
      code_name = args.code_name,
      out = args.out)


if __name__ == "__main__":
  main(sys.argv[1:])
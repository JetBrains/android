#!/usr/bin/env python3
import argparse
from collections import defaultdict
import os
import intellij


def write_spec_file(out, mac_bundle_name, ides):
  sdk_versions = {}
  for platform, ide in ides.items():
    sdk_versions[platform] = ide.version()
  if len(set(sdk_versions.values())) > 1:
    raise ValueError(
        f"Major and minor versions differ between OS platforms! {sdk_versions}"
    )

  with open(out, "w") as file:
    file.write("# Auto-generated file, do not edit manually.\n")
    file.write("SPEC = struct(\n")
    file.write(f'    major_version = "{ide.major}",\n')
    file.write(f'    minor_version = "{ide.minor}",\n')

    common_jars = set.intersection(
        *[ide.platform_jars for ide in ides.values()]
    )
    sdk_jars = {
        "_" + platform: ide.platform_jars - common_jars
        for platform, ide in ides.items()
    }
    sdk_jars[""] = common_jars
    for s, jars in sorted(sdk_jars.items()):
      file.write(f"    jars{s} = [\n")
      for jar in sorted(jars):
        file.write('        "' + jar + '",\n')
      file.write("    ],\n")

    all_plugin_ids = set.union(
        *[set(ide.plugin_jars.keys()) for ide in ides.values()]
    )

    common_plugin_jars = defaultdict(set)
    for id in all_plugin_ids:
      jar_sets = [
          ide.plugin_jars[id] if id in ide.plugin_jars else set()
          for ide in ides.values()
      ]
      common_plugin_jars[id] = set.intersection(*jar_sets)

    plugin_jars = {}
    plugin_jars[""] = common_plugin_jars
    for platform, ide in ides.items():
      plugin_jars["_" + platform] = {}
      for id, jars in ide.plugin_jars.items():
        plugin_jars["_" + platform][id] = jars - common_plugin_jars[id]

    for s, pjars in sorted(plugin_jars.items()):
      file.write(f"    plugin_jars{s} = {{\n")
      for plugin in sorted(pjars.keys()):
        jars = pjars[plugin]
        if jars:
          file.write('        "' + plugin + '": [\n')
          for jar in sorted(jars):
            file.write('            "' + jar[1:] + '",\n')
          file.write("        ],\n")
      file.write("    },\n")

    file.write(f'    mac_bundle_name = "{mac_bundle_name}",\n')
    file.write(")\n")


def main(args):
  ide = intellij.IntelliJ.create(intellij.LINUX, os.path.abspath(args.path))
  write_spec_file(os.path.abspath(args.out), "", {intellij.LINUX: ide})


if __name__ == "__main__":
  parser = argparse.ArgumentParser()
  parser.add_argument(
      "--path", default=".", dest="path", help="The path to the IDE"
  )
  parser.add_argument(
      "--out", default="spec.bzl", dest="out", help="Where to save the bzl"
  )
  args = parser.parse_args()
  main(args)

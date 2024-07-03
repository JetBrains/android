"""A tool to check consistency of an intellij plugin."""
from pathlib import Path
import argparse
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from tools.adt.idea.studio import intellij

def check_plugin(plugin_id, files, deps, external_xmls, out):
  element = intellij.load_plugin_xml(files, external_xmls)

  ids = [id.text for id in element.findall("id")]

  if not ids:
    # If id is not found, IJ uses name
    # https://jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_configuration_file.html
    ids = [id.text for id in element.findall("name")]

  if len(set(ids)) != 1:
    print("Expected exactly one id, but found [%s]" % ",".join(ids))
    sys.exit(1)
  found_id = ids[0]

  if plugin_id and found_id != plugin_id:
    print("Expected plugin id to be %s, but found %s" % (plugin_id, found_id))
    sys.exit(1)

  if element.tag != 'idea-plugin':
    print("Expected plugin.xml root item to be 'idea-plugin' but was %s" % element.tag)
    sys.exit(1)

  if element.attrib.get("allow-bundled-update", "false") != "false" and found_id != "org.jetbrains.kotlin":
      print("Bundled plugin update are not allowed for plugin: %s" % found_id)
      sys.exit(1)

  if deps is not None:
    depends_xml = set()
    for e in element.findall("depends"):
      # We only validate plugin dependencies not module ones
      if e.text in [
          "com.intellij.modules.java",
          "com.intellij.modules.lang",
          "com.intellij.modules.platform",
          "com.intellij.modules.vcs",
          "com.intellij.modules.xdebugger",
          "com.intellij.modules.xml",
          "com.intellij.modules.androidstudio",
      ]:
        continue
      # Ignore optional dependencies, some are against IJ ultimate plugins which we don't have
      if e.get("optional") == "true":
        continue
      depends_xml.add(e.text)

    depends_build = set()
    for d in deps:
      with open(d, "r") as info:
        depends_build.add(info.read())
    if depends_build != depends_xml:
      print("Error while checking plugin dependencies")
      for d in depends_build - depends_xml:
        print("The build depends on plugin \"%s\", but this dependency is not declared in the plugin.xml." % d)
      for d in depends_xml - depends_build:
        print("The plugin.xml declares a dependency on \"%s\", but it's not declared in the build." % d)
      sys.exit(1)

  with open(out, "w") as info:
    info.write(found_id)


if __name__ == "__main__":
  parser = argparse.ArgumentParser()
  parser.add_argument(
      "--files",
      dest="files",
      nargs="+",
      type=Path,
      help="Path to files included in the plugin.")
  parser.add_argument(
      "--deps",
      dest="deps",
      default=None,
      nargs="*",
      help="Ids of the plugins this plugin depends on.")
  parser.add_argument(
      "--external_xmls",
      dest="external_xmls",
      default=[],
      nargs="*",
      help="xmls files that this plugin can include but are not present.")
  parser.add_argument(
      "--plugin_id",
      dest="plugin_id",
      help="The expected id of this plugin.")
  parser.add_argument(
      "--out",
      dest="out",
      help="Path to a file where to save the plugin information.")
  args = parser.parse_args()
  check_plugin(args.plugin_id, args.files, args.deps, args.external_xmls, args.out)

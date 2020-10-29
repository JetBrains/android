"""A tool to check consistency of an intellij plugin."""
import argparse
import io
import os
import re
import xml.etree.ElementTree as ET
import zipfile
import sys

def check_plugin(plugin_id, files, deps, out):
  xmls = {}
  for file in files:
    if file.endswith(".jar"):
      with zipfile.ZipFile(file) as jar:
        for jar_entry in jar.namelist():
          if jar_entry == "META-INF/plugin.xml":
            xmls[file + "!" + jar_entry] = jar.read(jar_entry)

  if len(xmls) != 1:
    msg = "\n".join(xmls.keys())
    print("Plugin should have exactly one plugin.xml file (found %d)" % len(xmls))
    print(msg)
    sys.exit(1)

  _, xml = list(xmls.items())[0]
  element = ET.fromstring(xml)
  ids = [id.text for id in element.findall("id")]

  if len(ids) != 1:
      print("Expected exactly one id, but found [%s]" % ",".join(ids))
      sys.exit(1)
  found_id = ids[0]

  if plugin_id and found_id != plugin_id:
    print("Expected plugin id to be %d, but found %s" % (plugin_id, found_id))
    sys.exit(1)

  if deps != None:
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
      ]:
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
      help="Path to files included in the plugin.")
  parser.add_argument(
      "--deps",
      dest="deps",
      default=None,
      nargs="*",
      help="Ids of the plugins this plugin depends on.")
  parser.add_argument(
      "--plugin_id",
      dest="plugin_id",
      help="The expected id of this plugin.")
  parser.add_argument(
      "--out",
      dest="out",
      help="Path to a file where to save the plugin information.")
  args = parser.parse_args()
  check_plugin(args.plugin_id, args.files, args.deps, args.out)

"""A tool to check consistency of an intellij plugin."""
import argparse
import io
import os
import re
import xml.etree.ElementTree as ET
import zipfile
import sys

def check_plugin(plugin_id, files, out):
  xmls = {}
  for file in files:
    if file.endswith(".jar"):
      with zipfile.ZipFile(file) as jar:
        for jar_entry in jar.namelist():
          if jar_entry == "META-INF/plugin.xml":
            xmls[file + "!" + jar_entry] = jar.read(jar_entry)

  if len(xmls) != 1:
    msg = "\n".join(xmls.keys())
    print("Plugin %s should have exactly one plugin.xml file (found %d)" % (plugin_id, len(xmls)))
    print(msg)
    sys.exit(1)

  _, xml = list(xmls.items())[0]
  element = ET.fromstring(xml)
  ids = [id.text for id in element.findall("id")]

  if ids != [plugin_id]:
    print("Expected exactly one id of the form \"%s\", but found [%s]" % (plugin_id, ",".join(ids)))
    sys.exit(1)

  open(out, "w").close()


if __name__ == "__main__":
  parser = argparse.ArgumentParser()
  parser.add_argument(
      "--files",
      dest="files",
      nargs="+",
      help="Path to files included in the plugin.")
  parser.add_argument(
      "--plugin_id",
      dest="plugin_id",
      required=True,
      help="The expected plugin id.")
  parser.add_argument(
      "--out",
      dest="out",
      help="Path to a log file where to save the result of the analysis.")
  args = parser.parse_args()
  check_plugin(args.plugin_id, args.files, args.out)

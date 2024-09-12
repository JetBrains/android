"""A tool to check consistency of an intellij plugin."""
from pathlib import Path
import argparse
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from tools.adt.idea.studio import intellij

def check_plugin(plugin_id, files, deps, external_xmls, out):
  if len(files) == 1 and files[0].match("**/lib/modules/*.jar"):
    # This is a v2 module, not a plugin per se. See go/studio-v2-modules for details.
    kind = "module"
    found_id = files[0].stem
    element = intellij.load_plugin_xml(files, external_xmls, f"{found_id}.xml")
  else:
    kind = "plugin"
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

  # Disallow updates for bundled plugins. We enforce this even for JetBrains plugins, because
  # we want to guarantee compatibility between plugins, and because we want platform plugins
  # to always come from our own IntelliJ fork (which may have patches, for example).
  if element.attrib.get("allow-bundled-update", "false") != "false" and found_id != "org.jetbrains.kotlin":
      print("Bundled plugin update are not allowed for plugin: %s" % found_id)
      sys.exit(1)

  if deps is not None:
    # Check for duplicate <dependencies> elements, because duplicates get
    # silently overwritten at runtime at XmlReader.readDependencies().
    if len(element.findall("dependencies")) > 1:
      sys.exit(f"ERROR: found multiple <dependencies> elements in plugin.xml for plugin '{found_id}'")

    # Collect plugin.xml dependencies, handling both v1 and v2 syntax.
    # Each dependency is represented as a pair: (kind, ID).
    depends_xml = set()
    for e in element.findall("depends"):
      if e.get("optional") != "true":
        depends_xml.add(("plugin", e.text))
    for e in element.findall("dependencies/plugin"):
      depends_xml.add(("plugin", e.attrib["id"]))
    for e in element.findall("dependencies/module"):
      depends_xml.add(("module", e.attrib["name"]))

    # Ignore "marker" modules; we do not validate them.
    marker_modules = [
      "com.intellij.modules.java",
      "com.intellij.modules.lang",
      "com.intellij.modules.platform",
      "com.intellij.modules.vcs",
      "com.intellij.modules.xdebugger",
      "com.intellij.modules.xml",
      "com.intellij.modules.androidstudio",
    ]
    for m in marker_modules:
      depends_xml.discard(("plugin", m))

    # Collect build dependencies.
    depends_build = set()
    for d in deps:
      with open(d, "r") as info:
        dep_kind, dep_id = info.read().split(":")
        depends_build.add((dep_kind, dep_id))

    # Check consistency between plugin.xml and build dependencies.
    if depends_build != depends_xml:
      print("Error while checking plugin dependencies")
      for dep_kind, dep_id in depends_build - depends_xml:
        print(
          f"ERROR: Plugin '{found_id}' depends on {dep_kind} '{dep_id}' in the build, "
          "but this dependency is not declared in the plugin.xml file."
        )
      for dep_kind, dep_id in depends_xml - depends_build:
        print(
          f"ERROR: Plugin '{found_id}' depends on {dep_kind} '{dep_id}' in the plugin.xml file, "
          "but this dependency is not declared in the build."
        )
      sys.exit(1)

  with open(out, "w") as info:
    info.write(f"{kind}:{found_id}")


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

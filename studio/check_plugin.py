"""A tool to check consistency of an intellij plugin."""
from pathlib import Path
import argparse
import sys
from tools.adt.idea.studio import intellij

def check_plugin(kind, id, files, deps, out):
  if kind == "module":
    # This is a v2 module, not a plugin per se. See go/studio-v2-modules for details.
    element = intellij.load_plugin_xml(files, f"{id}.xml")
  else:
    assert kind == "plugin"
    element = intellij.load_plugin_xml(files)
    found_ids = [id.text for id in element.findall("id")]
    if not found_ids:
      # If id is not found, IJ uses name
      # https://jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_configuration_file.html
      found_ids = [id.text for id in element.findall("name")]
    if len(set(found_ids)) != 1:
      print("Expected exactly one id, but found [%s]" % ",".join(found_ids))
      sys.exit(1)
    found_id = found_ids[0]
    if found_id != id:
      print("Expected plugin id to be %s, but found %s" % (id, found_id))
      sys.exit(1)

  if element.tag != 'idea-plugin':
    print("Expected plugin.xml root item to be 'idea-plugin' but was %s" % element.tag)
    sys.exit(1)

  # Disallow updates for bundled plugins. We enforce this even for JetBrains plugins, because
  # we want to guarantee compatibility between plugins, and because we want platform plugins
  # to always come from our own IntelliJ fork (which may have patches, for example).
  if element.attrib.get("allow-bundled-update", "false") != "false" and id != "org.jetbrains.kotlin":
      print("Bundled plugin update are not allowed for plugin: %s" % id)
      sys.exit(1)

  if deps is not None:
    # Check for duplicate <dependencies> elements, because duplicates get
    # silently overwritten at runtime at XmlReader.readDependencies().
    if len(element.findall("dependencies")) > 1:
      sys.exit(f"ERROR: found multiple <dependencies> elements in plugin.xml for plugin '{id}'")

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
          f"ERROR: {kind} '{id}' depends on {dep_kind} '{dep_id}' in the build, "
          "but this dependency is not declared in the plugin.xml file."
        )
      for dep_kind, dep_id in depends_xml - depends_build:
        print(
          f"ERROR: {kind} '{id}' depends on {dep_kind} '{dep_id}' in the plugin.xml file, "
          "but this dependency is not declared in the build."
        )
      sys.exit(1)

  with open(out, "w") as info:
    info.write(f"{kind}:{id}")


if __name__ == "__main__":
  parser = argparse.ArgumentParser()
  parser.add_argument(
      "--kind",
      choices=["plugin", "module"],
      required=True,
      help="Whether this is a top-level plugin, or a plugin module inside a larger host plugin")
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
      "--id",
      required=True,
      help="The expected id of this plugin or plugin module")
  parser.add_argument(
      "--out",
      dest="out",
      help="Path to a file where to save the plugin information.")
  args = parser.parse_args()
  check_plugin(args.kind, args.id, args.files, args.deps, args.out)

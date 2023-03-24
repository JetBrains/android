"""A tool to check consistency of an intellij plugin."""
import argparse
import re
import sys
import xml.etree.ElementTree as ET
import zipfile


def load_include(include, external_xmls, cwd, index):
  href = include.get("href")
  parse = include.get("parse", "xml")
  if parse != "xml":
    print("only xml parse is supported")
    sys.exit(1)
  xpointer = include.get("xpointer")
  xpath = None
  if xpointer:
    m = re.match(r"xpointer\((.*)\)", xpointer)
    if not m:
      print("only xpointers of the form xpointer(xpath) are supported")
      sys.exit(1)
    xpath = m.group(1)
  is_optional = any(child.tag == "{http://www.w3.org/2001/XInclude}fallback" for child in include)

  rel = href[1:] if href.startswith("/") else cwd + "/" + href

  if rel in external_xmls or is_optional:
    return [], None
  new_cwd = rel[0:rel.rindex("/")]

  if rel not in index:
    print("Cannot find file to include %s" % href)
    sys.exit(1)

  with zipfile.ZipFile(index[rel]) as jar:
    res = jar.read(rel)

  e = ET.fromstring(res)
  if not xpath:
    return [e], new_cwd

  if not xpath.startswith("/"):
    print("Unexpected xpath %s. Only absolute paths are supported" % xpath)
    sys.exit(1)

  ret = []
  root, path = xpath[1:].split("/", 1)
  if root == e.tag:
    ret = e.findall("./" + path)
  if not ret:
    print("While including %s, the path %s," % (rel, xpath))
    print("did not produce any elements to include")
    sys.exit(1)

  return ret, new_cwd


def resolve_includes(elem, external_xmls, cwd, index):
  """ Resolves xincludes in the given xml element. By replacing xinclude tags like

  <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
    <xi:include href="/META-INF/android-plugin.xml" xpointer="xpointer(/idea-plugin/*)"/>
    ...

  with the xml pointed by href and the xpath given in xpointer.
  """

  i = 0
  while i < len(elem):
    e = elem[i]
    if e.tag == "{http://www.w3.org/2001/XInclude}include":
      nodes, new_cwd = load_include(e, external_xmls, cwd, index)
      subtree = ET.Element("nodes")
      subtree.extend(nodes)
      resolve_includes(subtree, external_xmls, new_cwd, index)
      nodes = list(subtree)
      if nodes:
        for node in nodes[:-1]:
          elem.insert(i, node)
          i = i + 1
        node = nodes[len(nodes)-1]
        if e.tail:
          node.tail = (node.tail or "") + e.tail
        elem[i] = node
    else:
      resolve_includes(e, external_xmls, cwd, index)
    i = i + 1


def check_plugin(plugin_id, files, deps, external_xmls, out):
  xmls = {}
  index = {}
  for file in files:
    if file.endswith(".jar"):
      with zipfile.ZipFile(file) as jar:
        for jar_entry in jar.namelist():
          if jar_entry == "META-INF/plugin.xml":
            xmls[file + "!" + jar_entry] = jar.read(jar_entry)
          if not jar_entry.endswith("/"):
            # TODO: Investigate if we can have a strict mode where we fail on duplicate
            # files across jars in the same plugin. Currently even IJ plugins fail with
            # such a check as they have even .class files duplicated in the same plugin.
            index[jar_entry] = file

  if len(xmls) != 1:
    msg = "\n".join(xmls.keys())
    print("Plugin should have exactly one plugin.xml file (found %d)" % len(xmls))
    print(msg)
    sys.exit(1)

  _, xml = list(xmls.items())[0]
  element = ET.fromstring(xml)
  ids = [id.text for id in element.findall("id")]

  if not ids:
    # If id is not found, IJ uses name
    # https://jetbrains.org/intellij/sdk/docs/basics/plugin_structure/plugin_configuration_file.html
    ids = [id.text for id in element.findall("name")]

  if len(ids) != 1:
    print("Expected exactly one id, but found [%s]" % ",".join(ids))
    sys.exit(1)
  found_id = ids[0]
  # We cannot use ElementInclude because it does not support xpointer
  resolve_includes(element, external_xmls, "META-INF", index)
  if plugin_id and found_id != plugin_id:
    print("Expected plugin id to be %d, but found %s" % (plugin_id, found_id))
    sys.exit(1)

  if element.tag != 'idea-plugin':
    print("Expected plugin.xml root item to be 'idea-plugin' but was %s" % element.tag)
    sys.exit(1)

  if element.attrib.get("allow-bundled-update", "false") != "false" and found_id != "org.jetbrains.kotlin" and found_id != "org.rust.lang":
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

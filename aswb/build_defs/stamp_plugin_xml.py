#!/usr/bin/python3
#
# Copyright 2019 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Stamps a plugin xml with build information."""

import argparse
import io
import re
import sys
from xml.dom import minidom

parser = argparse.ArgumentParser()

parser.add_argument(
    "--plugin_xml",
    help="The plugin xml file",
)
parser.add_argument(
    "--api_version_txt",
    help="The file containing the api version info",
    required=True,
)
parser.add_argument(
    "--stamp_since_build",
    action="store_true",
    help="Stamp since-build with the build number",
)
parser.add_argument(
    "--stamp_until_build",
    action="store_true",
    help=("Stamp until-build with the major release component of the build "
          "number"),
)
parser.add_argument(
    "--plugin_id",
    help="plugin ID to stamp into the plugin.xml",
)
parser.add_argument(
    "--plugin_name",
    help="plugin name to stamp into the plugin.xml",
)
parser.add_argument(
    "--version",
    help="Version to stamp into the plugin.xml",
)
parser.add_argument(
    "--version_file",
    help="Version file to stamp into the plugin.xml",
)
parser.add_argument(
    "--changelog_file",
    help="Changelog file to add to plugin.xml",
)
parser.add_argument(
    "--description_file",
    help="File with description element data to add to plugin.xml",
)
parser.add_argument(
    "--vendor_file",
    help="File with vendor element data to add to plugin.xml",
)


def _read_changelog(changelog_file):
  """Reads the changelog and transforms it into trivial HTML if it's not HTML."""
  with io.open(changelog_file, encoding="utf-8") as f:
    if changelog_file.endswith("html"):
      return f.read()
    else:
      return "\n".join("<p>" + line + "</p>" for line in f.readlines())


def _read_description(description_file):
  """Reads the description and transforms it into trivial HTML."""
  with open(description_file) as f:
    return f.read()


def _read_vendor(vendor_file):
  """Reads vendor data from an .xml file and returns the vendor element."""
  dom = minidom.parse(vendor_file)
  vendor_elements = dom.getElementsByTagName("vendor")
  if len(vendor_elements) != 1:
    raise ValueError("Ambigious or missing vendor element (%d elements)" %
                     len(vendor_elements))
  return vendor_elements[0]

def main():
  args = parser.parse_args()

  if args.plugin_xml:
    dom = minidom.parse(args.plugin_xml)
  else:
    dom = minidom.parseString("<idea-plugin/>")

  with open(args.api_version_txt) as f:
    # api_version formatted as AI-xxx.yyy.zzz.www.__BUILD_NUMBER__
    # for example: AI-242.23339.11.2422.__BUILD_NUMBER__
    api_version = f.readline().strip()
    if api_version.endswith(" EAP"):
      # remove the EAP suffix if present
      api_version = api_version[:-4]
    # remove AI- prefix
    api_version = api_version[3:]
  new_elements = []

  idea_plugin = dom.documentElement

  if args.version and args.version_file:
    raise ValueError("Cannot supply both version and version_file")

  if args.version or args.version_file:
    version_elements = idea_plugin.getElementsByTagName("version")
    for element in version_elements:
      idea_plugin.removeChild(element)
    version_element = dom.createElement("version")
    new_elements.append(version_element)

    build_number = None
    if args.version:
      build_number = args.version
    else:
      with open(args.version_file) as f:
        build_number = f.read().strip()

    # Since we are releasing a single version of the plugin from a given build
    # then using the build number is enough.
    # for example: the plugin version will be 12480590
    # if built for Android Studio AI-242.23339.11.2422.12480590
    version_text = dom.createTextNode(build_number)
    version_element.appendChild(version_text)

  if args.stamp_since_build or args.stamp_until_build:
    if idea_plugin.getElementsByTagName("idea-version"):
      raise ValueError("idea-version element already present")

    idea_version_element = dom.createElement("idea-version")
    new_elements.append(idea_version_element)

    # use only the first 3 components from api_version in
    # compatibility range because of b/298233757
    idea_version = ".".join(api_version.split(".")[:3])

    if args.stamp_since_build:
      idea_version_element.setAttribute("since-build", idea_version)

    if args.stamp_until_build:
      idea_version_element.setAttribute("until-build", idea_version)

  # TODO(b/349186243): add changelog to plugin.xml

  if args.plugin_id:
    if idea_plugin.getElementsByTagName("id"):
      raise ValueError("id element already in plugin.xml")
    id_element = dom.createElement("id")
    new_elements.append(id_element)
    id_text = dom.createTextNode(args.plugin_id)
    id_element.appendChild(id_text)

  if args.plugin_name:
    if idea_plugin.getElementsByTagName("name"):
      raise ValueError("name element already in plugin.xml")
    name_element = dom.createElement("name")
    new_elements.append(name_element)
    name_text = dom.createTextNode(args.plugin_name)
    name_element.appendChild(name_text)

  if args.description_file:
    if idea_plugin.getElementsByTagName("description"):
      raise ValueError("description element already in plugin.xml")
    description_element = dom.createElement("description")
    description_text = _read_description(args.description_file)
    description_cdata = dom.createCDATASection(description_text)
    description_element.appendChild(description_cdata)
    new_elements.append(description_element)

  if args.vendor_file:
    if idea_plugin.getElementsByTagName("vendor"):
      raise ValueError("vendor element already in plugin.xml")
    vendor_element = dom.createElement("vendor")
    vendor_src_element = _read_vendor(args.vendor_file)
    vendor_element.setAttribute("email",
                                vendor_src_element.getAttribute("email"))
    vendor_element.setAttribute("url", vendor_src_element.getAttribute("url"))
    vendor_text = dom.createTextNode(vendor_src_element.firstChild.data)
    vendor_element.appendChild(vendor_text)
    new_elements.append(vendor_element)

  for new_element in new_elements:
    idea_plugin.appendChild(new_element)

  sys.stdout.buffer.write(dom.toxml(encoding="utf-8"))


if __name__ == "__main__":
  main()

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
parser.add_argument(
    "--since_build_numbers",
    metavar="KEY=VALUE",
    nargs="+",
    help=("List of key-value pairs to map plugin api versions to the since "
          "build number to be used for it in plugin.xml"),
)
parser.add_argument(
    "--until_build_numbers",
    metavar="KEY=VALUE",
    nargs="+",
    help=("List of key-value pairs to map plugin api versions to the until "
          "build number to be used for it in plugin.xml"),
)


def parse_key_value_items(items):
  """Parse key-value parameters and returns them in a dict."""
  res = {}
  for item in items:
    item_pair = item.split("=", 1)
    key = item_pair[0].strip()
    res[key] = item_pair[1]
  return res


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


def _strip_product_code(api_version):
  """Strips the product code from the api version string."""
  match = re.match(r"^([A-Z]+-)?([0-9]+)((\.[0-9]+)*)", api_version)
  if match is None:
    raise ValueError("Invalid build number: " + api_version)

  return match.group(2) + match.group(3)


def _parse_major_version(api_version):
  """Extracts the major version number from a full api version string."""
  match = re.match(r"^([A-Z]+-)?([0-9]+)((\.[0-9]+)*)", api_version)
  if match is None:
    raise ValueError("Invalid build number: " + api_version)

  return match.group(2)


def _strip_build_number(api_version):
  """Removes the build number component from a full api version string.

  If there are more than 2 version number components, return the first 2
  components.

  Some IDEs do not report their full build version to JetBrains, so plugins
  built against a version with more components may not be discoverable even in
  a compatible IDE version.

  Args:
    api_version: A version string containing the main version and build number.

  Returns:
    The first two components of the version string.

  Raise:
    ValueError: An incorrectly formatted version string.
  """
  if re.match(r"^([A-Z]+-)?([0-9]+)(\.[0-9]+)+$", api_version):
    return ".".join(api_version.split(".")[:2])
  else:
    raise ValueError("Unsupported API version %s - the version must be of " %
                     api_version +
                     "the form <alphanum>.<num>, with at least two components.")


def main():
  args = parser.parse_args()

  if args.plugin_xml:
    dom = minidom.parse(args.plugin_xml)
  else:
    dom = minidom.parseString("<idea-plugin/>")

  since_build_numbers = {}
  until_build_numbers = {}
  if args.since_build_numbers:
    since_build_numbers = parse_key_value_items(args.since_build_numbers)

  if args.until_build_numbers:
    until_build_numbers = parse_key_value_items(args.until_build_numbers)

  is_eap = False
  with open(args.api_version_txt) as f:
    api_version = f.readline().strip()
    if api_version.endswith(" EAP"):
      is_eap = True
      api_version = api_version[:-4]
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

    version_value = None
    if args.version:
      version_value = args.version
    else:
      with open(args.version_file) as f:
        version_value = f.read().strip()
    # Since we may release different versions that target different plugin api
    # versions simultaneously, we append the name of the api_version to the
    # plugin version.
    version_text = dom.createTextNode(version_value + "-api-version-" +
                                      _parse_major_version(api_version))
    version_element.appendChild(version_text)

  if args.stamp_since_build or args.stamp_until_build:
    if idea_plugin.getElementsByTagName("idea-version"):
      raise ValueError("idea-version element already present")

    idea_version_element = dom.createElement("idea-version")
    new_elements.append(idea_version_element)

    if is_eap:
      # IU211.6693.111 >> since_build=211.6693 and until_build=211.6693.*
      build_version = _strip_build_number(
          _strip_product_code(api_version))
    else:
      # IU211.6693.111 >> since_build=211 and until_build=211.*
      build_version = _parse_major_version(api_version)

    if args.stamp_since_build:
      if build_version in since_build_numbers.keys():
        idea_version_element.setAttribute("since-build",
                                          since_build_numbers[build_version])
      else:
        idea_version_element.setAttribute("since-build", build_version)

    if args.stamp_until_build:
      if build_version in until_build_numbers.keys():
        idea_version_element.setAttribute("until-build",
                                          until_build_numbers[build_version])
      else:
        idea_version_element.setAttribute("until-build", build_version + ".*")

  if args.changelog_file:
    if idea_plugin.getElementsByTagName("change-notes"):
      raise ValueError("change-notes element already in plugin.xml")
    changelog_element = dom.createElement("change-notes")
    changelog_text = _read_changelog(args.changelog_file)
    changelog_cdata = dom.createCDATASection(changelog_text)
    changelog_element.appendChild(changelog_cdata)
    new_elements.append(changelog_element)

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

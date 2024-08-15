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

"""Appends XML elements specifying optional dependencies to a plugin XML file.
"""

import argparse
import sys
from xml.dom.minidom import parse  # pylint: disable=g-importing-member

parser = argparse.ArgumentParser()

parser.add_argument(
    "--plugin_xml", help="The main plugin xml file", required=True)
parser.add_argument("--output", help="The output file.")
parser.add_argument(
    "optional_xml_files",
    nargs="+",
    help="Sequence of module, module xml... pairs")


def pairwise(t):
  it = iter(t)
  return zip(it, it)


def main():

  args = parser.parse_args()
  dom = parse(args.plugin_xml)

  plugin_xml = dom.documentElement

  for module, optional_xml in pairwise(args.optional_xml_files):
    depends_element = dom.createElement("depends")
    depends_element.setAttribute("optional", "true")
    depends_element.setAttribute("config-file", optional_xml)
    depends_element.appendChild(dom.createTextNode(module))
    plugin_xml.appendChild(depends_element)
    plugin_xml.appendChild(dom.createTextNode("\n"))

  if args.output:
    with open(args.output, "wb") as f:
      f.write(dom.toxml(encoding="utf-8"))
  else:
    sys.stdout.buffer.write(dom.toxml(encoding="utf-8"))

if __name__ == "__main__":
  main()

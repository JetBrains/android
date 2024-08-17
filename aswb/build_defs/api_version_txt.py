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
"""Produces a api_version.txt file with the plugin API version."""

import argparse
import json
import re
from xml.dom.minidom import parseString  # pylint: disable=g-importing-member
import zipfile

parser = argparse.ArgumentParser()

parser.add_argument(
    "--application_info_json",
    help="A json file containing the product information details.",
    required=True,)
parser.add_argument(
    "--check_eap",
    help="If true, the build number will be checked and marked with `EAP` if it is an EAP version.",
    default=False,
    action="store_true")


def _is_valid_build_number(build_number):
  """Validates the build number.

  Args:
    build_number: The build number as text.
  Returns:
    true if the build number is valid and throws ValueError otherwise.
  Raises:
    ValueError: if the build number is invalid.
  """
  match = re.match(r"^([A-Z]+-)?([0-9]+)((\.[0-9]+)*)", build_number)
  if match is None:
    raise ValueError("Invalid build number: " + build_number)

  return True


def _parse_app_info_json(application_info_json, check_eap):
  """Extracts the build number from application_info_json.

  Args:
    application_info_json: The name of the json file to extract the build number
                           from.
    check_eap: If the returned build number should be checked and marked as EAP
               if it is or not.
  Raises:
    ValueError: if the build number is invalid or it cannot be extracted.
  """

  with open(application_info_json) as jf:
    data = json.loads(jf.read())

  build_number = data["productCode"] + "-" + data["buildNumber"]
  if _is_valid_build_number(build_number):
    if check_eap:
      if "versionSuffix" in data and (data["versionSuffix"] == "EAP" or
                                      data["versionSuffix"] == "Beta"):
        print(build_number + " EAP")
        return
    print(build_number)


def _parse_app_info_jar(application_info_jar, application_info_file):
  """Extracts the build number from application_info_file which is inside application_info_jar.

  Args:
    application_info_jar: The name of the jar file.
    application_info_file: The name of the application info file.

  Raises:
    ValueError: if the build number is invalid or it cannot be extracted.
  """

  with open(application_info_file) as f:
    application_info_file = f.read().strip()

  with zipfile.ZipFile(application_info_jar, "r") as zf:
    try:
      data = zf.read(application_info_file)
    except:
      raise ValueError("Could not read application info file: " +
                       application_info_file)
    component = parseString(data)

    build_elements = component.getElementsByTagName("build")
    if not build_elements:
      raise ValueError("Could not find <build> element.")
    if len(build_elements) > 1:
      raise ValueError("Ambiguous <build> element.")
    build_element = build_elements[0]

    attrs = build_element.attributes
    try:
      api_version_attr = attrs["apiVersion"]
    except KeyError:
      api_version_attr = attrs["number"]

  if not api_version_attr:
    raise ValueError("Could not find api version in application info")

  if _is_valid_build_number(api_version_attr.value):
    print(api_version_attr.value)  # pylint: disable=superfluous-parens


def main():

  args = parser.parse_args()

  application_info_json = args.application_info_json
  if application_info_json.endswith(".json"):
    _parse_app_info_json(application_info_json, args.check_eap)
  else:
    raise ValueError(
        "Invalid application_info_json: {}, only accepts .json files"
        .format(application_info_json))

if __name__ == "__main__":
  main()

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

"""Adds a list of files into the META-INF directory of the passed deploy jar.
"""

import argparse
import shutil
import zipfile

# Set to Jan 1 1980, the earliest date supported by zipfile
ZIP_DATE = (1980, 1, 1, 0, 0, 0)

parser = argparse.ArgumentParser()

parser.add_argument(
    "--deploy_jar",
    required=True,
    help="The deploy jar to modify.",)
parser.add_argument(
    "--output",
    required=True,
    help="The output file.",)
parser.add_argument(
    "meta_inf_files",
    nargs="+",
    help="Sequence of input file, final file name pairs",)


def pairwise(t):
  it = iter(t)
  return zip(it, it)


def main():
  args = parser.parse_args()

  shutil.copyfile(args.deploy_jar, args.output)
  output_jar = zipfile.ZipFile(args.output, "a")
  for meta_inf_file, name in pairwise(args.meta_inf_files):
    with open(meta_inf_file, "rb") as f:
      zip_info = zipfile.ZipInfo("META-INF/" + name, ZIP_DATE)
      output_jar.writestr(zip_info, f.read())

if __name__ == "__main__":
  main()

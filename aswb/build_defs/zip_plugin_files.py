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

"""Packages plugin files into a zip archive."""

import argparse
import os
import stat
import zipfile

parser = argparse.ArgumentParser()

parser.add_argument("--output", help="The output filename.", required=True)
parser.add_argument(
    "files_to_zip", nargs="+", help="Sequence of exec_path, zip_path... pairs")


def pairwise(t):
  it = iter(t)
  return zip(it, it, it)


def main():
  args = parser.parse_args()
  with zipfile.ZipFile(args.output, "w") as outfile:
    for exec_path, zip_path, executable in pairwise(args.files_to_zip):
      with open(exec_path, mode="rb") as input_file:
        zipinfo = zipfile.ZipInfo(zip_path, (2000, 1, 1, 0, 0, 0))
        filemode = (
            0o755
            if executable == "True"
            else stat.S_IMODE(os.fstat(input_file.fileno()).st_mode)
        )
        zipinfo.external_attr = filemode << 16
        outfile.writestr(zipinfo, input_file.read(), zipfile.ZIP_DEFLATED)

if __name__ == "__main__":
  main()

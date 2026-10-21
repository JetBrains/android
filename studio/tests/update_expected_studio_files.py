#  Copyright (C) 2026 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import os
import sys
import zipfile
import argparse
import shutil

PLATFORMS = ["linux", "win", "mac", "mac_arm"]

def main(ide, configurations):
  ws_dir = os.environ.get("BUILD_WORKSPACE_DIRECTORY")
  if not ws_dir:
    print("ERROR: This script must be run via Bazel.", file=sys.stderr)
    sys.exit(1)

  ide_path_prefix = "/".join(ide.split("/")[:-1])
  ide_name = ide.split("/")[-1]

  try:
    shutil.rmtree(os.path.join(ws_dir, ide_path_prefix, "tests/expected_studio_files/%s" % ide_name))
  except FileNotFoundError:
    pass

  configurations.sort()
  base_configuration = configurations[0]

  # Update base files
  base_lines = {}
  for platform in PLATFORMS:
    name = f"{ide}.{base_configuration}.{platform}.lst"
    try:
      with open(name, "r") as txt:
        base_lines[platform] = sorted(txt.read().splitlines())
    except Exception as e:
      print(f"ERROR reading manifest {name}: {e}", file=sys.stderr)
      sys.exit(1)

    target_path = os.path.join(ws_dir, ide_path_prefix, f"tests/expected_studio_files/{ide_name}", f"expected_{platform}.txt")
    os.makedirs(os.path.join(ws_dir, ide_path_prefix, f"tests/expected_studio_files/{ide_name}"), exist_ok=True)
    try:
      with open(target_path, "w") as target_file:
        target_file.writelines([line + "\n" for line in base_lines[platform]])
        print(f"Updated file: {target_path}", file=sys.stderr)
    except Exception as e:
      print(f"ERROR writing to {target_path}: {e}", file=sys.stderr)
      sys.exit(1)

  # Generate diffs for all configurations
  for configuration in configurations:
    for platform in PLATFORMS:
      name = f"{ide}.{configuration}.{platform}.lst"
      try:
        with open(name, "r") as txt:
          actual_lines = sorted(txt.read().splitlines())
      except Exception as e:
        print(f"ERROR reading manifest {name}: {e}", file=sys.stderr)
        sys.exit(1)

      base_set = set(base_lines[platform])
      actual_set = set(actual_lines)
      added_lines = actual_set - base_set
      removed_lines = base_set - actual_set

      if not added_lines and not removed_lines:
        continue

      diff_dir = os.path.join(ws_dir, ide_path_prefix, f"tests/expected_studio_files/{ide_name}/{configuration}")
      os.makedirs(diff_dir, exist_ok=True)
      target_path = os.path.join(diff_dir, f"expected_diff_{platform}.txt")

      try:
        with open(target_path, "w") as target_file:
          for line in sorted(list(added_lines)):
            target_file.write(f"+++{line}\n")
          for line in sorted(list(removed_lines)):
            target_file.write(f"---{line}\n")
        print(f"Updated file: {target_path}", file=sys.stderr)
      except Exception as e:
        print(f"ERROR writing to {target_path}: {e}", file=sys.stderr)
        sys.exit(1)

  print("\nDone!", file=sys.stderr)

if __name__ == "__main__":
  parser = argparse.ArgumentParser()
  parser.add_argument(
    "--ide",
    dest="ide",
    required=True,
    help="The path (prefix) to the ide artifacts")
  parser.add_argument(
    "--ide-configuration",
    dest="configurations",
    nargs="*",
    required=True,
    help="The ide artifacts configurations")

  args = parser.parse_args()
  main(args.ide, args.configurations)

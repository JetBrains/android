"""A utility to update the searchable options files."""
import argparse
import json
import glob
import os
import platform
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import zipfile

def extract_file(zip_file, info, extract_dir):
    """ Extracts a file preserving file attributes. """
    out_path = zip_file.extract(info.filename, path=extract_dir)
    attr = info.external_attr >> 16
    if attr:
      os.chmod(out_path, attr)


def generate_searchable_options(work_dir, out_dir):
  """Generates the xmls in out_dir, using work_dir as a scratch pad."""

  suffix = {
    "Windows": "win",
    "Linux": "linux",
    "Darwin": "mac_arm" if platform.machine() == "arm64" else "mac" ,
  }
  zip_path = os.path.join("tools/adt/idea/studio/android-studio.%s.zip" % suffix[platform.system()])
  with zipfile.ZipFile(zip_path) as zip_file:
    for info in zip_file.infolist():
      extract_file(zip_file, info, work_dir)

  vmoptions_file = "%s/studio.vmoptions" % work_dir
  with open(vmoptions_file, "w") as props:
    props.writelines([
        "-Didea.config.path=%s/config\n" % work_dir,
        "-Didea.system.path=%s/system\n" % work_dir,
        "-Duser.home=%s/home\n" % work_dir])

  env = {
      "STUDIO_VM_OPTIONS": vmoptions_file,
      "XDG_DATA_HOME": "%s/data" % work_dir,
      "SHELL": os.getenv("SHELL", "")
  }
  options_dir = os.path.join(work_dir, "options")

  studio_bin = {
    "Windows": "/android-studio/bin/studio.cmd",
    "Linux": "/android-studio/bin/studio.sh",
    "Darwin": "/Android Studio*.app/Contents/MacOS/studio",
  }
  [bin_path] = glob.glob(work_dir + studio_bin[platform.system()])
  subprocess.call([bin_path, "traverseUI", options_dir, "true"], env=env)
  with open("tools/adt/idea/studio/android-studio.plugin.lst", "r") as list_file:
    plugin_list = {}
    for line in list_file.read().splitlines():
      parts = [p.strip() for p in line.split(":", 1)]
      plugin_list[parts[0]] = parts[1] if len(parts) > 1 else None

  with open(os.path.join(options_dir, "content.json"), "r") as content_file:
    content = json.loads(content_file.read())

  os.makedirs(out_dir, exist_ok=True)
  bzl = {}
  for plugin_dir, id in plugin_list.items():
    if id and id in content:
      for entry in content[id]:
        name = entry["file"]
        shutil.move(os.path.join(options_dir, name), out_dir)
        bzl[name] = id

  with open(os.path.join(out_dir, "content.bzl"), "w") as f:
    f.write("# This file is generated automatically. Do not modify.\n")
    f.write("SEARCHABLE_OPTIONS = {\n")
    for k,v in bzl.items():
      f.write("    \"%s\": \"%s\",\n" % (k,v))
    f.write("}\n")

  return plugin_list


def update_searchable_options(work_dir, workspace_dir):
  so_dir = os.path.join(workspace_dir, "tools/adt/idea/searchable-options")
  files = glob.glob(os.path.join(so_dir, "*.json"))
  for file in files:
    os.remove(file)
  os.remove(os.path.join(so_dir, "content.bzl"))

  generate_searchable_options(work_dir, so_dir)


def find_workspace():
  curr_dir = os.getcwd()
  while os.path.dirname(curr_dir) != curr_dir:
    path = os.path.join(curr_dir, "DO_NOT_BUILD_HERE")
    if os.path.exists(path):
      with open(path, "r") as file:
        return file.read()
    curr_dir = os.path.abspath(os.path.join(curr_dir, os.pardir))

  sys.exit("Can't find the DO_NOT_BUILD_HERE any of the parent directories. Was binary invoked through 'bazel run'? if not use --workspace")

if __name__ == "__main__":
  parser = argparse.ArgumentParser()
  parser.add_argument(
      "--workspace",
      default="",
      dest="workspace",
      help="The workspace directory, if empty the tool will look for it assuming 'bazel run'")
  tmp_dir = tempfile.mkdtemp()
  args = parser.parse_args()
  workspace = args.workspace if args.workspace else find_workspace()
  update_searchable_options(tmp_dir, workspace)

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


def generate_searchable_options(work_dir, out_dir, ide_path, plugins):
  """Generates the xmls in out_dir, using work_dir as a scratch pad."""

  suffix = {
    "Windows": "win",
    "Linux": "linux",
    "Darwin": "mac_arm" if platform.machine() == "arm64" else "mac" ,
  }
  zip_path = os.path.join("%s.%s.zip" % (ide_path, suffix[platform.system()]))
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
    "Linux": "/android-studio/bin/studio",
    "Darwin": "/Android Studio*.app/Contents/MacOS/studio",
  }
  [bin_path] = glob.glob(work_dir + studio_bin[platform.system()])
  subprocess.call([bin_path, "traverseUI", options_dir, "true"], env=env)
  with open("%s.plugin.lst" % ide_path, "r") as list_file:
    plugin_list = {}
    for line in list_file.read().splitlines():
      parts = [p.strip() for p in line.split(":", 1)]
      plugin_list[parts[0]] = parts[1] if len(parts) > 1 else None

  with open(os.path.join(options_dir, "content.json"), "r") as content_file:
    content = json.loads(content_file.read())

  os.makedirs(out_dir, exist_ok=True)
  for plugin_dir, id in plugin_list.items():
    if id not in plugins:
      continue
    if id and id in content:
      for entry in content[id]:
        name = entry["file"]
        src_file = os.path.join(options_dir, name)
        dst_file = os.path.join(out_dir, name)
        with open(src_file, "r", encoding="utf-8") as sf:
          lines = sf.readlines()
        lines.sort()
        with open(dst_file, "w", encoding="utf-8") as df:
          # IntelliJ writes a full JSON object per line, often resulting in very long lines.
          # We reformat the JSON here to be more readable and VCS friendly.
          for line in lines:
            json.dump(json.loads(line), df, indent=2)
            df.write("\n")


  return plugin_list


def update_searchable_options(work_dir, workspace_dir, out, ide_path, configurations, plugins):
  content_file = os.path.join(os.path.join(workspace_dir, out), "content.bzl")
  if os.path.exists(content_file):
    os.remove(content_file)
  else:
    os.makedirs(os.path.join(workspace_dir, out), exist_ok=True)

  with open(os.path.join(os.path.join(workspace_dir,out),"content.bzl"), "w") as f:
    package_name = "/".join(out.split("/")[:-2])
    sub_dir = "/".join(out.split("/")[-2:])
    f.write("# This file is generated automatically. Do not modify.\n")
    f.write("SEARCHABLE_OPTIONS = {\n")
    for configuration in configurations:
      so_dir = os.path.join(os.path.join(workspace_dir, out), "%s-searchable-options" % configuration)
      config_work_dir = os.path.join(work_dir, configuration)
      ide_path_prefix = "%s.%s" % (ide_path,configuration)

      files = glob.glob(os.path.join(so_dir, "*.json"))
      for file in files:
        os.remove(file)

      plugin_list = generate_searchable_options(config_work_dir, so_dir, ide_path_prefix, plugins)

      with open(os.path.join(os.path.join(config_work_dir,"options"), "content.json"), "r") as content_file:
        content = json.loads(content_file.read())
      plugin_list = dict(sorted(plugin_list.items(), key=lambda item: item[1]))
      f.write("    \"%s\": {\n" % configuration)
      for plugin_dir, id in plugin_list.items():
        if id not in plugins:
          continue
        if id and id in content:
          for entry in content[id]:
            name = entry["file"]
            f.write("        \"//%s:%s/%s-searchable-options/%s\": \"%s\",\n" % (package_name,sub_dir,configuration,name,id))
      f.write("    },\n")

    f.write("}\n")


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
  parser.add_argument(
    "--out",
    dest="out",
    required=True,
    help="The output directory")
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
  parser.add_argument(
    "--plugins",
    dest="plugins",
    nargs="*",
    default=[],
    help="The plugins to export, if none chosen all plugins are exported")

  tmp_dir = tempfile.mkdtemp()
  try:
    args = parser.parse_args()
    workspace = args.workspace if args.workspace else find_workspace()
    if args.plugins:
      update_searchable_options(tmp_dir, workspace, args.out, args.ide, args.configurations, args.plugins)
  finally:
    shutil.rmtree(tmp_dir)

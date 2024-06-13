#!/usr/bin/env python3

import argparse
import os
import platform
import re
import subprocess
import sys
import tempfile


def main(argv):
  script_name = os.path.basename(__file__)
  script_dir = os.path.dirname(__file__)
  name = script_name[:-3] if script_name.endswith(".py") else script_name

  parser = argparse.ArgumentParser(
      formatter_class=argparse.ArgumentDefaultsHelpFormatter
  )
  parser.add_argument(
      "--debug",
      dest="debug",
      nargs="?",
      const="127.0.0.1:5005",
      help=(
          "Suspend the IDE at startup waiting for the java debugger on the"
          " given ip address:port."
      ),
  )
  parser.add_argument(
      "--config_base_dir",
      dest="config_base_dir",
      default="~/.studio_launcher/." + name,
      help="The directory to use to store logs, configs, and system files.",
  )
  parser.add_argument(
      "--properties",
      nargs="*",
      default=[],
      action="extend",
      dest="properties",
      help="Additional system properties to be added to studio.properties.",
  )
  parser.add_argument(
      "--vmoptions",
      nargs="*",
      default=[],
      action="extend",
      dest="vmoptions",
      help="Additional vmoptions to be added to studio.vmoptions.",
  )

  args, unknown_args = parser.parse_known_args(argv)

  with open(os.path.join(script_dir, "files.lst"), "r") as f:
    runfiles = [l.strip() for l in f]

  config_base_dir = os.path.expanduser(args.config_base_dir)
  os.makedirs(config_base_dir, exist_ok=True)
  env = os.environ

  vmoptions = []
  if args.debug:
    vmoptions.append(
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=%s"
        % args.debug
    )
  vmoptions.extend(args.vmoptions)
  if vmoptions:
    options = os.path.join(config_base_dir, "studio.vmoptions")
    with open(options, "w") as f:
      for opt in vmoptions:
        f.write(opt + "\n")
    env["STUDIO_VM_OPTIONS"] = options

  system_dirs = {
      "idea.config.path": ".config",
      "idea.plugins.path": ".plugins",
      "idea.system.path": ".system",
      "idea.log.path": ".log",
  }

  properties = []
  for p, v in system_dirs.items():
    d = os.path.join(config_base_dir, v)
    os.makedirs(d, exist_ok=True)
    properties.append("%s=%s" % (p, d))

  properties.extend(args.properties)

  properties_file = os.path.join(config_base_dir, "studio.properties")
  with open(properties_file, "w") as f:
    for l in properties:
      f.write(l + "\n")
  env["STUDIO_PROPERTIES"] = properties_file

  if platform.system() == "Darwin":
    app_dir = os.path.join(script_dir, "Android Studio Preview.app")
    run_command_list = ["open", app_dir]
  else:
    app_dir = os.path.join(script_dir, "android-studio")
    run_command_list = [os.path.join(script_dir, "android-studio/bin/studio.sh")]

  for root, _, files in os.walk(app_dir):
    for file in files:
      absolute_path = os.path.join(root, file)
      relative_path = os.path.relpath(absolute_path, script_dir)
      if relative_path not in runfiles:
        print("Removing obsolete file: " + relative_path)
        os.remove(absolute_path)

  sys.exit(
      subprocess.call(
          run_command_list + unknown_args,
          env=env,
      )
  )


if __name__ == "__main__":
  # Ignore --wrapper_script_flag to make it compatible with existing tooling
  wrapper_flag = "--wrapper_script_flag="
  args = [
      a[len(wrapper_flag) :] if a.startswith(wrapper_flag) else a
      for a in sys.argv[1:]
  ]
  main(args)

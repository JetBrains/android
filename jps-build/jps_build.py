from collections import defaultdict
import argparse
import datetime
import glob
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import zipfile

def files_in_dir(root, base = None):
    walk_dir = os.path.join(root, base) if base else root
    ret = []
    for dir, _, files in os.walk(walk_dir):
        for file in files:
            rel = os.path.relpath(dir, root)
            file_path = os.path.join(rel, file)
            ret.append(file_path)
    return ret

def write_files(workspace, files, dest):
    print("Creating " + dest)
    if dest.endswith(".zip"):
        with zipfile.ZipFile(dest, "w") as zip:
            for rel in files:
                zip.write(os.path.join(workspace, rel), rel)
    else:
        os.makedirs(dest, exist_ok=True)
        for rel in files:
            path = os.path.join(dest, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            shutil.copy2(os.path.join(workspace, rel), path)

def is_in_any(path, dirs):
    return any(path.startswith(d) for d in dirs)

def jps_build(args, environment, cwd):
    if args.reuse_workspace:
        workspace = args.reuse_workspace
    else:
        workspace = tempfile.mkdtemp()

    for source in args.sources:
        print("Setting up source: " + source + " @ " + str(datetime.datetime.now()))
        if source.endswith(".tar"):
            with tarfile.open(source, "r") as tar:
                if not args.reuse_workspace:
                    tar.extractall(workspace)
        elif source.endswith(".zip"):
            with zipfile.ZipFile(source, "r") as zip:
                if not args.reuse_workspace:
                    # TODO: don't use shell, but extract all does not preserve x attribute
                    # zip.extractall(workspace)
                    subprocess.run(["unzip", source, "-d", workspace], stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
        elif source.endswith(".lst"):
            with open(source, "r") as f:
                for rel, path in [l.strip().split("=") for l in f.readlines()]:
                    dest = os.path.join(workspace, rel)
                    os.makedirs(os.path.dirname(dest), exist_ok=True)
                    if args.verbose:
                        print("Copying listed file from %s to: %s %s" %(path, workspace, rel))
                    shutil.copy2(path, dest)
        elif os.path.isdir(source):
            shutil.copytree(source, workspace, dirs_exist_ok=True)
    sources = set(files_in_dir(workspace))

    home = os.path.join(workspace, "home")
    os.makedirs(home, exist_ok=True)

    env = {k : v for k,v in environment.items() }
    env["HOME"] = home
    env["JPS_WORKSPACE"] = workspace
    for k,v in args.envs:
        env[k] = v

    bin_cwd = os.path.join(workspace, args.working_directory)
    bin_path = os.path.join(bin_cwd, args.command)

    print("Running at: " + str(datetime.datetime.now()))
    cmd = [bin_path]
    cmd.extend([s.replace("{jps_bin_cwd}", bin_cwd) for s in args.args])
    if args.verbose:
        print("Running " + " ".join(cmd))
    retcode = subprocess.call(cmd, cwd=bin_cwd, env=env)

    run_workspace = environment.get("BUILD_WORKSPACE_DIRECTORY")
    print("Done running at: " + str(datetime.datetime.now()))
    if retcode == 0:
        all_files = set(files_in_dir(workspace))

        downloaded_files = set()
        output_files = set()

        for new_file in all_files - sources:
            is_output = False
            if is_in_any(new_file, args.output_dirs):
                output_files.add(new_file)
            elif is_in_any(new_file, args.ignore_dirs):
                pass
            else:
                # If it's not a build output or ignored, it is a download
                downloaded_files.add(new_file)

        if args.download_cache and run_workspace:
            write_files(workspace, downloaded_files, os.path.join(run_workspace, args.download_cache))

        if args.out_file:
            write_files(workspace, output_files, args.out_file)

        if args.verbose:
            print("Output Dirs:\n " + "\n".join(args.output_dirs))
            print("Output Files:\n " + "\n".join(output_files))
            print("Downloaded Files:\n " + "\n".join(downloaded_files))

    if run_workspace and args.delete_workspace and not args.reuse_workspace:
        shutil.rmtree(workspace)
    else:
        print("Leaving " + workspace + " behind.")
    print("Done copying at: " + str(datetime.datetime.now()))

    return retcode

def endswith_zip(arg):
    if not arg.endswith(".zip"):
        print("Argument '%s' must end with .zip" % arg)
        sys.exit(1)
    return arg

def main(argv, environment, cwd):
  parser = argparse.ArgumentParser()
  parser.add_argument(
      "--sources",
      dest="sources",
      action="append",
      required=True,
      help="Path to the zip,tar or directory containing workspace files.")
  parser.add_argument(
      "--command",
      dest="command",
      required=True,
      help="Command to execute on the workspace.")
  parser.add_argument(
      "--working_directory",
      dest="working_directory",
      default="",
      help="Working directory in the workspace to run the command.")
  parser.add_argument(
      "--out_file",
      help="The path to a zip file to store the output.",
      type=endswith_zip)
  parser.add_argument(
      "--reuse_workspace",
      dest="reuse_workspace",
      help="The already setup workspace to use, useful to test.")
  parser.add_argument(
      "--download_cache",
      dest="download_cache",
      help="If given, where to save the download cache.")
  parser.add_argument(
      "--delete_workspace",
      action=argparse.BooleanOptionalAction,
      dest="delete_workspace",
      default=True,
      help="Whether to delete the temporary workspace.")
  parser.add_argument(
      "--module",
      dest="module",
      help="The name of the module to build.")
  parser.add_argument(
      "--arg",
      dest="args",
      action="append",
      default=[],
      help="Arguments to pass to the command.")
  parser.add_argument(
      "--output_dir",
      dest="output_dirs",
      action="append",
      default=[],
      help="Directories to be considered as outputs of the running command.")
  parser.add_argument(
      "--ignore_dir",
      dest="ignore_dirs",
      action="append",
      default=[],
      help="Directories that are created that are not download nor output")
  parser.add_argument(
      "--verbose",
      action=argparse.BooleanOptionalAction,
      default=False,
      help="Generate verbose output")
  parser.add_argument(
      "--env",
      nargs=2,
      dest="envs",
      action="append",
      default=[],
      help="Environment variables to pass to the command script.")
  args = parser.parse_args(argv)
  return jps_build(args, environment, cwd)

if __name__ == "__main__":
  sys.exit(main(sys.argv[1:], os.environ, os.getcwd()))

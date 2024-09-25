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

def jps_import(args, environment, cwd):
    temp_dir = tempfile.mkdtemp()
    with zipfile.ZipFile(args.src, 'r') as zip_ref:
        zip_ref.extractall(temp_dir)

    classpath_file = "tools/idea/out/studio/artifacts/module-tests/%s.classpath.txt" % args.module
    with open(os.path.join(temp_dir, classpath_file), 'r') as file:
        classpath = file.readlines()

    entries = set()
    with zipfile.ZipFile(args.dest, 'w') as new_jar:
        for entry in classpath:
            path = os.path.join(temp_dir, entry.strip())
            if not os.path.exists(path):
                continue
            if os.path.isdir(path):
                for root, dirs, files in os.walk(path):
                    for file in files:
                        file_path = os.path.join(root, file)
                        arcname = os.path.relpath(file_path, path)
                        if arcname not in entries:
                            new_jar.write(file_path, arcname) 
                            entries.add(arcname)
            elif path.endswith(".jar"):
                with zipfile.ZipFile(path, 'r') as other_jar:
                    for item in other_jar.infolist():
                        if not item.filename.endswith("/"):
                            if item.filename not in entries:
                                with other_jar.open(item) as file:
                                    new_jar.writestr(item, file.read())
                                entries.add(item.filename)
    return 0

def main(argv, environment, cwd):
  parser = argparse.ArgumentParser()
  parser.add_argument(
      "--src",
      help="The source zip with the raw output.")
  parser.add_argument(
      "--module",
      help="The name of the module to use the classpath from.")
  parser.add_argument(
      "--dest",
      help="The path to the .jar file to create.")
  args = parser.parse_args(argv)
  return jps_import(args, environment, cwd)

if __name__ == "__main__":
  sys.exit(main(sys.argv[1:], os.environ, os.getcwd()))

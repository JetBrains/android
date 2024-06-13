import os
import unittest
import zipfile
import jps_build

def get_path(name):
  return os.path.join(os.getenv("TEST_TMPDIR"), name)

def create_zip(name, contents):
  path = get_path(name)
  tmp = 0
  with zipfile.ZipFile(path, "w") as zip:
    for e, v in contents.items():
      if isinstance(v, str):
        info = zipfile.ZipInfo(e)
        if e.endswith(".sh"):
            info.external_attr = 0o777 << 16
        zip.writestr(info, v)
      else:
        tmp_path = create_zip("%s.%d.zip" % (name, tmp), v)
        zip.write(tmp_path, e)
        os.remove(tmp_path)
  return path

def create_file(name, content):
  path = get_path(name)
  with open(path, "w") as f:
    f.write(content)
  return path

def read_file(path, mode = "r"):
  with open(path, mode) as f:
    return f.read()

def read_entry(zip, entry):
  with zipfile.ZipFile(zip, "r") as z:
    return z.read(entry).decode("utf-8")

def create_idea_sources():
    return create_zip("idea_sources.zip", {
        "tools/idea/platform/jps-bootstrap/jps-bootstrap.sh": FAKE_JPS_BOOTSTRAP_SH,
    })

def write_file(path, content):
    with open(path, "w") as f:
        f.write(content)

def list_files(path):
    if path.endswith(".zip"):
        with zipfile.ZipFile(path, "r") as zip:
            return sorted([n for n in zip.namelist() if not n.endswith("/")])
    else:
        return sorted(jps_build.files_in_dir(path))

FAKE_JPS_BOOTSTRAP_SH = """#!/bin/sh
# Fake download files
mkdir -p $HOME/.down
touch $HOME/.down/down.jar
mkdir -p build/download
touch build/download/down.jar
touch build/download/down.exe
chmod a+x build/download/down.exe
mkdir -p out/jps-bootstrap
touch out/jps-bootstrap/boot.jar
mkdir -p unexpected_download
touch unexpected_download/what.txt
# Only "download if file is not there"
mkdir -p out/studio/artifacts
if [ -f conditional.jar ]; then
  echo "Not Downloaded" > out/studio/artifacts/download.log
else
  touch conditional.jar
  echo "Downloaded" > out/studio/artifacts/download.log
fi

# Fake build and write outputs
mkdir -p out/studio/classes/module
touch out/studio/classes/module/a.class
mkdir -p out/studio/artifacts/debugger-tests
echo "{out}/some/out/dir" > out/studio/artifacts/debugger-tests/classpath.txt
"""

class JpsBuildTest(unittest.TestCase):

  def test_build_output(self):
    idea = create_idea_sources()
    out_classpath = get_path("classpath.txt")
    out_zip = get_path("out.zip")
    jps_build.main([
        "--sources",
        idea,
        "--out_file",
        out_zip,
        "--command",
        "platform/jps-bootstrap/jps-bootstrap.sh",
        "--working_directory",
        "tools/idea",
        "--output_dir",
        "tools/idea/out/studio/",
        "--output_dir",
        "tools/idea/build/jps-bootstrap-work/",
    ], {}, get_path("."))
    self.assertEqual([
        "tools/idea/out/studio/artifacts/debugger-tests/classpath.txt",
        "tools/idea/out/studio/artifacts/download.log",
        "tools/idea/out/studio/classes/module/a.class",
    ], list_files(out_zip))

  def test_custom_command(self):
    idea = create_zip("idea_sources.zip", {
        "dir/bin.sh": FAKE_JPS_BOOTSTRAP_SH,
    })
    out_classpath = get_path("classpath.txt")
    out_zip = get_path("out.zip")
    jps_build.main([
        "--sources",
        idea,
        "--out_file",
        out_zip,
        "--command",
        "bin.sh",
        "--working_directory",
        "dir",
        "--output_dir",
        "dir/out/studio/",
        "--output_dir",
        "dir/build/jps-bootstrap-work/",
    ], {}, get_path("."))
    self.assertEqual([
        "dir/out/studio/artifacts/debugger-tests/classpath.txt",
        "dir/out/studio/artifacts/download.log",
        "dir/out/studio/classes/module/a.class",
    ], list_files(out_zip))

  def test_error(self):
    idea = create_zip("idea_sources.zip", {
        "dir/bin.sh": "#!/bin/sh\nexit 1",
    })
    ret = jps_build.main([
        "--sources",
        idea,
        "--command",
        "bin.sh",
        "--working_directory",
        "dir",
    ], {}, get_path("."))
    self.assertEqual(1, ret)

  def test_download_cache(self):
    idea = create_idea_sources()
    run_workspace = get_path("run_workspace")
    jps_build.main([
        "--sources",
        idea,
        "--download_cache",
        "cache.zip",
        "--command",
        "platform/jps-bootstrap/jps-bootstrap.sh",
        "--working_directory",
        "tools/idea",
        "--output_dir",
        "tools/idea/out/studio/",
        "--output_dir",
        "tools/idea/build/jps-bootstrap-work/",
    ], {"BUILD_WORKSPACE_DIRECTORY": run_workspace}, get_path("."))
    self.assertEqual([
        "home/.down/down.jar",
        "tools/idea/build/download/down.exe",
        "tools/idea/build/download/down.jar",
        "tools/idea/conditional.jar",
        "tools/idea/out/jps-bootstrap/boot.jar",
        "tools/idea/unexpected_download/what.txt",
    ], list_files(os.path.join(run_workspace, "cache.zip")))

  def test_ignore(self):
    idea = create_idea_sources()
    run_workspace = get_path("run_workspace")
    out_zip = get_path("out.zip")
    jps_build.main([
        "--sources",
        idea,
        "--out_file",
        out_zip,
        "--download_cache",
        "cache.zip",
        "--command",
        "platform/jps-bootstrap/jps-bootstrap.sh",
        "--working_directory",
        "tools/idea",
        "--output_dir",
        "tools/idea/out/studio/",
        "--output_dir",
        "tools/idea/build/jps-bootstrap-work/",
        "--ignore_dir",
        "tools/idea/build/download",
        "--ignore_dir",
        "tools/idea/out/jps-bootstrap",
    ], {"BUILD_WORKSPACE_DIRECTORY": run_workspace}, get_path("."))
    self.assertEqual([
        "home/.down/down.jar",
        "tools/idea/conditional.jar",
        "tools/idea/unexpected_download/what.txt",
    ], list_files(os.path.join(run_workspace, "cache.zip")))
    self.assertEqual([
        "tools/idea/out/studio/artifacts/debugger-tests/classpath.txt",
        "tools/idea/out/studio/artifacts/download.log",
        "tools/idea/out/studio/classes/module/a.class",
    ], list_files(out_zip))

  def test_dev_flow(self):
    # Test that it "downloads" into a directory, and then builds from it.
    idea = create_idea_sources()
    run_workspace = get_path("run_workspace")
    os.makedirs(os.path.join(run_workspace, "cache"))
    write_file(os.path.join(run_workspace, "cache/keep.me"), "hi")

    first_out_zip = get_path("first_out.zip")
    jps_build.main([
        "--sources",
        idea,
        "--download_cache",
        "cache",
        "--out_file",
        first_out_zip,
        "--command",
        "platform/jps-bootstrap/jps-bootstrap.sh",
        "--working_directory",
        "tools/idea",
        "--output_dir",
        "tools/idea/out/studio/",
        "--output_dir",
        "tools/idea/build/jps-bootstrap-work/",
    ], {"BUILD_WORKSPACE_DIRECTORY": run_workspace}, get_path("."))
    self.assertEqual([
        "./keep.me",
        "home/.down/down.jar",
        "tools/idea/build/download/down.exe",
        "tools/idea/build/download/down.jar",
        "tools/idea/conditional.jar",
        "tools/idea/out/jps-bootstrap/boot.jar",
        "tools/idea/unexpected_download/what.txt",
    ], list_files(os.path.join(run_workspace, "cache")))
    self.assertTrue(os.access(os.path.join(run_workspace, "cache/tools/idea/build/download/down.exe"), os.X_OK))

    self.assertEqual("Downloaded\n", read_entry(first_out_zip, "tools/idea/out/studio/artifacts/download.log"))

    cache = os.path.join(run_workspace, "cache")
    files = ["%s=%s\n" % (f, os.path.join(cache, f)) for f in list_files(cache)]
    cache_lst = get_path("cache.lst")
    write_file(cache_lst, "".join(files))

    # Add the downloaded cache as a source
    out_zip = get_path("out.zip")
    jps_build.main([
        "--sources",
        idea,
        "--sources",
        cache_lst,
        "--download_cache",
        "cache",
        "--out_file",
        out_zip,
        "--command",
        "platform/jps-bootstrap/jps-bootstrap.sh",
        "--working_directory",
        "tools/idea",
        "--output_dir",
        "tools/idea/out/studio/",
        "--output_dir",
        "tools/idea/build/jps-bootstrap-work/",
    ], {}, run_workspace)
    self.assertEqual([
        "tools/idea/out/studio/artifacts/debugger-tests/classpath.txt",
        "tools/idea/out/studio/artifacts/download.log",
        "tools/idea/out/studio/classes/module/a.class",
    ], list_files(out_zip))

    #not cache, read output
    self.assertEqual("Not Downloaded\n", read_entry(out_zip, "tools/idea/out/studio/artifacts/download.log"))


if __name__ == "__main__":
  unittest.main()
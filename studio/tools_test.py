import json
import os
import io
import unittest
import zipfile
from tools.adt.idea.studio import stamper


def read_file(path, mode = "r"):
  with open(path, mode) as f:
    return f.read()


def get_path(name):
  return os.path.join(os.getenv("TEST_TMPDIR"), name)


def create_file(name, content):
  path = get_path(name)
  with open(path, "w") as f:
    f.write(content)
  return path


def create_zip(name, contents):
  path = get_path(name)
  tmp = 0
  with zipfile.ZipFile(path, "w") as zip:
    for e, v in contents.items():
      if isinstance(v, str):
        zip.writestr(e, v)
      else:
        tmp_path = create_zip("%s.%d.zip" % (name, tmp), v)
        zip.write(tmp_path, e)
        os.remove(tmp_path)
  return path


def read_zip_data(data):
  res = {}
  with zipfile.ZipFile(io.BytesIO(data)) as zip:
    for name in zip.namelist():
      data = zip.read(name)
      if name.endswith(".jar") or name.endswith(".zip"):
        res[name] = read_zip_data(data)
      else:
        res[name] = data.decode("utf-8")
  return res


def read_zip(path):
  return read_zip_data(read_file(path, "rb"))


class ToolsTest(unittest.TestCase):
  """Tests tools used to bundle Android Studio."""

  def test_overwrite_plugin_version(self):
    build_txt = create_file("build.txt", "AI-1234.3333")
    before = create_zip("before.jar", {
          "ANY-DIR/anyfile.xml": "<version>1</version>"
    })
    after = get_path("res.jar")
    stamper.main([
        "--entry", "ANY-DIR/anyfile.xml",
        "--build_txt", build_txt,
        "--stamp", before, after,
        "--overwrite_plugin_version",
    ])

    self.assertEqual({
        "ANY-DIR/anyfile.xml": "<version>1234.3333</version>"
      }, read_zip(after))

  def test_change_since_until(self):
    build_txt = create_file("build.txt", "AI-1234.3333")
    before = create_zip("before.jar", {
          "ANY-DIR/anyfile.xml": "<idea-version since-build=\"1.1\" until-build=\"2.1\">"
    })
    after = get_path("res.jar")
    stamper.main([
        "--entry", "ANY-DIR/anyfile.xml",
        "--build_txt", build_txt,
        "--stamp", before, after,
        "--overwrite_plugin_version",
    ])

    self.assertEqual({
        "ANY-DIR/anyfile.xml": "<idea-version since-build=\"1234.3333\" until-build=\"1234.3333\">"
      }, read_zip(after))

  def test_change_since(self):
    build_txt = create_file("build.txt", "AI-1234.3333")
    before = create_zip("before.jar", {
          "ANY-DIR/anyfile.xml": "<idea-version since-build=\"1.1\">"
    })
    after = get_path("res.jar")
    stamper.main([
        "--entry", "ANY-DIR/anyfile.xml",
        "--build_txt", build_txt,
        "--stamp", before, after,
        "--overwrite_plugin_version",
    ])

    self.assertEqual({
        "ANY-DIR/anyfile.xml": "<idea-version since-build=\"1234.3333\">"
      }, read_zip(after))

  def test_stamp_product_info(self):
    build_txt = create_file("build.txt", "AI-1234.3333")
    stable = create_file("info.txt", "BUILD_EMBED_LABEL 3333")
    before = create_file("product-info.json", '{"name": "Studio", "version": "dev build", "buildNumber": "AI-1234.__BUILD_NUMBER__"}')
    after = get_path("res.json")
    stamper.main([
        "--info_file", stable,
        "--build_txt", build_txt,
        "--stamp", before, after,
        "--stamp_product_info"
    ])
    self.assertEqual(json.dumps({"name": "Studio", "version": "AI-1234.3333", "buildNumber": "AI-1234.3333"}, sort_keys=True, indent=2),
                     read_file(after))

  def test_replace_build_number(self):
    stable = create_file("info.txt", "BUILD_EMBED_LABEL 3333")
    before = create_file("like_build.txt", "AI-1234.__BUILD_NUMBER__")
    after = get_path("res.txt")
    stamper.main([
        "--info_file", stable,
        "--stamp", before, after,
        "--replace_build_number"
    ])
    self.assertEqual("AI-1234.3333", read_file(after)) 

  def test_stamp_app_info(self):
    volatile = create_file("volatile.txt", "BUILD_TIMESTAMP 1597877532")
    build_txt = create_file("build.txt", "AI-1234.3333")
    before = create_zip("resources.jar", {
        "idea/AndroidStudioApplicationInfo.xml": """
      <build number="AI-__BUILD__" date="__BUILD_DATE__">
      <version major="4" minor="3" micro="2" patch="1" full="a" eap="false" >"""
    })
    after = get_path("res.zip")
    stamper.main([
        "--entry", "idea/AndroidStudioApplicationInfo.xml",
        "--version_file", volatile, 
        "--build_txt", build_txt,
        "--version_micro", "33",
        "--version_patch", "44",
        "--version_full", "{0} Canary 5",
        "--eap", "false",
        "--stamp", before, after,
        "--stamp_app_info"
    ])
    self.maxDiff=None
    self.assertEqual({
        "idea/AndroidStudioApplicationInfo.xml": """
      <build number="AI-1234.3333" date="202008192252">
      <version major="4" minor="3" micro="33" patch="44" full="{0} Canary 5" eap="false" >"""
    }, read_zip(after))

  def test_inject(self):
    build_txt = create_file("build.txt", "AI-1234.3333")
    before = create_zip("before.jar", {
          "ANY-DIR/anyfile.xml": "<id>x</id>"
    })
    after = get_path("res.jar")
    stamper.main([
        "--entry", "ANY-DIR/anyfile.xml",
        "--build_txt", build_txt,
        "--stamp", before, after,
        "--overwrite_plugin_version",
    ])

    self.assertEqual({
        "ANY-DIR/anyfile.xml": "<id>x</id>\n" +
        "  <idea-version since-build=\"1234.3333\" until-build=\"1234.3333\"/>\n" +
        "  <version>1234.3333</version>"
      }, read_zip(after))


if __name__ == "__main__":
  unittest.main()

import os
import io
import unittest
import stamper
import zipfile


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
        res[name] = data
  return res


def read_zip(path):
  return read_zip_data(read_file(path, "rb"))


class ToolsTest(unittest.TestCase):
  """Tests tools used to bundle Android Studio."""

  def test_change_version(self):
    stable = create_file("info.txt", "BUILD_EMBED_LABEL 3333")
    volatile = create_file("volatile.txt", "BUILD_TIMESTAMP 1597877532")
    platform = create_zip("platform.zip", {
      "android-studio/build.txt": "AI-1234.__BUILD_NUMBER__"
    })
    before = create_zip("plugin.zip", {
      "plugin/a/lib/a.jar": {
          "META-INF/plugin.xml": "<version>1</version>"
        }
    })
    res = get_path("res.zip")
    stamper.main([
        "--os", "linux",
        "--info_file", stable,
        "--version_file", volatile,
        "--platform", platform,
        "--stamp_plugin", before, res,
    ])

    self.assertEqual({
      "plugin/a/lib/a.jar": {
          "META-INF/plugin.xml": "<version>1234.3333</version>"
        }
      }, read_zip(res))

  def test_change_since_until(self):
    stable = create_file("info.txt", "BUILD_EMBED_LABEL 3333")
    volatile = create_file("volatile.txt", "BUILD_TIMESTAMP 1597877532")
    platform = create_zip("platform.zip", {
      "android-studio/build.txt": "AI-1234.__BUILD_NUMBER__"
    })
    before = create_zip("plugin.zip", {
      "plugin/a/lib/a.jar": {
          "META-INF/plugin.xml": "<idea-version since-build=\"1.1\" until-build=\"2.1\">"
        }
    })
    res = get_path("res.zip")
    stamper.main([
        "--os", "linux",
        "--info_file", stable,
        "--version_file", volatile,
        "--platform", platform,
        "--stamp_plugin", before, res,
    ])

    self.assertEqual({
      "plugin/a/lib/a.jar": {
          "META-INF/plugin.xml": "<idea-version since-build=\"1234.3333\" until-build=\"1234.3333\">"
        }
      }, read_zip(res))

  def test_change_since(self):
    stable = create_file("info.txt", "BUILD_EMBED_LABEL 3333")
    volatile = create_file("volatile.txt", "BUILD_TIMESTAMP 1597877532")
    platform = create_zip("platform.zip", {
      "android-studio/build.txt": "AI-1234.__BUILD_NUMBER__"
    })
    before = create_zip("plugin.zip", {
      "plugin/a/lib/a.jar": {
          "META-INF/plugin.xml": "<idea-version since-build=\"1.1\">"
        }
    })
    res = get_path("res.zip")
    stamper.main([
        "--os", "linux",
        "--info_file", stable,
        "--version_file", volatile,
        "--platform", platform,
        "--stamp_plugin", before, res,
    ])

    self.assertEqual({
      "plugin/a/lib/a.jar": {
          "META-INF/plugin.xml": "<idea-version since-build=\"1234.3333\">"
        }
      }, read_zip(res))

  def test_stamp_linux_platform(self):
    stable = create_file("info.txt", "BUILD_EMBED_LABEL 3333")
    volatile = create_file("volatile.txt", "BUILD_TIMESTAMP 1597877532")
    platform = create_zip("platform.zip", {
      "android-studio/build.txt": "AI-1234.__BUILD_NUMBER__",
      "android-studio/product-info.json": "Info __BUILD_NUMBER__ __BUILD_NUMBER__",
      "android-studio/lib/resources.jar": {
        "idea/AndroidStudioApplicationInfo.xml": """
      <build number="AI-__BUILD__" date="__BUILD_DATE__">
      <version major="4" minor="3" micro="2" patch="1" full="a" eap="false" >"""
      }
    })
    res = get_path("res.zip")
    stamper.main([
        "--os", "linux",
        "--info_file", stable,
        "--version_file", volatile,
        "--platform", platform,
        "--version_micro", "33",
        "--version_patch", "44",
        "--version_full", "{0} Canary 5",
        "--eap", "true",
        "--stamp_platform", res,
    ])

    self.assertEqual({
      "android-studio/build.txt": "AI-1234.3333",
      "android-studio/product-info.json": "Info 3333 3333",
      "android-studio/lib/resources.jar": {
        "idea/AndroidStudioApplicationInfo.xml": """
      <build number="AI-1234.3333" date="202008192252">
      <version major="4" minor="3" micro="33" patch="44" full="{0} Canary 5" eap="true" >"""
      }
    }, read_zip(res))

  def test_stamp_mac_platform(self):
    stable = create_file("info.txt", "BUILD_EMBED_LABEL 3333")
    volatile = create_file("volatile.txt", "BUILD_TIMESTAMP 1597877532")
    platform = create_zip("platform.zip", {
      "Android Studio.app/Contents/Resources/build.txt": "AI-1234.__BUILD_NUMBER__",
      "Android Studio.app/Contents/Info.plist": "Info __BUILD_NUMBER__ __BUILD_NUMBER__",
      "Android Studio.app/Contents/lib/resources.jar": {
        "idea/AndroidStudioApplicationInfo.xml": """
      <build number="AI-__BUILD__" date="__BUILD_DATE__">
      <version major="4" minor="3" micro="2" patch="1" full="a" eap="false" >"""
      }
    })
    res = get_path("res.zip")
    stamper.main([
        "--os", "mac",
        "--info_file", stable,
        "--version_file", volatile,
        "--platform", platform,
        "--version_micro", "33",
        "--version_patch", "44",
        "--version_full", "{0} Canary 5",
        "--eap", "true",
        "--stamp_platform", res,
    ])

    self.assertEqual({
      "Android Studio.app/Contents/Resources/build.txt": "AI-1234.3333",
      "Android Studio.app/Contents/Info.plist": "Info 3333 3333",
      "Android Studio.app/Contents/lib/resources.jar": {
        "idea/AndroidStudioApplicationInfo.xml": """
      <build number="AI-1234.3333" date="202008192252">
      <version major="4" minor="3" micro="33" patch="44" full="{0} Canary 5" eap="true" >"""
      }
    }, read_zip(res))

  def test_inject(self):
    stable = create_file("info.txt", "BUILD_EMBED_LABEL 3333")
    volatile = create_file("volatile.txt", "BUILD_TIMESTAMP 1597877532")
    platform = create_zip("platform.zip", {
      "android-studio/build.txt": "AI-1234.__BUILD_NUMBER__"
    })
    before = create_zip("plugin.zip", {
      "plugin/a/lib/a.jar": {
          "META-INF/plugin.xml": "<id>x</id>"
        }
    })
    res = get_path("res.zip")
    stamper.main([
        "--os", "linux",
        "--info_file", stable,
        "--version_file", volatile,
        "--platform", platform,
        "--stamp_plugin", before, res,
    ])

    self.assertEqual({
      "plugin/a/lib/a.jar": {
          "META-INF/plugin.xml": "<id>x</id>\n" +
        "  <idea-version since-build=\"1234.3333\" until-build=\"1234.3333\"/>\n" +
        "  <version>1234.3333</version>"
        }
      }, read_zip(res))


if __name__ == "__main__":
  unittest.main()

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

  def test_change_since_until_major(self):
    build_txt = create_file("build.txt", "AI-1234.3333")
    before = create_zip("before.jar", {
          "ANY-DIR/anyfile.xml": "<idea-version since-build=\"1.1\" until-build=\"2.1\"/>"
    })
    after = get_path("res.jar")
    stamper.main([
        "--entry", "ANY-DIR/anyfile.xml",
        "--build_txt", build_txt,
        "--stamp", before, after,
        "--overwrite_since_until_builds",
    ])

    self.assertEqual({
        "ANY-DIR/anyfile.xml": "<idea-version since-build=\"1234\" until-build=\"1234.*\"/>"
      }, read_zip(after))

  def test_change_since_until_major_new(self):
    build_txt = create_file("build.txt", "AI-1234.3333")
    before = create_zip("before.jar", {
          "ANY-DIR/anyfile.xml": "<id>my.id</id>"
    })
    after = get_path("res.jar")
    stamper.main([
        "--entry", "ANY-DIR/anyfile.xml",
        "--build_txt", build_txt,
        "--stamp", before, after,
        "--overwrite_since_until_builds",
    ])

    self.assertEqual({
        "ANY-DIR/anyfile.xml": "<id>my.id</id>\n  <idea-version since-build=\"1234\" until-build=\"1234.*\"/>"
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
    before = create_file("product-info.json", json.dumps({
      "name": "Studio",
      "version": "dev build",
      "buildNumber": "__BUILD_NUMBER__",
      "bundledPlugins": [
        "some.platform.plugin",
      ],
      "layout": [
        {
          "name": "some.platform.plugin",
          "kind": "plugin",
          "classPath": [],
        },
      ],
    }))
    after = get_path("res.json")
    stamper.main([
        "--build_txt", build_txt,
        "--stamp", before, after,
        "--stamp_product_info",
        "--added_plugin", "org.jetbrains.android", "plugins/android/lib/android.jar", "plugins/android/lib/asm.jar",
    ])
    expected = json.dumps({
      "name": "Studio",
      "version": "AI-1234.3333",
      "buildNumber": "1234.3333",
      "bundledPlugins": [
        "some.platform.plugin",
        "org.jetbrains.android",
      ],
      "layout": [
        {
          "name": "some.platform.plugin",
          "kind": "plugin",
          "classPath": [],
        },
        {
          "name": "org.jetbrains.android",
          "kind": "plugin",
          "classPath": [
            "plugins/android/lib/android.jar",
            "plugins/android/lib/asm.jar",
          ],
        },
      ],
    }, indent=2)
    self.assertEqual(expected, read_file(after))

  def test_add_essential_plugins(self):
    volatile = create_file(
        "volatile.txt",
        """BUILD_HOSTNAME hostname.c.googlers.com
BUILD_TIMESTAMP 1746478494
BUILD_USERNAME username
FORMATTED_DATE 2025 May 05 20 54 54 Mon""",
    )

    build_txt = create_file("build.txt", "AI-251.23774.435.2511.SNAPSHOT")

    before = create_zip(
        "resources.jar",
        {"idea/AndroidStudioApplicationInfo.xml": """<!--
  ~ Copyright 2000-2013 JetBrains s.r.o.
  -->
<component xmlns="http://jetbrains.org/intellij/schema/application-info"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://jetbrains.org/intellij/schema/application-info http://jetbrains.org/intellij/schema/ApplicationInfo.xsd">
  <version major="2025" minor="1" micro="1" patch="9" full="Narwhal | {0}.{1}.{2} Canary 9" suffix="" eap="true"/>
  <company name="Google" url="http://developer.android.com"/>
  <build number="AI-251.23774.435.2511.13434847" date="202505011510" apiVersion="251.23774.435"/>
  <logo url="/artwork/studio_splash.png"/>
  <icon svg="/artwork/androidstudio.svg" svg-small="/artwork/androidstudio-small.svg" ico="artwork/androidstudio.ico"/>
  <icon-eap svg="/artwork/preview/androidstudio.svg" svg-small="/artwork/preview/androidstudio-small.svg"/>
  <names product="Studio" fullname="Android Studio" script="studio"/> <!-- fullname is used by NPW to show default folder for projects as -->
  <essential-plugin>com.intellij.java</essential-plugin>
</component>"""},
    )

    after = get_path("res.txt")

    stamper.main([
        "--entry",
        "idea/AndroidStudioApplicationInfo.xml",
        "--version_file",
        volatile,
        "--version_full",
        "Narwhal | {0}.{1}.{2} Canary 9",
        "--eap",
        "true",
        "--version_micro",
        "1",
        "--version_patch",
        "9",
        "--build_txt",
        build_txt,
        "--essential_plugins",
        "com.google.idea.g3plugins",
        "--stamp_app_info",
        "--stamp",
        before,
        after,
    ])

    self.maxDiff = None

    self.assertEqual(
        {"idea/AndroidStudioApplicationInfo.xml": """<!--
  ~ Copyright 2000-2013 JetBrains s.r.o.
  -->
<component xmlns="http://jetbrains.org/intellij/schema/application-info"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://jetbrains.org/intellij/schema/application-info http://jetbrains.org/intellij/schema/ApplicationInfo.xsd">
  <version major="2025" minor="1" micro="1" patch="9" full="Narwhal | {0}.{1}.{2} Canary 9" suffix="" eap="true"/>
  <company name="Google" url="http://developer.android.com"/>
  <build number="AI-251.23774.435.2511.13434847" date="202505011510" apiVersion="251.23774.435"/>
  <logo url="/artwork/studio_splash.png"/>
  <icon svg="/artwork/androidstudio.svg" svg-small="/artwork/androidstudio-small.svg" ico="artwork/androidstudio.ico"/>
  <icon-eap svg="/artwork/preview/androidstudio.svg" svg-small="/artwork/preview/androidstudio-small.svg"/>
  <names product="Studio" fullname="Android Studio" script="studio"/> <!-- fullname is used by NPW to show default folder for projects as -->
  <essential-plugin>com.intellij.java</essential-plugin>
  <essential-plugin>com.google.idea.g3plugins</essential-plugin>
</component>"""},
        read_zip(after),
    )

  def test_add_essential_plugins_to_empty_list(self):
    volatile = create_file(
        "volatile.txt",
        """BUILD_HOSTNAME hostname.c.googlers.com
BUILD_TIMESTAMP 1746478494
BUILD_USERNAME username
FORMATTED_DATE 2025 May 05 20 54 54 Mon""",
    )

    build_txt = create_file("build.txt", "AI-251.23774.435.2511.SNAPSHOT")

    before = create_zip(
        "resources.jar",
        {"idea/AndroidStudioApplicationInfo.xml": """<!--
  ~ Copyright 2000-2013 JetBrains s.r.o.
  -->
<component xmlns="http://jetbrains.org/intellij/schema/application-info"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://jetbrains.org/intellij/schema/application-info http://jetbrains.org/intellij/schema/ApplicationInfo.xsd">
  <version major="2025" minor="1" micro="1" patch="9" full="Narwhal | {0}.{1}.{2} Canary 9" suffix="" eap="true"/>
  <company name="Google" url="http://developer.android.com"/>
  <build number="AI-251.23774.435.2511.13434847" date="202505011510" apiVersion="251.23774.435"/>
  <logo url="/artwork/studio_splash.png"/>
  <icon svg="/artwork/androidstudio.svg" svg-small="/artwork/androidstudio-small.svg" ico="artwork/androidstudio.ico"/>
  <icon-eap svg="/artwork/preview/androidstudio.svg" svg-small="/artwork/preview/androidstudio-small.svg"/>
  <names product="Studio" fullname="Android Studio" script="studio"/> <!-- fullname is used by NPW to show default folder for projects as -->
</component>"""},
    )

    after = get_path("res.txt")

    stamper.main([
        "--entry",
        "idea/AndroidStudioApplicationInfo.xml",
        "--version_file",
        volatile,
        "--version_full",
        "Narwhal | {0}.{1}.{2} Canary 9",
        "--eap",
        "true",
        "--version_micro",
        "1",
        "--version_patch",
        "9",
        "--build_txt",
        build_txt,
        "--essential_plugins",
        "com.google.idea.g3plugins",
        "--stamp_app_info",
        "--stamp",
        before,
        after,
    ])

    self.maxDiff = None

    self.assertEqual(
        {"idea/AndroidStudioApplicationInfo.xml": """<!--
  ~ Copyright 2000-2013 JetBrains s.r.o.
  -->
<component xmlns="http://jetbrains.org/intellij/schema/application-info"
           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
           xsi:schemaLocation="http://jetbrains.org/intellij/schema/application-info http://jetbrains.org/intellij/schema/ApplicationInfo.xsd">
  <version major="2025" minor="1" micro="1" patch="9" full="Narwhal | {0}.{1}.{2} Canary 9" suffix="" eap="true"/>
  <company name="Google" url="http://developer.android.com"/>
  <build number="AI-251.23774.435.2511.13434847" date="202505011510" apiVersion="251.23774.435"/>
  <logo url="/artwork/studio_splash.png"/>
  <icon svg="/artwork/androidstudio.svg" svg-small="/artwork/androidstudio-small.svg" ico="artwork/androidstudio.ico"/>
  <icon-eap svg="/artwork/preview/androidstudio.svg" svg-small="/artwork/preview/androidstudio-small.svg"/>
  <names product="Studio" fullname="Android Studio" script="studio"/> <!-- fullname is used by NPW to show default folder for projects as -->
  <essential-plugin>com.google.idea.g3plugins</essential-plugin>
</component>"""},
        read_zip(after),
    )

  def test_replace_build_number(self):
    stable = create_file("info.txt", "BUILD_EMBED_LABEL 3333")
    before = create_file("like_build.txt", "AI-__BUILD_NUMBER__")
    after = get_path("res.txt")
    stamper.main([
        "--info_file", stable,
        "--version_component", "1234",
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

  def test_stamp_day_in_full(self):
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
        "--version_full", "{0} Nightly __BUILD_DAY__",
        "--eap", "false",
        "--replace_build_day",
        "--stamp", before, after,
        "--stamp_app_info"
    ])
    self.maxDiff=None
    self.assertEqual({
        "idea/AndroidStudioApplicationInfo.xml": """
      <build number="AI-1234.3333" date="202008192252">
      <version major="4" minor="3" micro="33" patch="44" full="{0} Nightly 2020-08-19" eap="false" >"""
    }, read_zip(after))

  def test_replace_subs(self):
    volatile = create_file("volatile.txt", "BUILD_TIMESTAMP 1597877532")
    before = create_file("file.txt", "Some {a} text {b} here")
    after = get_path("after.txt")
    stamper.main([
        "--version_file", volatile,
        "--stamp", before, after,
        "--substitute", "{a}", "AA",
        "--substitute", "{b}", "b__BUILD_DAY__b",
        "--replace_build_day"
    ])
    self.assertEqual("Some AA text b2020-08-19b here", read_file(after))

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

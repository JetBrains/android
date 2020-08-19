import os
import unittest
import stamper


def read_file(path):
  with open(path) as f:
    return f.read()


def get_path(name):
  return os.path.join(os.getenv("TEST_TMPDIR"), name)


def create_file(name, content):
  path = get_path(name)
  with open(path, "w") as f:
    f.write(content)
  return path


class ToolsTest(unittest.TestCase):
  """Tests tools used to bundle Android Studio."""

  def test_change_version(self):
    stable = create_file("info.txt", "BUILD_EMBED_LABEL 3333")
    volatile = create_file("volatile.txt", "BUILD_TIMESTAMP 1597877532")
    build = create_file("build.txt", "AI-1234.__BUILD_NUMBER__")
    before = create_file("p.xml", "<version>1</version>")
    res = get_path("res.xml")
    stamper.main([
        "--info_file", stable,
        "--version_file", volatile,
        "--build_file", build,
        "--stamp_plugin", before, res,
    ])

    self.assertEqual("<version>1234.3333</version>", read_file(res))

  def test_change_since_until(self):
    stable = create_file("info.txt", "BUILD_EMBED_LABEL 3333")
    volatile = create_file("volatile.txt", "BUILD_TIMESTAMP 1597877532")
    build = create_file("build.txt", "AI-1234.__BUILD_NUMBER__")
    before = create_file("p.xml", "<idea-version since-build=\"1.1\" until-build=\"2.1\">")
    res = get_path("res.xml")
    stamper.main([
        "--info_file", stable,
        "--version_file", volatile,
        "--build_file", build,
        "--stamp_plugin", before, res,
    ])

    self.assertEqual("<idea-version since-build=\"1234.3333\" until-build=\"1234.3333\">", read_file(res))

  def test_change_since(self):
    stable = create_file("info.txt", "BUILD_EMBED_LABEL 3333")
    volatile = create_file("volatile.txt", "BUILD_TIMESTAMP 1597877532")
    build = create_file("build.txt", "AI-1234.__BUILD_NUMBER__")
    before = create_file("p.xml", "<idea-version since-build=\"1.1\">")
    res = get_path("res.xml")
    stamper.main([
        "--info_file", stable,
        "--version_file", volatile,
        "--build_file", build,
        "--stamp_plugin", before, res,
    ])

    self.assertEqual("<idea-version since-build=\"1234.3333\">", read_file(res))

  def test_app_info(self):
    stable = create_file("info.txt", "BUILD_EMBED_LABEL 3333")
    volatile = create_file("volatile.txt", "BUILD_TIMESTAMP 1597877532")
    build = create_file("build.txt", "AI-1234.__BUILD_NUMBER__")
    before = create_file("p.xml", """
      <build number="AI-__BUILD__" date="__BUILD_DATE__">
      <version major="4" minor="3" micro="2" patch="1" full="a" eap="false" >""")
    res = get_path("res.xml")
    stamper.main([
        "--info_file", stable,
        "--version_file", volatile,
        "--build_file", build,
        "--version",
        "11.22.33.44",
        "--version_full",
        "{0} Canary 5",
        "--eap",
        "true",
        "--stamp_app_info",
        before,
        res,
    ])

    self.assertEqual("""
      <build number="AI-1234.3333" date="202008192252">
      <version major="11" minor="22" micro="33" patch="44" full="{0} Canary 5" eap="true" >""", read_file(res))

  def test_inject(self):
    stable = create_file("info.txt", "BUILD_EMBED_LABEL 3333")
    volatile = create_file("volatile.txt", "BUILD_TIMESTAMP 1597877532")
    build = create_file("build.txt", "AI-1234.__BUILD_NUMBER__")
    before = create_file("p.xml", "<id>x</id>")
    res = get_path("res.xml")
    stamper.main([
        "--info_file", stable,
        "--version_file", volatile,
        "--build_file", build,
        "--stamp_plugin", before, res,
    ])

    self.assertEqual(
        "<id>x</id>\n" +
        "  <idea-version since-build=\"1234.3333\" until-build=\"1234.3333\"/>\n" +
        "  <version>1234.3333</version>",
        read_file(res))

  def test_stamp_file(self):
    stable = create_file("info.txt", "BUILD_EMBED_LABEL 3333")
    volatile = create_file("volatile.txt", "BUILD_TIMESTAMP 1597877532")
    build = create_file("build.txt", "AI-1234.__BUILD_NUMBER__")
    before = create_file("file.txt", "Change the __BUILD_NUMBER__")
    res = get_path("res.txt")
    stamper.main([
        "--info_file", stable,
        "--version_file", volatile,
        "--build_file", build,
        "--stamp_build",
        before,
        res,
    ])

    self.assertEqual("Change the 3333", read_file(res))


if __name__ == "__main__":
  unittest.main()

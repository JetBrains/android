import os
import unittest
import subprocess
import tempfile
from tools.adt.idea.studio.tests import test_utils


class MkSpecTest(unittest.TestCase):

  def test_mkspec(self):
    deploy_dir = test_utils.deploy_py("mkspec_files")

    download = tempfile.mkdtemp()
    test_utils.create_all(download, {"lib/app.jar": {"__index__": "data"},
        "lib/resources.jar": {
            "idea/AndroidStudioApplicationInfo.xml": "<version major=\"2024\" minor=\"1\">",
        },
        "plugins/only_linux/lib/foo.jar": {"META-INF/plugin.xml": "<xml><id>com.sample.only_linux</id></xml>"},
        "plugins/linux_windows/lib/foo.jar": {"META-INF/plugin.xml": "<xml><id>com.sample.linux_windows</id></xml>"},
        "plugins/common/lib/common.jar": {"META-INF/plugin.xml": "<xml><id>com.sample.common</id></xml>"},
        "product-info.json": {
            "launch": [{
                "bootClassPathJarNames": [],
            }]
        },
    })

    bzl = os.path.join(download, "spec.bzl")
    ret = subprocess.run(deploy_dir + "/tools/adt/idea/studio/mkspec.py --path " + download + " --out " + bzl, shell=True, env = {})
    ret.check_returncode()


    expected = """# Auto-generated file, do not edit manually.
SPEC = struct(
    major_version = "2024",
    minor_version = "1",
    jars = [
    ],
    jars_linux = [
    ],
    plugin_jars = {
        "com.sample.common": [
            "plugins/common/lib/common.jar",
        ],
        "com.sample.linux_windows": [
            "plugins/linux_windows/lib/foo.jar",
        ],
        "com.sample.only_linux": [
            "plugins/only_linux/lib/foo.jar",
        ],
    },
    plugin_jars_linux = {
    },
    mac_bundle_name = "",
)
"""
    actual = test_utils.readstr(bzl)
    if expected != actual:
      print(actual)
    self.assertEqual(expected, actual)

if __name__ == "__main__":
  unittest.main()
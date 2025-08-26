import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from tools.adt.idea.studio.tests import test_utils


class UpdateSherlockSdkTest(unittest.TestCase):

  def setUp(self):
    self.download_dir = Path(tempfile.mkdtemp(prefix="sherlock_download_"))
    self.workspace_dir = Path(tempfile.mkdtemp(prefix="sherlock_workspace_"))
    self.prebuilts_path = self.workspace_dir / "prebuilts" / "studio" / "intellij-sdk"
    self.prebuilts_path.mkdir(parents=True, exist_ok=True)

    self.deploy_dir = Path(test_utils.deploy_py("update_sherlock_sdk_files"))
    self.script_path = self.deploy_dir / "tools/adt/idea/studio/update_sherlock_sdk.py"

  def tearDown(self):
    shutil.rmtree(self.download_dir)
    shutil.rmtree(self.workspace_dir)

  def test_with_path(self):
    product_info = {
      "name": "Sherlock",
      "version": "2.0",
      "buildNumber": "251.26094",
      "productCode": "IC",
      "dataDirectoryName": "SherlockPlatform",
      "launch": [
        {
          "os": "Linux",
          "arch": "aarch64",
          "bootClassPathJarNames": ["app.jar", "lib.jar"],
          "additionalJvmArguments": [
            "-Didea.platform.prefix=SherlockPlatform"
          ]
        }
      ]
    }

    app_info_xml = '<application-info><version major="2" minor="0"/></application-info>'
    dummy_lib_content = {"__index__": "data"}
    common_lib_files = {
      "lib/app.jar": {
        "__index__": "data",
        "idea/SherlockPlatformApplicationInfo.xml": app_info_xml
      },
      "lib/lib.jar": dummy_lib_content,
    }
    plugin_xml_content = "<idea-plugin><id>com.sample.common</id></idea-plugin>"
    module_plugin_xml_content = "<idea-plugin><id>com.sample.module</id></idea-plugin>"

    common_plugin_files = {
      "plugins/common/lib/common.jar": {"META-INF/plugin.xml": plugin_xml_content},
      "lib/modules/com.sample.module.jar": {"META-INF/plugin.xml": module_plugin_xml_content},
    }

    # Linux
    linux_artifacts = {"Sherlock-2.1/" + k: v for k, v in common_lib_files.items()}
    linux_artifacts.update({"Sherlock-2.1/" + k: v for k, v in common_plugin_files.items()})
    linux_artifacts["Sherlock-2.1/product-info.json"] = product_info
    test_utils.create(str(self.download_dir / "sherlock-platform.tar.gz"), linux_artifacts)

    # Mac ARM
    mac_arm_artifacts = {"Sherlock.app/Contents/" + k: v for k, v in common_lib_files.items()}
    mac_arm_artifacts.update({"Sherlock.app/Contents/" + k: v for k, v in common_plugin_files.items()})
    mac_arm_artifacts["Sherlock.app/Contents/Resources/product-info.json"] = product_info
    test_utils.create(str(self.download_dir / "sherlock-platform.mac.aarch64.zip"), mac_arm_artifacts)

    # Mac x86_64
    mac_x64_artifacts = {"Sherlock.app/Contents/" + k: v for k, v in common_lib_files.items()}
    mac_x64_artifacts.update({"Sherlock.app/Contents/" + k: v for k, v in common_plugin_files.items()})
    mac_x64_artifacts["Sherlock.app/Contents/Resources/product-info.json"] = product_info
    test_utils.create(str(self.download_dir / "sherlock-platform.mac.x64.zip"), mac_x64_artifacts)

    # Windows
    win_artifacts = common_lib_files.copy()
    win_artifacts.update(common_plugin_files)
    win_artifacts["product-info.json"] = product_info
    test_utils.create(str(self.download_dir / "sherlock-platform.win.zip"), win_artifacts)

    # Sources
    test_utils.create(str(self.download_dir / "sherlock-platform-sources.zip"), {})

    env = os.environ.copy()
    pythonpath = self.deploy_dir / "tools/adt/idea/studio"
    env["PYTHONPATH"] = str(pythonpath) + os.pathsep + env.get("PYTHONPATH", "")

    cmd = [
      sys.executable,
      str(self.script_path),
      "--path", str(self.download_dir),
      "--workspace", str(self.workspace_dir)
    ]
    subprocess.run(cmd, capture_output=True, text=True, env=env)
    expected_metadata = f"local_path: {self.download_dir}\n"
    expected_spec_bzl = """# Auto-generated file, do not edit manually.
SPEC = struct(
    major_version = "2",
    minor_version = "0",
    jars = [
        "/lib/app.jar",
        "/lib/lib.jar",
    ],
    jars_darwin = [
    ],
    jars_darwin_aarch64 = [
    ],
    jars_linux = [
    ],
    jars_windows = [
    ],
    plugin_jars = {
        "com.sample.common": [
            "plugins/common/lib/common.jar",
        ],
        "com.sample.module": [
            "lib/modules/com.sample.module.jar",
        ],
    },
    plugin_jars_darwin = {
    },
    plugin_jars_darwin_aarch64 = {
    },
    plugin_jars_linux = {
    },
    plugin_jars_windows = {
    },
    add_exports = [
    ],
    add_opens = [
    ],
)
"""
    expected = {
      "prebuilts/studio/intellij-sdk/IC/METADATA": expected_metadata,
      "prebuilts/studio/intellij-sdk/IC/spec.bzl": expected_spec_bzl,
    }

    generated = []
    for root, _, filenames in os.walk(self.workspace_dir):
      for filename in filenames:
        if filename.endswith(".bzl") or filename == "METADATA":
          generated.append(Path(root).relative_to(self.workspace_dir).joinpath(filename).as_posix())

    self.assertCountEqual(expected.keys(), generated)

    for file, content in expected.items():
      actual_content = test_utils.readstr(str(self.workspace_dir / file))
      self.assertEqual(content, actual_content)


if __name__ == "__main__":
  unittest.main()

import os
import unittest
import subprocess
import tempfile
import glob
from tools.adt.idea.studio.tests import test_utils

class UpdateSdkTest(unittest.TestCase):

  def test_update_sdk(self):
    deploy_files = os.environ['update_sdk_files'].split(" ")
    deploy_dir = test_utils.deploy_py("update_sdk_files")

    download = tempfile.mkdtemp()
    test_utils.create(download + "/android-studio-1.2.3-no-jbr.tar.gz", {
        "android-studio/lib/app.jar": {
            "__index__": "data",
        },
        "android-studio/lib/resources.jar": {
            "idea/AndroidStudioApplicationInfo.xml": "<version major=\"2024\" minor=\"1\">",
        },
        "android-studio/plugins/plugin-classpath.txt": "data",
        "android-studio/plugins/only_linux/lib/foo.jar": {"META-INF/plugin.xml": "<xml><id>com.sample.only_linux</id></xml>"},
        "android-studio/plugins/linux_windows/lib/foo.jar": {"META-INF/plugin.xml": "<xml><id>com.sample.linux_windows</id></xml>"},
        "android-studio/plugins/common/lib/common.jar": {"META-INF/plugin.xml": "<xml><id>com.sample.common</id></xml>"},
        "android-studio/product-info.json": {
            "launch": [{
                "bootClassPathJarNames": [],
                "additionalJvmArguments": ["-Didea.platform.prefix=AndroidStudio"],
            }]
        },
    })
    test_utils.create(download + "/android-studio-1.2.3-no-jbr.win.zip", {
        "android-studio/lib/app.jar": {
            "__index__": "data",
        },
        "android-studio/lib/resources.jar": {
            "idea/AndroidStudioApplicationInfo.xml": "<version major=\"2024\" minor=\"1\">",
        },
        "android-studio/product-info.json": {
            "launch": [{
                "bootClassPathJarNames": [],
                "additionalJvmArguments": ["-Didea.platform.prefix=AndroidStudio"],
            }]
        },
        "android-studio/plugins/linux_windows/lib/foo.jar": {"META-INF/plugin.xml": "<xml><id>com.sample.linux_windows</id></xml>"},
        "android-studio/plugins/common/lib/common.jar": {"META-INF/plugin.xml": "<xml><id>com.sample.common</id></xml>"},
        "android-studio/plugins/plugin-classpath.txt": "data",
    })
    test_utils.create(download + "/android-studio-1.2.3.mac.aarch64-no-jdk.zip", {
        "Android Studio.app/Contents/lib/app.jar": {
            "__index__": "data",
        },
        "Android Studio.app/Contents/lib/resources.jar": {
            "idea/AndroidStudioApplicationInfo.xml": "<version major=\"2024\" minor=\"1\">",
        },
        "Android Studio.app/Contents/Resources/product-info.json": {
            "launch": [{
                "bootClassPathJarNames": [],
                "additionalJvmArguments": ["-Didea.platform.prefix=AndroidStudio"],
            }]
        },
        "Android Studio.app/Contents/plugins/common/lib/common.jar": {"META-INF/plugin.xml": "<xml><id>com.sample.common</id></xml>"},
        "Android Studio.app/Contents/plugins/plugin-classpath.txt": "data",
    })
    test_utils.create(download + "/android-studio-1.2.3.mac.x64-no-jdk.zip", {
        "Android Studio.app/Contents/lib/app.jar": {
            "__index__": "data",
        },
        "Android Studio.app/Contents/lib/resources.jar": {
            "idea/AndroidStudioApplicationInfo.xml": "<version major=\"2024\" minor=\"1\">",
        },
        "Android Studio.app/Contents/Resources/product-info.json": {
            "launch": [{
                "bootClassPathJarNames": [],
                "additionalJvmArguments": ["-Didea.platform.prefix=AndroidStudio"],
            }]
        },
        "Android Studio.app/Contents/plugins/common/lib/common.jar": {"META-INF/plugin.xml": "<xml><id>com.sample.common</id></xml>"},
        "Android Studio.app/Contents/plugins/plugin-classpath.txt": "data",
    })

    test_utils.create(download + "/android-studio-1.2.3-no-jbr.tar.gz.spdx.json", "")
    test_utils.create(download + "/android-studio-1.2.3-no-jbr.win.zip.spdx.json", "")
    test_utils.create(download + "/android-studio-1.2.3.mac.aarch64-no-jdk.zip.spdx.json", "")
    test_utils.create(download + "/android-studio-1.2.3.mac.x64-no-jdk.zip.spdx.json", "")
    test_utils.create(download + "/android-studio-1.2.3-sources.zip", {})
    test_utils.create(download + "/updater-full.jar", {})

    workspace = tempfile.mkdtemp()
    os.makedirs(workspace + "/prebuilts/studio/intellij-sdk/AI")
    os.makedirs(workspace + "/tools/adt/idea/.idea/libraries")
    os.makedirs(workspace + "/tools/adt/idea/studio")

    ret = subprocess.run(deploy_dir + "/tools/adt/idea/studio/update_sdk.py --path " + download + " --workspace " + workspace, shell=True, env = {})
    ret.check_returncode()

    # Assert things are created the expected way

    expected = {
      "prebuilts/studio/intellij-sdk/AI/spec.bzl" : """# Auto-generated file, do not edit manually.
SPEC = struct(
    major_version = "2024",
    minor_version = "1",
    jars = [
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
    },
    plugin_jars_darwin = {
    },
    plugin_jars_darwin_aarch64 = {
    },
    plugin_jars_linux = {
        "com.sample.linux_windows": [
            "plugins/linux_windows/lib/foo.jar",
        ],
        "com.sample.only_linux": [
            "plugins/only_linux/lib/foo.jar",
        ],
    },
    plugin_jars_windows = {
        "com.sample.linux_windows": [
            "plugins/linux_windows/lib/foo.jar",
        ],
    },
    mac_bundle_name = "Android Studio.app",
)
""",
      "tools/adt/idea/.idea/libraries/studio_sdk.xml" : """<component name="libraryTable">
  <library name="studio-sdk">
    <CLASSES>
    </CLASSES>
    <JAVADOC />
    <SOURCES>
      <root url="jar://$PROJECT_DIR$/../../../prebuilts/studio/intellij-sdk/AI/android-studio-sources.zip!/" />
    </SOURCES>
  </library>
</component>""",
      "tools/adt/idea/.idea/libraries/intellij_updater.xml" : """<component name="libraryTable">
  <library name="intellij-updater">
    <CLASSES>
      <root url="jar://$PROJECT_DIR$/../../../prebuilts/studio/intellij-sdk/AI/updater-full.jar!/" />
    </CLASSES>
    <JAVADOC />
    <SOURCES>
      <root url="jar://$PROJECT_DIR$/../../../prebuilts/studio/intellij-sdk/AI/android-studio-sources.zip!/" />
    </SOURCES>
  </library>
</component>""",

      "tools/adt/idea/.idea/libraries/intellij_test_framework.xml" : """<component name="libraryTable">
  <library name="intellij-test-framework">
    <CLASSES>
      <root url="jar://$PROJECT_DIR$/../../../prebuilts/studio/intellij-sdk/AI/$SDK_PLATFORM$/lib/testFramework.jar!/" />
    </CLASSES>
    <JAVADOC />
    <SOURCES>
      <root url="jar://$PROJECT_DIR$/../../../prebuilts/studio/intellij-sdk/AI/android-studio-sources.zip!/" />
    </SOURCES>
  </library>
</component>""",
      "tools/adt/idea/studio/studio-sdk-all-plugins.iml" : """<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
  <component name="NewModuleRootManager" inherit-compiler-output="true">
    <orderEntry type="inheritedJdk" />
    <orderEntry type="sourceFolder" forTests="false" />
    <orderEntry type="library" scope="RUNTIME" name="studio-sdk" level="project" />
    <orderEntry type="library" scope="RUNTIME" name="studio-plugin-com.sample.common" level="project" />
  </component>
</module>""",
      "tools/adt/idea/.idea/libraries/studio_plugin_com.sample.common.xml" : """<component name="libraryTable">
  <library name="studio-plugin-com.sample.common">
    <CLASSES>
      <root url="jar://$PROJECT_DIR$/../../../prebuilts/studio/intellij-sdk/AI/$SDK_PLATFORM$/plugins/common/lib/common.jar!/" />
    </CLASSES>
    <JAVADOC />
    <SOURCES>
      <root url="jar://$PROJECT_DIR$/../../../prebuilts/studio/intellij-sdk/AI/android-studio-sources.zip!/" />
    </SOURCES>
  </library>
</component>""",
      "tools/adt/idea/.idea/libraries/studio_plugin_com.sample.only_linux.xml" : """<component name="libraryTable">
  <library name="studio-plugin-com.sample.only_linux">
    <CLASSES>
      <root url="jar://$PROJECT_DIR$/../../../prebuilts/studio/intellij-sdk/AI/$SDK_PLATFORM$/plugins/only_linux/lib/foo.jar!/" />
    </CLASSES>
    <JAVADOC />
    <SOURCES>
      <root url="jar://$PROJECT_DIR$/../../../prebuilts/studio/intellij-sdk/AI/android-studio-sources.zip!/" />
    </SOURCES>
  </library>
</component>""",
      "tools/adt/idea/.idea/libraries/studio_plugin_com.sample.linux_windows.xml" : """<component name="libraryTable">
  <library name="studio-plugin-com.sample.linux_windows">
    <CLASSES>
      <root url="jar://$PROJECT_DIR$/../../../prebuilts/studio/intellij-sdk/AI/$SDK_PLATFORM$/plugins/linux_windows/lib/foo.jar!/" />
    </CLASSES>
    <JAVADOC />
    <SOURCES>
      <root url="jar://$PROJECT_DIR$/../../../prebuilts/studio/intellij-sdk/AI/android-studio-sources.zip!/" />
    </SOURCES>
  </library>
</component>""",
    }

    generated = []
    for root, directories, filenames in os.walk(workspace):
        for filename in filenames:
            for ext in ["xml", "bzl", "iml"]:
                if filename.endswith(ext):
                    generated.append(os.path.relpath(os.path.join(root, filename), workspace))

    self.assertEqual(sorted(expected.keys()), sorted(generated))

    for file, content in expected.items():
        self.assertEqual(content, test_utils.readstr(workspace + "/" + file))

if __name__ == "__main__":
  unittest.main()
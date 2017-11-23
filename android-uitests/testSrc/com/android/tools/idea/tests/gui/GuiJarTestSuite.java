/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.tests.gui;

import com.android.testutils.ClassSuiteRunner;
import com.android.tools.tests.GradleDaemonsRule;
import com.android.tools.tests.IdeaTestSuiteBase;
import com.android.tools.tests.XDisplayRule;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

import java.io.File;

import static com.android.testutils.TestUtils.getWorkspaceRoot;

@RunWith(ClassSuiteRunner.class)
public class GuiJarTestSuite extends IdeaTestSuiteBase {

  @ClassRule public static GradleDaemonsRule gradle = new GradleDaemonsRule();

  @ClassRule public static XDisplayRule display = new XDisplayRule();

  static {
    optSymlinkToIdeaHome(
      "prebuilts/tools/common/offline-m2",
      "tools/adt/idea/adt-ui/lib/libwebp",
      "tools/adt/idea/android/annotations",
      "tools/adt/idea/android-uitests/testData",
      "tools/adt/idea/artwork/resources/device-art-resources",
      "tools/adt/idea/android/lib",
      "tools/base/templates",
      "tools/idea/java",
      "tools/idea/bin",
      "prebuilts/studio/jdk",
      "prebuilts/studio/layoutlib",
      "prebuilts/studio/sdk");

    setUpOfflineRepo("tools/base/bazel/offline_repo_repo.zip", "out/studio/repo");
    setUpOfflineRepo("tools/adt/idea/android/test_deps_repo.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/adt/idea/android/android-gradle-1.5.0_repo_repo.zip", "prebuilts/tools/common/m2/repository");
    setUpOfflineRepo("tools/data-binding/data_binding_runtime_repo.zip", "prebuilts/tools/common/m2/repository");

    // Enable Kotlin plugin if available(see PluginManagerCore.PROPERTY_PLUGIN_PATH).
    File kotlin = new File(getWorkspaceRoot(), "prebuilts/tools/common/kotlin-plugin/Kotlin");
    if (kotlin.exists()) {
      System.setProperty("plugin.path", kotlin.getAbsolutePath());
    }

    // Make sure we run with UI
    System.setProperty("java.awt.headless", "false");
  }
}

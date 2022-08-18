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
import com.android.testutils.TestUtils;
import com.android.tools.tests.GradleDaemonsRule;
import com.android.tools.tests.IdeaTestSuiteBase;
import com.android.tools.tests.XDisplayRule;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

@RunWith(ClassSuiteRunner.class)
public class GuiJarTestSuite extends IdeaTestSuiteBase {

  @ClassRule public static GradleDaemonsRule gradle = new GradleDaemonsRule();

  @ClassRule public static XDisplayRule display = new XDisplayRule();

  static {
    unzipIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin.zip");
    linkIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin_runtime_dependencies.manifest");
    linkIntoOfflineMavenRepo("tools/adt/idea/android-uitests/test_deps.manifest");
    linkIntoOfflineMavenRepo("tools/base/third_party/kotlin/kotlin-m2repository.manifest");
    unzipIntoOfflineMavenRepo("tools/data-binding/data_binding_runtime.zip");
    linkIntoOfflineMavenRepo("tools/base/build-system/integration-test/kotlin_gradle_plugin_prebuilts.manifest");
    linkIntoOfflineMavenRepo("tools/base/build-system/integration-test/kotlin_gradle_plugin_for_compose_prebuilts.manifest");

    List<File> additionalPlugins = getExternalPlugins();
    if (!additionalPlugins.isEmpty()) {
      String pluginPaths = additionalPlugins.stream().map(f -> f.getAbsolutePath()).collect(Collectors.joining(","));
      Logger.getInstance(GuiJarTestSuite.class).info("Setting additional plugin paths: " + pluginPaths);

      String existingPluginPaths = System.getProperty("plugin.path");
      if (!StringUtil.isEmpty(existingPluginPaths)) {
        pluginPaths = existingPluginPaths + "," + pluginPaths;
      }

      System.setProperty("plugin.path", pluginPaths);
    }

    // Make sure we run with UI
    System.setProperty("java.awt.headless", "false");
  }

  private static List<File> getExternalPlugins() {
    List<File> plugins = new ArrayList<>(1);

    // Enable Bazel plugin if it's available
    File aswb = TestUtils.resolveWorkspacePathUnchecked("tools/adt/idea/android-uitests/aswb").toFile();
    if (aswb.exists()) {
      plugins.add(aswb);
    }

    return plugins;
  }
}

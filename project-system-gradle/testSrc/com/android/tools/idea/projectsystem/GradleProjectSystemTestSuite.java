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
package com.android.tools.idea.projectsystem;

import com.android.testutils.JarTestSuiteRunner;
import com.android.testutils.TestUtils;
import com.android.tools.tests.GradleDaemonsRule;
import com.android.tools.tests.IdeaTestSuiteBase;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.CoreIconManager;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses({
  GradleProjectSystemTestSuite.class,
  GradleProjectSystemOldAgpTestSuite.class,
})
public class GradleProjectSystemTestSuite extends IdeaTestSuiteBase {
  @ClassRule public static GradleDaemonsRule gradle = new GradleDaemonsRule();

  static {
    Path file = TestUtils.getWorkspaceRoot().resolve("tools/base/build-system/android_gradle_plugin.zip");
    if (Files.exists(file)) {
      unzipIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin.zip");
      linkIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin_runtime_dependencies.manifest");
      linkIntoOfflineMavenRepo("tools/adt/idea/project-system-gradle/test_deps.manifest");
      linkIntoOfflineMavenRepo("tools/base/build-system/integration-test/kotlin_gradle_plugin_prebuilts.manifest");
    }
    Path dataBindingSupportFile = TestUtils.getWorkspaceRoot().resolve("tools/data-binding/data_binding_runtime.zip");
    if (Files.exists(dataBindingSupportFile)) {
      unzipIntoOfflineMavenRepo("tools/data-binding/data_binding_runtime.zip");
    }
    // Avoid depending on the execution order and initializing icons with dummies.
    IconLoader.activate();
    try {
      IconManager.Companion.activate(new CoreIconManager());
    }
    catch (Throwable e) {
      e.printStackTrace();
    }
  }
}


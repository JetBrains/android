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
package com.android.tools.idea;

import com.android.testutils.JarTestSuiteRunner;
import com.android.tools.tests.IdeaTestSuiteBase;
import com.android.testutils.TestUtils;
import org.junit.runner.RunWith;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses({
  com.android.tools.idea.KotlinIntegrationTestSuite.class
})
@SuppressWarnings("NewClassNamingConvention") // Not a test.
public class KotlinIntegrationTestSuite extends IdeaTestSuiteBase {

  static {
    symlinkToIdeaHome(
        "tools/adt/idea/android/annotations",
        "tools/adt/idea/artwork/resources/device-art-resources",
        "tools/adt/idea/android/lib",
        "tools/adt/idea/android/testData",
        "tools/adt/idea/kotlin-integration/testData",
        "tools/base/templates",
        "tools/idea/build.txt",
        "tools/idea/java",
        "prebuilts/studio/jdk",
        "prebuilts/studio/sdk");

    setUpOfflineRepo("tools/base/build-system/studio_repo.zip", "out/studio/repo");
    setUpOfflineRepo("tools/adt/idea/kotlin-integration/test_deps.zip", "prebuilts/tools/common/m2/repository");

    // Enable Kotlin plugin (see PluginManagerCore.PROPERTY_PLUGIN_PATH).
    System.setProperty("plugin.path", TestUtils.getWorkspaceFile("prebuilts/tools/common/kotlin-plugin/Kotlin").getAbsolutePath());
  }
}

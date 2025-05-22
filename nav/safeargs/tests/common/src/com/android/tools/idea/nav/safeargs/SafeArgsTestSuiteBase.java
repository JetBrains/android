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
package com.android.tools.idea.nav.safeargs;

import com.android.test.testutils.TestUtils;
import com.android.tools.tests.GradleDaemonsRule;
import com.android.tools.tests.IdeaTestSuiteBase;
import org.junit.ClassRule;

public abstract class SafeArgsTestSuiteBase extends IdeaTestSuiteBase {
  @ClassRule public static GradleDaemonsRule gradle = new GradleDaemonsRule();

  static {
    String TESTDEPS_REPO = "tools/adt/idea/nav/safeargs/tests/testdeps_repo.manifest";
    if (TestUtils.workspaceFileExists(TESTDEPS_REPO)) {
      linkIntoOfflineMavenRepo(TESTDEPS_REPO);
    }
    String ANDROID_GRADLE_PLUGIN = "tools/base/build-system/android_gradle_plugin.zip";
    if (TestUtils.workspaceFileExists(ANDROID_GRADLE_PLUGIN)) {
      unzipIntoOfflineMavenRepo(ANDROID_GRADLE_PLUGIN);
      linkIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin_runtime_dependencies.manifest");
      linkIntoOfflineMavenRepo("tools/base/build-system/integration-test/kotlin_gradle_plugin_prebuilts.manifest");
    }
  }
}

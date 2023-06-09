/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.tools.idea.gradle.project.sync.perf.TestProjectPaths;
import com.android.tools.tests.GradleDaemonsRule;
import com.android.tools.tests.IdeaTestSuiteBase;
import java.util.logging.Logger;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

@RunWith(JarTestSuiteRunner.class)
public class SyncPerfTestSuite extends IdeaTestSuiteBase {
  @ClassRule public static final GradleDaemonsRule gradle = new GradleDaemonsRule();

  private static final Logger LOG = Logger.getLogger(SyncPerfTestSuite.class.getName());

  static {
    try {
      setUpSourceZip("prebuilts/studio/buildbenchmarks/dolphin.3627ef8a/src.zip",
                     // We unzip the source code into the same directory containing other test data.
                     "tools/adt/idea/sync-perf-tests/testData/" + TestProjectPaths.DOLPHIN_PROJECT_ROOT,
                     new DiffSpec("prebuilts/studio/buildbenchmarks/dolphin.3627ef8a/setupForSyncTest.diff", 2));
      unzipIntoOfflineMavenRepo("prebuilts/studio/buildbenchmarks/dolphin.3627ef8a/repo.zip");
    }
    catch(Exception e) {
      LOG.warning("Could not prepare Dolphin project: " + e);
    }

    linkIntoOfflineMavenRepo("tools/adt/idea/sync-perf-tests/test_deps.manifest");
    unzipIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin.zip");
    linkIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin_runtime_dependencies.manifest");
    linkIntoOfflineMavenRepo("tools/base/build-system/integration-test/kotlin_gradle_plugin_prebuilts.manifest");
  }
}

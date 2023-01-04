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
@JarTestSuiteRunner.ExcludeClasses({
  SyncPerfTestSuite.class,               // a suite mustn't contain itself
})
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

    try {
      setUpSourceZip("prebuilts/studio/buildbenchmarks/android-studio-gradle-test.3600041f/src.zip",
                     "tools/adt/idea/sync-perf-tests/testData/" + TestProjectPaths.BASE100,
                     new DiffSpec("prebuilts/studio/buildbenchmarks/android-studio-gradle-test.3600041f/setupForSyncTest.diff", 2));
      unzipIntoOfflineMavenRepo("prebuilts/studio/buildbenchmarks/android-studio-gradle-test.3600041f/repo.zip");
    }
    catch(Exception e) {
      LOG.warning("Could not prepare Base100 project: " + e);
    }

    try {
      setUpSourceZip("prebuilts/studio/buildbenchmarks/android-studio-gradle-test.3600041f/src.zip",
                     "tools/adt/idea/sync-perf-tests/testData/" + TestProjectPaths.BASE100_KOTLIN,
                     new DiffSpec("prebuilts/studio/buildbenchmarks/android-studio-gradle-test.3600041f/setupForSyncKotlinTest.diff", 2));
      unzipIntoOfflineMavenRepo("prebuilts/studio/buildbenchmarks/android-studio-gradle-test.3600041f/repo.zip");
    }
    catch(Exception e) {
      LOG.warning("Could not prepare Base100Kotlin project: " + e);
    }

    try {
      setUpSourceZip("prebuilts/studio/buildbenchmarks/SantaTracker.181be75/src.zip",
                     "tools/adt/idea/sync-perf-tests/testData/" + TestProjectPaths.SANTA_TRACKER,
                     new DiffSpec("prebuilts/studio/buildbenchmarks/SantaTracker.181be75/setupForIdeTest.diff", 2));
      unzipIntoOfflineMavenRepo("prebuilts/studio/buildbenchmarks/SantaTracker.181be75/repo.zip");
    }
    catch(Exception e) {
      LOG.warning("Could not prepare SantaTracker project: " + e);
    }

    try {
      setUpSourceZip("prebuilts/studio/buildbenchmarks/extra-large.2020.09.18/src.zip",
                     "tools/adt/idea/sync-perf-tests/testData/" + TestProjectPaths.EXTRA_LARGE,
                     new DiffSpec("prebuilts/studio/buildbenchmarks/extra-large.2020.09.18/setupForSyncTest.diff", 2));
      unzipIntoOfflineMavenRepo("prebuilts/studio/buildbenchmarks/extra-large.2020.09.18/repo.zip");
    }
    catch(Exception e) {
      LOG.warning("Could not prepare extra-large project: " + e);
    }

    linkIntoOfflineMavenRepo("tools/adt/idea/sync-perf-tests/test_deps.manifest");
    unzipIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin.zip");
    linkIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin_runtime_dependencies.manifest");
    linkIntoOfflineMavenRepo("tools/base/build-system/integration-test/kotlin_gradle_plugin_prebuilts.manifest");
  }
}

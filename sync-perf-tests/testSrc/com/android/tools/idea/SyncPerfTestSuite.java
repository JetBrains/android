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
import com.android.tools.idea.gradle.project.sync.perf.AbstractGradleSyncPerfTestCase;
import com.android.tools.idea.gradle.project.sync.perf.TestProjectPaths;
import com.android.tools.tests.GradleDaemonsRule;
import com.android.tools.tests.IdeaTestSuiteBase;
import com.android.tools.tests.LeakCheckerRule;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses({
  SyncPerfTestSuite.class,              // a suite mustn't contain itself
  AbstractGradleSyncPerfTestCase.class, // Abstract class
})
public class SyncPerfTestSuite extends IdeaTestSuiteBase {
  @ClassRule public static final LeakCheckerRule checker = new LeakCheckerRule();
  @ClassRule public static final GradleDaemonsRule gradle = new GradleDaemonsRule();

  static {
    setUpSourceZip("prebuilts/studio/buildbenchmarks/dolphin.3627ef8a/src.zip",
                   // We unzip the source code into the same directory containing other test data.
                   "tools/adt/idea/sync-perf-tests/testData/" + TestProjectPaths.DOLPHIN_PROJECT_ROOT,
                   new DiffSpec("prebuilts/studio/buildbenchmarks/dolphin.3627ef8a/setupForSyncTest.diff", 2));
    setUpSourceZip("prebuilts/studio/buildbenchmarks/android-studio-gradle-test.3600041f/src.zip",
                   "tools/adt/idea/sync-perf-tests/testData/" + TestProjectPaths.BASE100,
                   new DiffSpec("prebuilts/studio/buildbenchmarks/android-studio-gradle-test.3600041f/setupForSyncTest.diff", 2));

    unzipIntoOfflineMavenRepo("prebuilts/studio/buildbenchmarks/dolphin.3627ef8a/repo.zip");
    unzipIntoOfflineMavenRepo("prebuilts/studio/buildbenchmarks/android-studio-gradle-test.3600041f/repo.zip");
    unzipIntoOfflineMavenRepo("tools/adt/idea/sync-perf-tests/test_deps.zip");
    unzipIntoOfflineMavenRepo("tools/base/build-system/studio_repo.zip");
  }
}

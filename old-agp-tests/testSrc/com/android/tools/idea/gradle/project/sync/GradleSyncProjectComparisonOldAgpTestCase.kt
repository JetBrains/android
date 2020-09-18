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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.project.sync.snapshots.GradleSyncProjectComparisonTest
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.assertIsEqualToSnapshot

/**
 * Snapshot tests for 'Gradle Sync' that use old versions of AGP
 *
 * These tests compare the results of sync by converting the resulting project to a stable text format which does not depend on local
 * environment (and ideally should not depend on the versions of irrelevant libraries) and comparing them to pre-recorded golden sync
 * results.
 *
 * The pre-recorded sync results can be found in testData/syncedProjectSnapshots/ *.txt files. Consult [snapshotSuffixes] for more
 * details on the way in which the file names are constructed.
 *
 * NOTE: It you made changes to sync or the test projects which make these tests fail in an expected way, you can re-run the tests
 *       from IDE with -DUPDATE_TEST_SNAPSHOTS to update the files. (You may need to re-run several times (currently up to 3) to
 *       update multiple snapshots used in one test.
 *
 *       Or with bazel:
bazel test //tools/adt/idea/old-agp-tests:intellij.android.old-agp-tests_tests \
--jvmopt="-DUPDATE_TEST_SNAPSHOTS=$(bazel info workspace)" --test_output=streamed
 */
class GradleSyncProjectComparisonOldAgpTest: GradleSyncProjectComparisonTest() {
  fun testSimpleApplicationWithAgp3_3_2() {
    val text = importSyncAndDumpProject(
      projectDir = TestProjectPaths.SIMPLE_APPLICATION,
      patch = {
        AndroidGradleTests.defaultPatchPreparedProject(it, "5.5", "3.3.2", null)
      }
    )
    assertIsEqualToSnapshot(text)
  }
}
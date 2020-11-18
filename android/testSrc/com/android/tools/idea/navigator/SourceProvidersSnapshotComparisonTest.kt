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
package com.android.tools.idea.navigator

import com.android.testutils.TestUtils
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.dumpSourceProviders
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.annotations.SystemIndependent
import java.io.File

/**
 * Snapshot tests for 'Source Providers'.
 *
 * The pre-recorded sync results can be found in testData/sourceProvidersSnapshots/ *.txt files.
 *
 * NOTE: It you made changes to sync or the test projects which make these tests fail in an expected way, you can re-run the tests
 *       from IDE with -DUPDATE_TEST_SNAPSHOTS to update the files.
 *
 *       Or with bazel:
bazel test //tools/adt/idea/android:intellij.android.core.tests_tests  --test_sharding_strategy=disabled  \
--test_filter="SourceProvidersSnapshotComparisonTest" --nocache_test_results --strategy=TestRunner=standalone \
--jvmopt='-DUPDATE_TEST_SNAPSHOTS' --test_output=streamed
 */
class SourceProvidersSnapshotComparisonTest : AndroidGradleTestCase(), SnapshotComparisonTest {
  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/sourceProviders"
  override fun getTestDataDirectoryWorkspaceRelativePath(): @SystemIndependent String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos() =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectToSnapshotPaths.PSD_SAMPLE_REPO)))


  fun testSimpleApplication() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION)
    assertIsEqualToSnapshot(text)
  }

  fun testWithMlModels() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.APP_WITH_ML_MODELS)
    assertIsEqualToSnapshot(text)
  }

  fun testMultiFlavor() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.MULTI_FLAVOR)
    assertIsEqualToSnapshot(text)
  }

  fun testNestedProjects() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.PSD_SAMPLE_GROOVY)
    assertIsEqualToSnapshot(text)
  }

  fun testCompositeBuild() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.COMPOSITE_BUILD)
    assertIsEqualToSnapshot(text)
  }

  fun testWithBuildSrc() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.APP_WITH_BUILDSRC)
    assertIsEqualToSnapshot(text)
  }

  fun testJpsWithQualifiedNames() {
    val srcPath = File(myFixture.testDataPath, toSystemDependentName(TestProjectToSnapshotPaths.JPS_WITH_QUALIFIED_NAMES))
    // Prepare project in a different directory (_jps) to avoid closing the currently opened project.
    val projectPath = File(toSystemDependentName(project.basePath + "_jps"))

    AndroidGradleTests.prepareProjectForImportCore(srcPath, projectPath) { projectRoot ->
      // Override settings just for tests (e.g. sdk.dir)
      AndroidGradleTests.updateLocalProperties(projectRoot, TestUtils.getSdk().toFile())
    }

    val project = PlatformTestUtil.loadAndOpenProject(projectPath.toPath(), testRootDisposable)
    val text = project.dumpSourceProviders()
    PlatformTestUtil.forceCloseProjectWithoutSaving(project)

    assertIsEqualToSnapshot(text)
  }

  fun testCompatibilityWithAndroidStudio36Project() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.COMPATIBILITY_TESTS_AS_36)
    assertIsEqualToSnapshot(text)
  }

  fun testCompatibilityWithAndroidStudio36NoImlProject() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.COMPATIBILITY_TESTS_AS_36_NO_IML)
    assertIsEqualToSnapshot(text)
  }

  private fun importSyncAndDumpProject(
    projectDir: String,
    patch: ((projectRootPath: File) -> Unit)? = null
  ): String {
    val projectRootPath = prepareProjectForImport(projectDir)
    patch?.invoke(projectRootPath)
    importProject()

    return project.dumpSourceProviders()
  }
}

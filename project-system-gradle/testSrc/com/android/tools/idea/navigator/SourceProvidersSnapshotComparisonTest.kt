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
import com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTestDef
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.SnapshotContext
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.dumpSourceProviders
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.annotations.SystemIndependent
import java.io.File

/**
 * Snapshot test definitions for 'Source Providers'. (To run tests see
 * [com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTest])
 *
 * The pre-recorded sync results can be found in testData/sourceProvidersSnapshots/ *.txt files.
 *
 * For instructions on how to update the snapshot files see [SnapshotComparisonTest] and if running from the command-line use
 * target as "//tools/adt/idea/android:intellij.android.core.tests_tests ---test_filter=SourceProvidersSnapshotComparisonTest".
 */

data class SourceProvidersTestDef(
  override val testProject: TestProject,
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AGP_CURRENT,
) : SyncedProjectTestDef {
  override val name: String = testProject.projectName

  override fun toString(): String = testProject.projectName

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): SyncedProjectTestDef {
    return copy(agpVersion = agpVersion)
  }

  override fun isCompatible(): Boolean {
    return agpVersion == AGP_CURRENT
  }

  override fun runTest(root: File, project: Project) {
    val text = project.dumpSourceProviders()
    val snapshotContext = SnapshotContext(testProject.projectName, agpVersion, "tools/adt/idea/android/testData/snapshots/sourceProviders")
    snapshotContext.assertIsEqualToSnapshot(text)
  }

  companion object {
    val tests: List<SourceProvidersTestDef> = listOf(
      SourceProvidersTestDef(TestProject.SIMPLE_APPLICATION),
      SourceProvidersTestDef(TestProject.SIMPLE_APPLICATION_VIA_SYMLINK),
      SourceProvidersTestDef(TestProject.SIMPLE_APPLICATION_APP_VIA_SYMLINK),
      SourceProvidersTestDef(TestProject.SIMPLE_APPLICATION_NOT_AT_ROOT),
      SourceProvidersTestDef(TestProject.SIMPLE_APPLICATION_MULTIPLE_ROOTS),
      SourceProvidersTestDef(TestProject.APP_WITH_ML_MODELS),
      SourceProvidersTestDef(TestProject.MULTI_FLAVOR),
      SourceProvidersTestDef(TestProject.PSD_SAMPLE_GROOVY),
      SourceProvidersTestDef(TestProject.COMPOSITE_BUILD),
      SourceProvidersTestDef(TestProject.APP_WITH_BUILDSRC),
      SourceProvidersTestDef(TestProject.COMPATIBILITY_TESTS_AS_36),
      SourceProvidersTestDef(TestProject.COMPATIBILITY_TESTS_AS_36_NO_IML),
      SourceProvidersTestDef(TestProject.TEST_FIXTURES),
      SourceProvidersTestDef(TestProject.KOTLIN_KAPT),
      SourceProvidersTestDef(TestProject.KOTLIN_MULTIPLATFORM),
      SourceProvidersTestDef(TestProject.NAVIGATOR_PACKAGEVIEW_COMMONROOTS),
      SourceProvidersTestDef(TestProject.NAVIGATOR_PACKAGEVIEW_SIMPLE),
    )
  }
}

class SourceProvidersSnapshotComparisonTest : AndroidGradleTestCase(), SnapshotComparisonTest {
  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/sourceProviders"
  override fun getTestDataDirectoryWorkspaceRelativePath(): @SystemIndependent String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos() =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectToSnapshotPaths.PSD_SAMPLE_REPO)))

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
}

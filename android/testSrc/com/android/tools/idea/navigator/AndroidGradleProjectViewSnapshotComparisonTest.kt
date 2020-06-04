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
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.AndroidGradleTestCase.invokeGradle
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.ProjectViewSettings
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.dumpAndroidProjectView
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.annotations.SystemIndependent
import java.io.File

class AndroidGradleProjectViewSnapshotComparisonTest : AndroidGradleTestCase(), GradleIntegrationTest, SnapshotComparisonTest {
  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/projectViews"
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

  fun testMultiFlavor_flattenPackages() {
    val text = importSyncAndDumpProject(
      TestProjectToSnapshotPaths.MULTI_FLAVOR,
      projectViewSettings = ProjectViewSettings(hideEmptyPackages = true, flattenPackages = true)
    )
    assertIsEqualToSnapshot(text)
  }

  fun testNestedProjects() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.PSD_SAMPLE_GROOVY)
    assertIsEqualToSnapshot(text)
  }

  fun testNavigatorPackageViewCommonRoots_compact() {
    val text = importSyncAndDumpProject(
      TestProjectToSnapshotPaths.NAVIGATOR_PACKAGEVIEW_COMMONROOTS,
      projectViewSettings = ProjectViewSettings(hideEmptyPackages = true)
    )
    assertIsEqualToSnapshot(text)
  }

  fun testNavigatorPackageViewCommonRoots_notCompact() {
    val text = importSyncAndDumpProject(
      TestProjectToSnapshotPaths.NAVIGATOR_PACKAGEVIEW_COMMONROOTS,
      projectViewSettings = ProjectViewSettings(hideEmptyPackages = false)
    )
    assertIsEqualToSnapshot(text)
  }

  fun testNavigatorPackageViewSimple() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.NAVIGATOR_PACKAGEVIEW_SIMPLE)
    assertIsEqualToSnapshot(text)
  }

  fun testCompositeBuild() {
    val text = importSyncAndDumpProject(TestProjectToSnapshotPaths.COMPOSITE_BUILD)
    assertIsEqualToSnapshot(text)
  }

  fun testKotlinKapt() {
    prepareProjectForImport(TestProjectToSnapshotPaths.KOTLIN_KAPT)
    importProject()
    invokeGradle(project, GradleBuildInvoker::rebuild)
    AndroidTestBase.refreshProjectFiles()
    val text = project.dumpAndroidProjectView(ProjectViewSettings(), Unit, { _, _ -> Unit })
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
      AndroidGradleTests.updateLocalProperties(projectRoot, TestUtils.getSdk())
    }

    val project = ProjectUtil.openProject(projectPath.absolutePath, null, false)!!
    val text = project.dumpAndroidProjectView()
    ProjectUtil.closeAndDispose(project)

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

  fun testMissingImlIsIgnored() {
    prepareGradleProject(TestProjectToSnapshotPaths.SIMPLE_APPLICATION_CORRUPTED_MISSING_IML_40, "testMissingImlIsIgnored_Test")
    val text = openPreparedProject("testMissingImlIsIgnored_Test") { project: Project ->
      project.dumpAndroidProjectView()
    }

    assertIsEqualToSnapshot(text)
  }

  private fun importSyncAndDumpProject(
    projectDir: String,
    patch: ((projectRootPath: File) -> Unit)? = null,
    projectViewSettings: ProjectViewSettings = ProjectViewSettings()
  ): String =
    importSyncAndDumpProject(projectDir, patch, projectViewSettings, Unit, { _, _ -> Unit })

  private fun <T : Any> importSyncAndDumpProject(
    projectDir: String,
    patch: ((projectRootPath: File) -> Unit)? = null,
    projectViewSettings: ProjectViewSettings = ProjectViewSettings(),
    initialState: T,
    filter: (element: AbstractTreeNode<*>, state: T) -> T?
  ): String {
    val projectRootPath = prepareProjectForImport(projectDir)
    patch?.invoke(projectRootPath)
    importProject()

    return project.dumpAndroidProjectView(projectViewSettings, initialState, filter)
  }
}

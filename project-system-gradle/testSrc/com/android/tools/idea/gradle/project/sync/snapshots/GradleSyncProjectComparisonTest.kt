/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.snapshots

import com.android.SdkConstants.FN_SETTINGS_GRADLE
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.FileSubject.file
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.API_DEPENDENCY
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.APP_WITH_BUILDSRC
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.APP_WITH_ML_MODELS
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.BASIC
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.CENTRAL_BUILD_DIRECTORY
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.COMPATIBILITY_TESTS_AS_36
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.COMPATIBILITY_TESTS_AS_36_NO_IML
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.COMPOSITE_BUILD
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.KOTLIN_GRADLE_DSL
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.KOTLIN_KAPT
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.KOTLIN_MULTIPLATFORM
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.MULTI_FLAVOR
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.NESTED_MODULE
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.NEW_SYNC_KOTLIN_TEST
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SETS
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.PSD_DEPENDENCY
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.PSD_SAMPLE_GROOVY
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.PSD_SAMPLE_REPO
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.PURE_JAVA_PROJECT
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.TEST_FIXTURES
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.TEST_ONLY_MODULE
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.TRANSITIVE_DEPENDENCIES
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.TWO_JARS
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.WITH_GRADLE_METADATA
import com.android.tools.idea.testing.assertAreEqualToSnapshots
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.tools.idea.testing.saveAndDump
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.WriteAction.run
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.join
import com.intellij.openapi.util.io.FileUtil.writeToFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.PathUtil.toSystemDependentName
import com.intellij.util.indexing.IndexableSetContributor
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.AndroidTestBase.refreshProjectFiles
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.kotlin.idea.core.script.dependencies.KotlinScriptDependenciesIndexableSetContributor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * Snapshot tests for 'Gradle Sync'.
 *
 * These tests compare the results of sync by converting the resulting project to a stable text format which does not depend on local
 * environment (and ideally should not depend on the versions of irrelevant libraries) and comparing them to pre-recorded golden sync
 * results.
 *
 * The pre-recorded sync results can be found in testData/syncedProjectSnapshots/ *.txt files. Consult [snapshotSuffixes] for more
 * details on the way in which the file names are constructed.
 *
 * For instructions on how to update the snapshot files see [SnapshotComparisonTest] and if running from the command-line use
 * target as "//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.project.sync.snapshots".
 */
@RunsInEdt
open class GradleSyncProjectComparisonTest : GradleIntegrationTest, SnapshotComparisonTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  val testName = TestName()

  override fun getName(): String = testName.methodName

  protected val projectName: String get() = "p/${getName()}"

  @Before
  fun before() {
    // NOTE: We do not re-register the extensions since (1) we do not know whether we removed it and (2) there is no simple way to
    //       re-register it by its class name. It means that this test might affect tests running after this one.

    // [KotlinScriptDependenciesIndexableSetContributor] contributes a lot of classes/sources to index in order to provide Ctrl+Space
    // experience in the code editor. It takes approximately 4 minutes to complete. We unregister the contributor to make our tests
    // run faster.
    IndexableSetContributor.EP_NAME.point.unregisterExtension(KotlinScriptDependenciesIndexableSetContributor::class.java)
  }

  @RunWith(JUnit4::class)
  @RunsInEdt
  class GradleSyncProjectComparisonTestCase : GradleSyncProjectComparisonTest() {
    // https://code.google.com/p/android/issues/detail?id=233038
    @Test
    fun testLoadPlainJavaProject() {
      val text = importSyncAndDumpProject(PURE_JAVA_PROJECT)
      assertIsEqualToSnapshot(text)
    }

    // See https://code.google.com/p/android/issues/detail?id=226802
    @Test
    fun testNestedModule() {
      val text = importSyncAndDumpProject(NESTED_MODULE)
      assertIsEqualToSnapshot(text)
    }

    // See https://code.google.com/p/android/issues/detail?id=76444
    @Test
    fun testWithEmptyGradleSettingsFileInSingleModuleProject() {
      val text = importSyncAndDumpProject(
        projectDir = BASIC,
        patch = { projectRootPath -> createEmptyGradleSettingsFile(projectRootPath) }
      ) { it.saveAndDump() }
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testTransitiveDependencies() {
      // TODO(b/124505053): Remove almost identical snapshots when SDK naming is fixed.
      val text = importSyncAndDumpProject(TRANSITIVE_DEPENDENCIES)
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testSimpleApplication() {
      val text = importSyncAndDumpProject(SIMPLE_APPLICATION)
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testWithGradleMetadata() {
      val text = importSyncAndDumpProject(WITH_GRADLE_METADATA)
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testTestFixtures() {
      val text = importSyncAndDumpProject(TEST_FIXTURES)
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testTestOnlyModule() {
      val text = importSyncAndDumpProject(TEST_ONLY_MODULE)
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testWithMlModels() {
      val text = importSyncAndDumpProject(APP_WITH_ML_MODELS)
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testMultiFlavor() {
      val text = importSyncAndDumpProject(MULTI_FLAVOR)
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testExternalSourceSets() {
      val projectRootPath = prepareGradleProject(NON_STANDARD_SOURCE_SETS, "project")
      openPreparedProject("project/application") { project ->
        val text = project.saveAndDump(
          mapOf("EXTERNAL_SOURCE_SET" to File(projectRootPath, "externalRoot"),
                "EXTERNAL_MANIFEST" to File(projectRootPath, "externalManifest"))
        )
        assertIsEqualToSnapshot(text)
      }
    }

    // See https://code.google.com/p/android/issues/detail?id=74259
    @Test
    fun testWithCentralBuildDirectoryInRootModuleDeleted() {
      val text = importSyncAndDumpProject(CENTRAL_BUILD_DIRECTORY, { projectRootPath ->
        // The bug appears only when the central build folder does not exist.
        val centralBuildDirPath = File(projectRootPath, join("central", "build"))
        val centralBuildParentDirPath = centralBuildDirPath.parentFile
        FileUtil.delete(centralBuildParentDirPath)
      }) { it.saveAndDump() }
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testSyncWithKotlinDsl() {
      val text = importSyncAndDumpProject(KOTLIN_GRADLE_DSL)
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testSyncKotlinProject() {
      // TODO(b/125321223): Remove suffixes from the snapshot files when fixed.
      val text = importSyncAndDumpProject(NEW_SYNC_KOTLIN_TEST)
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testPsdDependencyDeleteModule() {
      importSyncAndDumpProject(PSD_DEPENDENCY) { project ->
        val beforeDelete = project.saveAndDump()
        PsProjectImpl(project).let { projectModel ->
          projectModel.removeModule(":moduleB")
          projectModel.applyChanges()
        }
        val textAfterDelete = project.syncAndDumpProject()
        // TODO(b/124497021): Remove duplicate dependencies from the snapshot by reverting to the main snapshot when the bug is fixed.
        assertAreEqualToSnapshots(
          beforeDelete to ".before_delete",
          textAfterDelete to ".after_moduleb_deleted"
        )
      }
    }

    @Test
    fun testPsdDependencyAndroidToJavaModuleAndBack() {
      importSyncAndDumpProject(PSD_DEPENDENCY) { project ->
        val beforeAndroidToJava = project.saveAndDump()
        val oldModuleCContent = WriteAction.compute<ByteArray, Throwable> {
          val jModuleMFile = project.guessProjectDir()?.findFileByRelativePath("jModuleM/build.gradle")!!
          val moduleCFile = project.guessProjectDir()?.findFileByRelativePath("moduleC/build.gradle")!!
          val moduleCContent = moduleCFile.contentsToByteArray()
          val jModuleMContent = jModuleMFile.contentsToByteArray()
          moduleCFile.setBinaryContent(jModuleMContent)
          moduleCContent
        }
        ApplicationManager.getApplication().saveAll()
        val afterAndroidToJava = project.syncAndDumpProject()
        // TODO(b/124497021): Remove duplicate dependencies from the snapshot by reverting to the main snapshot when the bug is fixed.

        run<Throwable> {
          val moduleCFile = project.guessProjectDir()?.findFileByRelativePath("moduleC/build.gradle")!!
          moduleCFile.setBinaryContent(oldModuleCContent)
        }
        ApplicationManager.getApplication().saveAll()
        val textAfterSecondChange = project.syncAndDumpProject()
        // TODO(b/124497021): Remove duplicate dependencies from the snapshot by reverting to the main snapshot when the bug is fixed.
        assertAreEqualToSnapshots(
          beforeAndroidToJava to ".before_android_to_java",
          afterAndroidToJava to ".after_android_to_java",
          textAfterSecondChange to ".after_java_to_android"
        )
      }
    }

    @Test
    fun testPsdSample() {
      val text = importSyncAndDumpProject(PSD_SAMPLE_GROOVY)
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testTwoJarsWithTheSameName() {
      val text = importSyncAndDumpProject(TWO_JARS)
      // TODO(b/125680482): Update the snapshot when the bug is fixed.
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testWithCompositeBuild() {
      // TODO(b/169230806): Dependencies on nested included builds are broken.
      // uncomment link in main builds apps build.gradle to test.
      val text = importSyncAndDumpProject(COMPOSITE_BUILD)
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testWithBuildSrc() {
      val text = importSyncAndDumpProject(APP_WITH_BUILDSRC)
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testKmp() {
      val text = importSyncAndDumpProject(KOTLIN_MULTIPLATFORM)
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testKapt() {
      val text = importSyncAndDumpProject(KOTLIN_KAPT)
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testCompatibilityWithAndroidStudio36Project() {
      withJdkNamed18 {
        val text = importSyncAndDumpProject(COMPATIBILITY_TESTS_AS_36)
        assertIsEqualToSnapshot(text)
      }
    }

    @Test
    fun testCompatibilityWithAndroidStudio36NoImlProject() {
      withJdkNamed18 {
        val text = importSyncAndDumpProject(COMPATIBILITY_TESTS_AS_36_NO_IML)
        assertIsEqualToSnapshot(text)
      }
    }

    @Test
    fun testApiDependency() {
      val text = importSyncAndDumpProject(projectDir = API_DEPENDENCY)
      assertIsEqualToSnapshot(text)
    }
  }

  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/syncedProjects"
  override val snapshotSuffixes = listOfNotNull(
    // Suffixes to use to override the default expected result.
    ".single_variant", // TODO(b/168452472): Rename snapshots and remove.
    ""
  )

  override fun getTestDataDirectoryWorkspaceRelativePath(): @SystemIndependent String = "tools/adt/idea/android/testData/snapshots"

  protected fun <T> importSyncAndDumpProject(
    projectDir: String,
    patch: ((projectRootPath: File) -> Unit)? = null,
    body: (Project) -> T
  ): T {
    val projectRootPath = prepareGradleProject(projectDir, projectName)
    patch?.invoke(projectRootPath)
    return openPreparedProject(projectName) { project ->
      waitForSourceFolderManagerToProcessUpdates(project)
      body(project)
    }
  }

  protected fun importSyncAndDumpProject(projectDir: String): String =
    importSyncAndDumpProject(projectDir) { it.saveAndDump() }

  protected fun Project.syncAndDumpProject(): String {
    requestSyncAndWait()
    waitForSourceFolderManagerToProcessUpdates(this)

    return this.saveAndDump()
  }

  protected fun <T> withJdkNamed18(body: () -> T): T {
    val newJdk = if (ProjectJdkTable.getInstance().findJdk("1.8") == null) {
      val anyJdk = IdeSdks.getInstance().jdk!!
      val newJdk = SdkConfigurationUtil.setupSdk(ProjectJdkTable.getInstance().allJdks, anyJdk.homeDirectory!!, JavaSdk.getInstance(),
                                                 true, null, "1.8")!!
      ApplicationManager.getApplication().runWriteAction { ProjectJdkTable.getInstance().addJdk(newJdk) }
      newJdk
    }
    else {
      null
    }
    try {
      return body()
    }
    finally {
      if (newJdk != null) {
        ApplicationManager.getApplication().runWriteAction { ProjectJdkTable.getInstance().removeJdk(newJdk) }
      }
    }
  }

  protected fun createEmptyGradleSettingsFile(projectRootPath: File) {
    val settingsFilePath = File(projectRootPath, FN_SETTINGS_GRADLE)
    assertThat(FileUtil.delete(settingsFilePath)).isTrue()
    writeToFile(settingsFilePath, " ")
    assertAbout(file()).that(settingsFilePath).isFile()
    refreshProjectFiles()
  }

  override fun getAdditionalRepos() =
    listOf(File(AndroidTestBase.getTestDataPath(), toSystemDependentName(PSD_SAMPLE_REPO)))

  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
}

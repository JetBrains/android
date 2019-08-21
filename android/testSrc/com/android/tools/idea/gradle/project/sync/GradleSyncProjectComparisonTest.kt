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

import com.android.SdkConstants.FN_SETTINGS_GRADLE
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.variant.view.BuildVariantUpdater
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.FileSubject
import com.android.tools.idea.testing.FileSubject.file
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.TestProjectPaths.BASIC
import com.android.tools.idea.testing.TestProjectPaths.CENTRAL_BUILD_DIRECTORY
import com.android.tools.idea.testing.TestProjectPaths.HELLO_JNI
import com.android.tools.idea.testing.TestProjectPaths.KOTLIN_GRADLE_DSL
import com.android.tools.idea.testing.TestProjectPaths.NESTED_MODULE
import com.android.tools.idea.testing.TestProjectPaths.NEW_SYNC_KOTLIN_TEST
import com.android.tools.idea.testing.TestProjectPaths.PSD_DEPENDENCY
import com.android.tools.idea.testing.TestProjectPaths.PSD_SAMPLE
import com.android.tools.idea.testing.TestProjectPaths.PURE_JAVA_PROJECT
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.TestProjectPaths.TRANSITIVE_DEPENDENCIES
import com.android.tools.idea.testing.TestProjectPaths.TWO_JARS
import com.android.tools.idea.testing.TestProjectPaths.VARIANT_SPECIFIC_DEPENDENCIES
import com.android.tools.idea.testing.assertAreEqualToSnapshots
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.saveAndDump
import com.google.common.truth.Truth.assertAbout
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.WriteAction.run
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtil.delete
import com.intellij.openapi.util.io.FileUtil.join
import com.intellij.openapi.util.io.FileUtil.writeToFile
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.plugins.gradle.settings.DistributionType.DEFAULT_WRAPPED
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
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
 * NOTE: It you made changes to sync or the test projects which make these tests fail in an expected way, you can re-run the tests
 *       from IDE with -DUPDATE_TEST_SNAPSHOTS to update the files. (You may need to re-run several times (currently up to 3) to
 *       update multiple snapshots used in one test.
 *
 *       Or with bazel:
         bazel test //tools/adt/idea/android:intellij.android.core.tests_tests  --test_sharding_strategy=disabled  \
               --test_filter="GradleSyncProjectComparisonTest" --nocache_test_results --strategy=TestRunner=standalone \
               --jvmopt='-DUPDATE_TEST_SNAPSHOTS' --test_output=streamed --runs_per_test=3
 */
abstract class GradleSyncProjectComparisonTest(
    private val singleVariantSync: Boolean = false
) : GradleSyncIntegrationTestCase(), SnapshotComparisonTest {
  override fun useSingleVariantSyncInfrastructure(): Boolean = singleVariantSync
  override fun useCompoundSyncInfrastructure(): Boolean = false

  class FullVariantGradleSyncProjectComparisonTest : GradleSyncProjectComparisonTest()

  class SingleVariantGradleSyncProjectComparisonTest :
      GradleSyncProjectComparisonTest(singleVariantSync = true) {
    /** TODO(b/124504437): Enable this test */
    override fun testNdkProjectSync() = Unit
  }

  override val snapshotDirectoryName = "syncedProjectSnapshots"
  override val snapshotSuffixes = listOfNotNull(
    // Suffixes to use to override the default expected result.
    ".single_variant".takeIf { singleVariantSync },
    ""
  )

  private lateinit var ideComponents: IdeComponents

  private fun importSyncAndDumpProject(projectDir: String, patch: ((projectRootPath: File) -> Unit)? = null): String {
    val projectRootPath = prepareProjectForImport(projectDir)
    patch?.invoke(projectRootPath)
    AndroidGradleTests.importProject(project, GradleSyncInvoker.Request.testRequest())
    return project.saveAndDump()
  }

  private fun syncAndDumpProject(): String {
    requestSyncAndWait()
    return project.saveAndDump()
  }

  override fun setUp() {
    super.setUp()
    val project = project
    ideComponents = IdeComponents(project)
    val projectSettings = GradleProjectSettings()
    projectSettings.distributionType = DEFAULT_WRAPPED
    GradleSettings.getInstance(project).linkedProjectsSettings = listOf(projectSettings)
  }

  fun testImportNoSync() {
    val projectRootPath = prepareProjectForImport(SIMPLE_APPLICATION)
    val request = GradleProjectImporter.Request(project)
    GradleProjectImporter.getInstance().importProjectNoSync(request)
    AndroidTestBase.refreshProjectFiles()
    val text = project.saveAndDump()
    assertIsEqualToSnapshot(text)
  }

  // https://code.google.com/p/android/issues/detail?id=233038
  open fun testLoadPlainJavaProject() {
    val text = importSyncAndDumpProject(PURE_JAVA_PROJECT)
/* b/137231583
    assertIsEqualToSnapshot(text)
b/137231583 */
  }

  // See https://code.google.com/p/android/issues/detail?id=226802
  fun testNestedModule() {
    val text = importSyncAndDumpProject(NESTED_MODULE)
    assertIsEqualToSnapshot(text)
  }

  // See https://code.google.com/p/android/issues/detail?id=224985
  open fun testNdkProjectSync() {
    val text = importSyncAndDumpProject(HELLO_JNI)
    assertIsEqualToSnapshot(text)
  }

  // See https://code.google.com/p/android/issues/detail?id=76444
  fun testWithEmptyGradleSettingsFileInSingleModuleProject() {
    val text = importSyncAndDumpProject(BASIC) { createEmptyGradleSettingsFile() }
    assertIsEqualToSnapshot(text)
  }

  fun testTransitiveDependencies() {
    // TODO(b/124505053): Remove almost identical snapshots when SDK naming is fixed.
    val text = importSyncAndDumpProject(TRANSITIVE_DEPENDENCIES)
    assertIsEqualToSnapshot(text)
  }

  fun testSimpleApplication() {
    val text = importSyncAndDumpProject(SIMPLE_APPLICATION)
    assertIsEqualToSnapshot(text)
  }

  fun testSimpleApplicationWithAgp3_3_2() {
    val text = importSyncAndDumpProject(SIMPLE_APPLICATION) {
      val buildFile = it.resolve("build.gradle")
      buildFile.writeText(
        buildFile.readText()
          .replace(
            Regex("classpath ['\"]com.android.tools.build:gradle:(.+)['\"]"),
            "classpath 'com.android.tools.build:gradle:3.3.2'"))
    }
    assertIsEqualToSnapshot(text)
  }

  // See https://code.google.com/p/android/issues/detail?id=74259
  fun testWithCentralBuildDirectoryInRootModuleDeleted() {
    val text = importSyncAndDumpProject(CENTRAL_BUILD_DIRECTORY) { projectRootPath ->
      // The bug appears only when the central build folder does not exist.
      val centralBuildDirPath = File(projectRootPath, join("central", "build"))
      val centralBuildParentDirPath = centralBuildDirPath.parentFile
      delete(centralBuildParentDirPath)
    }
    assertIsEqualToSnapshot(text)
  }

  fun testSyncWithKotlinDsl() {
    val text = importSyncAndDumpProject(KOTLIN_GRADLE_DSL)
    assertIsEqualToSnapshot(text)
  }

  fun testSyncKotlinProject() {
    // TODO(b/125321223): Remove suffixes from the snapshot files when fixed.
    val text = importSyncAndDumpProject(NEW_SYNC_KOTLIN_TEST)
    assertIsEqualToSnapshot(text)
  }

  open fun testPsdDependency() {
    val firstSync = importSyncAndDumpProject(PSD_DEPENDENCY) { projectRoot -> addPsdSampleRepo(projectRoot) }
    val secondSync = syncAndDumpProject()
    // TODO(b/124677413): When fixed, [secondSync] should match the same snapshot. (Remove ".second_sync")
    assertAreEqualToSnapshots(
      firstSync to "",
      secondSync to ".second_sync"
    )
  }

  open fun testPsdDependencyDeleteModule() {
    val beforeDelete = importSyncAndDumpProject(PSD_DEPENDENCY) { projectRoot -> addPsdSampleRepo(projectRoot) }
    PsProjectImpl(project).let { projectModel ->
      projectModel.removeModule(":moduleB")
      projectModel.applyChanges()
    }
    val textAfterDelete = syncAndDumpProject()
    // TODO(b/124497021): Remove duplicate dependencies from the snapshot by reverting to the main snapshot when the bug is fixed.
    assertAreEqualToSnapshots(
      beforeDelete to ".before_delete",
      textAfterDelete to ".after_moduleb_deleted"
    )
  }

  // TODO(b/128873247): Update snapshot files with the bug is fixed and Java-Gradle facet is removed.
  open fun testPsdDependencyAndroidToJavaModuleAndBack() {
    val beforeAndroidToJava = importSyncAndDumpProject(PSD_DEPENDENCY) { projectRoot ->
      val localRepositories = AndroidGradleTests.getLocalRepositoriesForGroovy()
      val testRepositoryPath =
          File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectPaths.PSD_SAMPLE_REPO)).absolutePath
      val repositories = """
      maven {
        name "test"
        url "file:$testRepositoryPath"
      }
      $localRepositories
      """
      AndroidGradleTests.updateGradleVersionsAndRepositories(projectRoot, repositories, null)
    }
    val oldModuleCContent = WriteAction.compute<ByteArray, Throwable> {
      val jModuleMFile = project.guessProjectDir()?.findFileByRelativePath("jModuleM/build.gradle")!!
      val moduleCFile = project.guessProjectDir()?.findFileByRelativePath("moduleC/build.gradle")!!
      val moduleCContent = moduleCFile.contentsToByteArray()
      val jModuleMContent = jModuleMFile.contentsToByteArray()
      moduleCFile.setBinaryContent(jModuleMContent)
      moduleCContent
    }
    ApplicationManager.getApplication().saveAll()
    val afterAndroidToJava = syncAndDumpProject()
    // TODO(b/124497021): Remove duplicate dependencies from the snapshot by reverting to the main snapshot when the bug is fixed.

    run<Throwable> {
      val moduleCFile = project.guessProjectDir()?.findFileByRelativePath("moduleC/build.gradle")!!
      moduleCFile.setBinaryContent(oldModuleCContent)
    }
    ApplicationManager.getApplication().saveAll()
    val textAfterSecondChange = syncAndDumpProject()
    // TODO(b/124497021): Remove duplicate dependencies from the snapshot by reverting to the main snapshot when the bug is fixed.
    assertAreEqualToSnapshots(
      beforeAndroidToJava to ".before_android_to_java",
      afterAndroidToJava to ".after_android_to_java",
      textAfterSecondChange to ".after_java_to_android"
    )
  }

  open fun testPsdSample() {
    val text = importSyncAndDumpProject(PSD_SAMPLE)
    assertIsEqualToSnapshot(text)
  }

  open fun testPsdSampleRenamingModule() {
    val beforeRename = importSyncAndDumpProject(PSD_SAMPLE)
    PsProjectImpl(project).let { projectModel ->
      projectModel.removeModule(":nested1")
      projectModel.removeModule(":nested1:deep")
      with(projectModel.parsedModel.projectSettingsModel!!) {
        addModulePath(":container1")
        addModulePath(":container1:deep")
      }
      projectModel.applyChanges()
    }
    run<Throwable> {
      project.guessProjectDir()!!.findFileByRelativePath("nested1")!!.rename("test", "container1")
    }
    ApplicationManager.getApplication().saveAll()
    val afterRename = syncAndDumpProject()
    assertAreEqualToSnapshots(
      beforeRename to "",
      afterRename to ".after_rename"
    )
  }

  open fun testPsdDependencyUpgradeLibraryModule() {
    val beforeLibUpgrade = importSyncAndDumpProject(PSD_DEPENDENCY) { projectRoot ->
      val localRepositories = AndroidGradleTests.getLocalRepositoriesForGroovy()
      val testRepositoryPath =
          File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectPaths.PSD_SAMPLE_REPO)).absolutePath
      val repositories = """
      maven {
        name "test"
        url "file:$testRepositoryPath"
      }
      $localRepositories
      """
      AndroidGradleTests.updateGradleVersionsAndRepositories(projectRoot, repositories, null)
    }
    PsProjectImpl(project).let { projectModel ->
      projectModel
          .findModuleByGradlePath(":modulePlus")!!
          .dependencies
          .findLibraryDependencies("com.example.libs", "lib1")
          .single().version = "1.0".asParsed()
      projectModel
          .findModuleByGradlePath(":mainModule")!!
          .dependencies
          .findLibraryDependencies("com.example.libs", "lib1")
          .forEach { it.version = "0.9.1".asParsed() }
      projectModel
          .findModuleByGradlePath(":mainModule")!!
          .dependencies
          .findLibraryDependencies("com.example.jlib", "lib3")
          .single().version = "0.9.1".asParsed()
      projectModel.applyChanges()
    }
    val afterLibUpgrade = syncAndDumpProject()
    // TODO(b/124677413): Remove irrelevant changes from the snapshot when the bug is fixed.
    assertAreEqualToSnapshots(
      beforeLibUpgrade to ".before_lib_upgrade",
      afterLibUpgrade to ".after_lib_upgrade"
    )
  }

  fun testTwoJarsWithTheSameName() {
    val text = importSyncAndDumpProject(TWO_JARS)
    // TODO(b/125680482): Update the snapshot when the bug is fixed.
    assertIsEqualToSnapshot(text)
  }

  fun testWithCompositeBuild() {
    val text = importSyncAndDumpProject(TestProjectPaths.COMPOSITE_BUILD)
    assertIsEqualToSnapshot(text)
  }

  fun testSwitchingVariants_simpleApplication() {
    val debugBefore = importSyncAndDumpProject(SIMPLE_APPLICATION)
    BuildVariantUpdater.getInstance(project).updateSelectedBuildVariant(project, "app", "release")
    val release = project.saveAndDump()
    BuildVariantUpdater.getInstance(project).updateSelectedBuildVariant(project, "app", "debug")
    val debugAfter = project.saveAndDump()
    assertAreEqualToSnapshots(
      debugBefore to ".debug",
      release to ".release",
      debugAfter to ".debug"
    )
  }

  // TODO(b/135453395): This test illustrates that variant switching does not remove dependencies.
  fun testSwitchingVariants_variantSpecificDependencies() {
    val freeDebugBefore = importSyncAndDumpProject(VARIANT_SPECIFIC_DEPENDENCIES)

    BuildVariantUpdater.getInstance(project).updateSelectedBuildVariant(project, "app", "paidDebug")
    val paidDebug = project.saveAndDump()

    BuildVariantUpdater.getInstance(project).updateSelectedBuildVariant(project, "app", "freeDebug")
    val freeDebugAfter = project.saveAndDump()

    assertAreEqualToSnapshots(
      freeDebugBefore to ".freeDebugBefore",
      paidDebug to ".paidDebug",
      freeDebugAfter to ".freeDebugAfter"
    )
  }

  private fun createEmptyGradleSettingsFile() {
    val settingsFilePath = File(projectFolderPath, FN_SETTINGS_GRADLE)
    assertTrue(delete(settingsFilePath))
    writeToFile(settingsFilePath, " ")
    assertAbout<FileSubject, File>(file()).that(settingsFilePath).isFile()
    refreshProjectFiles()
  }

  private fun addPsdSampleRepo(projectRoot: File) {
    val localRepositories = AndroidGradleTests.getLocalRepositoriesForGroovy()
    val testRepositoryPath =
      File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectPaths.PSD_SAMPLE_REPO)).absolutePath
    val repositories = """
        maven {
          name "test"
          url "file:$testRepositoryPath"
        }
        $localRepositories
        """
    AndroidGradleTests.updateGradleVersionsAndRepositories(projectRoot, repositories, null)
  }
}


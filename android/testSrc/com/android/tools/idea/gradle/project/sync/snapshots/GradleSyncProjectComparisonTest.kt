/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.FileSubject
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
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.MULTI_FLAVOR
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.NESTED_MODULE
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.NEW_SYNC_KOTLIN_TEST
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SETS
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.PSD_DEPENDENCY
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.PSD_SAMPLE_GROOVY
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.PSD_SAMPLE_REPO
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.PURE_JAVA_PROJECT
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.TRANSITIVE_DEPENDENCIES
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.TWO_JARS
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.VARIANT_SPECIFIC_DEPENDENCIES
import com.android.tools.idea.testing.assertAreEqualToSnapshots
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.fileUnderGradleRoot
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.tools.idea.testing.saveAndDump
import com.android.tools.idea.testing.switchVariant
import com.google.common.truth.Truth.assertAbout
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.WriteAction.run
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.join
import com.intellij.openapi.util.io.FileUtil.writeToFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.PathUtil.toSystemDependentName
import junit.framework.Assert.assertTrue
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.android.AndroidTestBase.refreshProjectFiles
import org.jetbrains.annotations.SystemIndependent
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
 * NOTE: It you made changes to sync or the test projects which make these tests fail in an expected way, you can re-run the tests
 *       from IDE with -DUPDATE_TEST_SNAPSHOTS to update the files.
 *
 *       Or with bazel:
```
bazel test \
--jvmopt="-DUPDATE_TEST_SNAPSHOTS=$(bazel info workspace)" \
--test_output=streamed \
--nocache_test_results \
--strategy=TestRunner=standalone \
//tools/adt/idea/android:intellij.android.core.tests_tests__gradle.project.sync.snapshots
```
 */
@RunsInEdt
open class GradleSyncProjectComparisonTest : GradleIntegrationTest, SnapshotComparisonTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  val testName = TestName()

  override fun getName(): String = testName.methodName

  protected val projectName: String get() = "p/${getName()}"

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
    fun testPsdDependency() {
      importSyncAndDumpProject(PSD_DEPENDENCY) { project ->
        val firstSync = project.saveAndDump()
        val secondSync = project.syncAndDumpProject()
        // TODO(b/124677413): When fixed, [secondSync] should match the same snapshot. (Remove ".second_sync")
        assertAreEqualToSnapshots(
          firstSync to "",
          secondSync to ".second_sync"
        )
      }
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

    // TODO(b/128873247): Update snapshot files with the bug is fixed and Java-Gradle facet is removed.
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
    fun testPsdSampleRenamingModule() {
      importSyncAndDumpProject(PSD_SAMPLE_GROOVY) { project ->
        val beforeRename = project.saveAndDump()
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
        val afterRename = project.syncAndDumpProject()
        assertAreEqualToSnapshots(
          beforeRename to "",
          afterRename to ".after_rename"
        )
      }
    }

    @Test
    fun testPsdDependencyUpgradeLibraryModule() {
      importSyncAndDumpProject(PSD_DEPENDENCY) { project ->
        val beforeLibUpgrade = project.saveAndDump()
        PsProjectImpl(project).let { projectModel ->
          projectModel
            .findModuleByGradlePath(":modulePlus")!!
            .dependencies
            .findLibraryDependencies("com.example.libs", "lib1")
            .forEach { it.version = "1.0".asParsed() }
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
        val afterLibUpgrade = project.syncAndDumpProject()
        // TODO(b/124677413): Remove irrelevant changes from the snapshot when the bug is fixed.
        assertAreEqualToSnapshots(
          beforeLibUpgrade to ".before_lib_upgrade",
          afterLibUpgrade to ".after_lib_upgrade"
        )
      }
    }

    @Test
    fun testTwoJarsWithTheSameName() {
      val text = importSyncAndDumpProject(TWO_JARS)
      // TODO(b/125680482): Update the snapshot when the bug is fixed.
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testWithCompositeBuild() {
      val text = importSyncAndDumpProject(COMPOSITE_BUILD)
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testWithBuildSrc() {
      val text = importSyncAndDumpProject(APP_WITH_BUILDSRC)
      assertIsEqualToSnapshot(text)
    }

    @Test
    fun testKapt() {
      importSyncAndDumpProject(KOTLIN_KAPT) { project ->
        val debugBefore = project.saveAndDump()
        switchVariant(project, ":app", "release")
        val release = project.saveAndDump()
        switchVariant(project, ":app", "debug")
        val debugAfter = project.saveAndDump()
        assertAreEqualToSnapshots(
          debugBefore to ".debug.before",
          release to ".release",
          debugAfter to ".debug.before"
        )
      }
    }

    @Test
    fun testSwitchingVariants_simpleApplication() {
      importSyncAndDumpProject(SIMPLE_APPLICATION) { project ->
        val debugBefore = project.saveAndDump()
        switchVariant(project, ":app", "release")
        val release = project.saveAndDump()
        switchVariant(project, ":app", "debug")
        val debugAfter = project.saveAndDump()
        assertAreEqualToSnapshots(
          debugBefore to ".debug",
          release to ".release",
          debugAfter to ".debug"
        )
      }
    }

    @Test
    fun testReimportSimpleApplication() {
      val root = prepareGradleProject(SIMPLE_APPLICATION, "project")
      val before = openPreparedProject("project") { project: Project ->
        project.saveAndDump()
      }
      FileUtil.delete(File(root, ".idea"))
      val after = openPreparedProject("project") { project ->
        project.saveAndDump()
      }
      assertAreEqualToSnapshots(
        before to ".same",
        after to ".same"
      )
    }

    @Test
    fun testReopenSimpleApplication() {
      val root = prepareGradleProject(SIMPLE_APPLICATION, "project")
      val before = openPreparedProject("project") { project: Project ->
        project.saveAndDump()
      }
      val after = openPreparedProject("project") { project ->
        project.saveAndDump()
      }
      assertAreEqualToSnapshots(
        before to ".same",
        after to ".same"
      )
    }

    @Test
    fun testSwitchingVariantsWithReopen_simpleApplication() {
      prepareGradleProject(SIMPLE_APPLICATION, "project")
      val debugBefore = openPreparedProject("project") { project: Project ->
        project.saveAndDump()
      }
      val release = openPreparedProject("project") { project ->
        switchVariant(project, ":app", "release")
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        project.saveAndDump()
      }
      val reopenedRelease = openPreparedProject("project") { project ->
        project.saveAndDump()
      }
      assertAreEqualToSnapshots(
        debugBefore to ".debug",
        release to ".release",
//        reopenedRelease to ".release" // TODO(b/178740252): Uncomment.
      )
    }

    @Test
    fun testSwitchingVariantsWithReopenAndResync_simpleApplication() {
      prepareGradleProject(SIMPLE_APPLICATION, "project")
      val debugBefore = openPreparedProject("project") { project: Project ->
        project.saveAndDump()
      }
      val release = openPreparedProject("project") { project ->
        switchVariant(project, ":app", "release")
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        runWriteAction {
          // Modify the project build file to ensure the project is synced when opened.
          project.gradleModule(":")!!.fileUnderGradleRoot("build.gradle")!!.also { file ->
            file.setBinaryContent((String(file.contentsToByteArray()) + " // ").toByteArray())
          }
        }
        project.saveAndDump()
      }
      val reopenedRelease = openPreparedProject("project") { project ->
        project.saveAndDump()
      }
      assertAreEqualToSnapshots(
        debugBefore to ".debug",
        release to ".release",
//        reopenedRelease to ".release" // TODO(b/178740252): Uncomment.
      )
    }

    @Test
    fun testSwitchingVariants_variantSpecificDependencies() {
      importSyncAndDumpProject(VARIANT_SPECIFIC_DEPENDENCIES) { project ->
        val freeDebugBefore = project.saveAndDump()
        switchVariant(project, ":app", "paidDebug")
        val paidDebug = project.saveAndDump()

        switchVariant(project, ":app", "freeDebug")
        val freeDebugAfter = project.saveAndDump()

        assertAreEqualToSnapshots(
          freeDebugBefore to ".freeDebug",
          paidDebug to ".paidDebug",
          freeDebugAfter to ".freeDebug"
        )
      }
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

  @Test
  fun testModulePerSourceSet() {
    StudioFlags.USE_MODULE_PER_SOURCE_SET.override(true)
    try {
      val text = importSyncAndDumpProject(PSD_SAMPLE_GROOVY)
      assertIsEqualToSnapshot(text)
    }
    finally {
      StudioFlags.USE_MODULE_PER_SOURCE_SET.clearOverride()
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
    return openPreparedProject(projectName) { project -> body(project) }
  }

  protected fun importSyncAndDumpProject(projectDir: String): String =
    importSyncAndDumpProject(projectDir) { it.saveAndDump() }

  protected fun Project.syncAndDumpProject(): String {
    requestSyncAndWait()
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
    assertTrue(FileUtil.delete(settingsFilePath))
    writeToFile(settingsFilePath, " ")
    assertAbout<FileSubject, File>(file()).that(settingsFilePath).isFile()
    refreshProjectFiles()
  }

  override fun getAdditionalRepos() =
    listOf(File(AndroidTestBase.getTestDataPath(), toSystemDependentName(PSD_SAMPLE_REPO)))

  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
}

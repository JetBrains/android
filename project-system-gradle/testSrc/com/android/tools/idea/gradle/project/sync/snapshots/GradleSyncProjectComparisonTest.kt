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
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.FileSubject.file
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.JavaModuleModelBuilder
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
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.LIGHT_SYNC_REFERENCE
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.MAIN_IN_ROOT
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.MULTI_FLAVOR
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.NESTED_MODULE
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.NEW_SYNC_KOTLIN_TEST
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SETS
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.NON_STANDARD_SOURCE_SET_DEPENDENCIES
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.PSD_SAMPLE_GROOVY
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.PSD_SAMPLE_REPO
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.PURE_JAVA_PROJECT
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.SIMPLE_APPLICATION
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.TEST_FIXTURES
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.TEST_ONLY_MODULE
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.TRANSITIVE_DEPENDENCIES
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.TWO_JARS
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.WITH_GRADLE_METADATA
import com.android.tools.idea.testing.assertIsEqualToSnapshot
import com.android.tools.idea.testing.getAndMaybeUpdateSnapshot
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.requestSyncAndWait
import com.android.tools.idea.testing.saveAndDump
import com.google.common.truth.Truth.assertAbout
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtil.join
import com.intellij.openapi.util.io.FileUtil.writeToFile
import com.intellij.testFramework.EdtRule
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
import org.junit.rules.RuleChain
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

    @Test
    fun testMainInRoot() {
      val text = importSyncAndDumpProject(MAIN_IN_ROOT)
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

    @Test
    fun testNonStandardSourceSetDependencies() {
      val text = importSyncAndDumpProject(NON_STANDARD_SOURCE_SET_DEPENDENCIES)
      assertIsEqualToSnapshot(text)
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
      val text = importSyncAndDumpProject(NEW_SYNC_KOTLIN_TEST)
      assertIsEqualToSnapshot(text)
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

    @Test
    fun testLightSyncReference() {
      val text = importSyncAndDumpProject(projectDir = LIGHT_SYNC_REFERENCE)
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

@RunsInEdt
class LightSyncReferenceTest : SnapshotComparisonTest, GradleIntegrationTest {
  @get:Rule
  var testName = TestName()

  val projectRule = AndroidProjectRule.withAndroidModels(
    prepareProjectSources = fun (root: File) {
      prepareGradleProject(resolveTestDataPath(LIGHT_SYNC_REFERENCE), root, {})
      root.resolve(".gradle").mkdir()
    },
    JavaModuleModelBuilder.rootModuleBuilder.copy(
      groupId = "",
      version = "unspecified",
    ),
    AndroidModuleModelBuilder(
      gradlePath = ":app",
      groupId = "reference",
      version = "unspecified",
      selectedBuildVariant = "debug",
      projectBuilder = AndroidProjectBuilder(androidModuleDependencyList = { listOf(AndroidModuleDependency(":androidlibrary", "debug")) }).build(),
    ),
    AndroidModuleModelBuilder(
      gradlePath = ":androidlibrary",
      groupId = "reference",
      version = "unspecified",
      selectedBuildVariant = "debug",
      projectBuilder = AndroidProjectBuilder(
        projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY },
        androidModuleDependencyList = { listOf(AndroidModuleDependency(":javalib", null)) }
      ).build()
    ),
    JavaModuleModelBuilder(
      ":javalib",
      groupId = "reference",
      version = "unspecified",
    )
  ).named("reference")

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  override fun getName(): String = testName.methodName
  override val snapshotDirectoryWorkspaceRelativePath: String = "tools/adt/idea/android/testData/snapshots/syncedProjects"
  override fun getTestDataDirectoryWorkspaceRelativePath(): @SystemIndependent String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos(): Collection<File> = emptyList()
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath

  @Test
  fun testLightSyncActual() {
    val dump = projectRule.project.saveAndDump()
    assertIsEqualToSnapshot(dump)
  }

  @Test
  fun compareResults() {
    val expectedSnapshot = object : SnapshotComparisonTest by this {
      override fun getName(): String = "testLightSyncReference"
    }
    val (_, expectedSnapshotTest) = expectedSnapshot.getAndMaybeUpdateSnapshot("", "", doNotUpdate = true)

    val actualSnapshot = object : SnapshotComparisonTest by this {
      override fun getName(): String = "testLightSyncActual"
    }
    val (_, actualSnapshotTest) = actualSnapshot.getAndMaybeUpdateSnapshot("", "", doNotUpdate = true)

    assertThat(actualSnapshotTest.filterOutProperties()).isEqualTo(expectedSnapshotTest.filterOutProperties())
  }
}
private fun String.filterOutProperties(): String =
  this
    .splitToSequence('\n')
    .nameProperties()
    .filter { (property, line) ->
      !PROPERTIES_TO_SKIP_BY_PREFIXES.any { property.startsWith(it) }
    }
    .map { it.first + " >> " + it.second }
    .joinToString(separator = "\n")

private fun Sequence<String>.nameProperties() = com.android.tools.idea.testing.nameProperties(this)

private val PROPERTIES_TO_SKIP_BY_PREFIXES = setOf(
  "PROJECT/BUILD_TASKS/TEST_COMPILE_MODE",
  "PROJECT/LIBRARY_TABLE",
  "PROJECT/MODULE/BUILD_TASKS/TEST_COMPILE_MODE",
  "PROJECT/MODULE/Classes",
  "PROJECT/MODULE/COMPILER_MODULE_EXTENSION",
  "PROJECT/MODULE/LIBRARY",
  "PROJECT/MODULE/TEST_MODULE_PROPERTIES",
  "PROJECT/RUN_CONFIGURATION"
)

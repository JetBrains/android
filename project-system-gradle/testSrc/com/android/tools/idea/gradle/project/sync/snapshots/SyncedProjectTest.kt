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

import com.android.SdkConstants
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.CapturePlatformModelsProjectResolverExtension
import com.android.tools.idea.navigator.AndroidProjectViewSnapshotComparisonTestDef
import com.android.tools.idea.navigator.SourceProvidersTestDef
import com.android.tools.idea.testing.AgpIntegrationTestDefinition
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT_V1
import com.android.tools.idea.testing.AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.ModelVersion
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.CoreIconManager
import com.intellij.ui.IconManager
import com.intellij.util.PathUtil
import com.intellij.util.indexing.IndexableSetContributor
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.kotlin.idea.core.script.dependencies.KotlinScriptDependenciesIndexableSetContributor
import org.junit.Assume
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runners.model.MultipleFailureException
import java.io.File
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.hasAnnotation

/**
 * An entry point to all tests asserting certain properties of synced projects.  See: [SyncedProjectTest.Companion.getTests] for the exact
 * list of assertions applied.
 *
 * This abstract test class allows to run tests in the IDE by running a specific test method below or by running a concrete class
 * representing an AGP version and/or other aspects of the environment.
 */
@RunsInEdt
abstract class SyncedProjectTest(
  val selfTest: Boolean = false,
  val agpVersion: AgpVersionSoftwareEnvironmentDescriptor
) : GradleIntegrationTest {

  interface TestDef : AgpIntegrationTestDefinition {
    val testProject: TestProject
    override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): TestDef
    fun runTest(root: File, project: Project)
  }

  companion object {
    val tests = (
      IdeModelSnapshotComparisonTestDefinition.tests() +
        SourceProvidersTestDef.tests +
        ProjectStructureSnapshotTestDef.tests +
        AndroidProjectViewSnapshotComparisonTestDef.tests
      ).groupBy { it.testProject }
  }

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos(): Collection<File> =
    listOf(File(AndroidTestBase.getTestDataPath(), PathUtil.toSystemDependentName(TestProjectToSnapshotPaths.PSD_SAMPLE_REPO)))

  @Test
  fun testSimpleApplication() = testProject(TestProject.SIMPLE_APPLICATION)

  @Test
  fun testAppWithMlModels() = testProject(TestProject.APP_WITH_ML_MODELS)

  @Test
  fun testAppWithBuildSrc() = testProject(TestProject.APP_WITH_BUILDSRC)

  @Test
  fun testCompatibilityAs36() = testProject(TestProject.COMPATIBILITY_TESTS_AS_36)

  @Test
  fun testCompatibilityAs36NoIml() = testProject(TestProject.COMPATIBILITY_TESTS_AS_36_NO_IML)

  @Test
  fun testSimpleApplicationWithAdditionalGradleSourceSets() = testProject(TestProject.SIMPLE_APPLICATION_WITH_ADDITIONAL_GRADLE_SOURCE_SETS)

  @Test
  fun testWithGradleMetadata() = testProject(TestProject.WITH_GRADLE_METADATA)

  @Test
  fun testBasicCmakeApp() = testProject(TestProject.BASIC_CMAKE_APP)

  @Test
  fun testPsdSampleGroovy() = testProject(TestProject.PSD_SAMPLE_GROOVY)

  @Test
  fun testCompositeBuild() = testProject(TestProject.COMPOSITE_BUILD)

  @Test
  fun testNonStandardSourceSets() = testProject(TestProject.NON_STANDARD_SOURCE_SETS)

  @Test
  fun testNonStandardSourceSetDependencies() = testProject(TestProject.NON_STANDARD_SOURCE_SET_DEPENDENCIES)

  @Test
  fun testLinked() = testProject(TestProject.LINKED)

  @Test
  fun testKotlinKapt() = testProject(TestProject.KOTLIN_KAPT)

  @Test
  fun testLintCustomChecks() = testProject(TestProject.LINT_CUSTOM_CHECKS)

  @Test
  fun testTestFixtures() = testProject(TestProject.TEST_FIXTURES)

  @Test
  fun testTestOnlyModule() = testProject(TestProject.TEST_ONLY_MODULE)

  @Test
  fun testKotlinMultiplatform() = testProject(TestProject.KOTLIN_MULTIPLATFORM)

  @Test
  fun testMultiFlavor() = testProject(TestProject.MULTI_FLAVOR)

  @Test
  fun testNamespaces() = testProject(TestProject.NAMESPACES)

  @Test
  fun testIncludeFromLib() = testProject(TestProject.INCLUDE_FROM_LIB)

  @Test
  fun testLocalAarsAsModules() = testProject(TestProject.LOCAL_AARS_AS_MODULES)

  @Test
  fun testBasic() = testProject(TestProject.BASIC)

  @Test
  fun testBasicWithEmptySettingsFile() = testProject(TestProject.BASIC_WITH_EMPTY_SETTINGS_FILE)

  @Test
  fun testMainInRoot() = testProject(TestProject.MAIN_IN_ROOT)

  @Test
  fun testNestedModule() = testProject(TestProject.NESTED_MODULE)

  @Test
  fun testTransitiveDependencies() = testProject(TestProject.TRANSITIVE_DEPENDENCIES)

  @Test
  fun testTransitiveDependenciesNoTargetSdkInLibs() = testProject(TestProject.TRANSITIVE_DEPENDENCIES_NO_TARGET_SDK_IN_LIBS)

  @Test
  fun testKotlinGradleDsl() = testProject(TestProject.KOTLIN_GRADLE_DSL)

  @Test
  fun testNewSyncKotlinTest() = testProject(TestProject.NEW_SYNC_KOTLIN_TEST)

  @Test
  fun testTwoJas() = testProject(TestProject.TWO_JARS)

  @Test
  fun testApiDependency() = testProject(TestProject.API_DEPENDENCY)

  @Test
  fun testNavigatorPackageViewCommonRoots() = testProject(TestProject.NAVIGATOR_PACKAGEVIEW_COMMONROOTS)

  @Test
  fun testNavigatorPackageViewSimple() = testProject(TestProject.NAVIGATOR_PACKAGEVIEW_SIMPLE)

  @Test
  fun testSimpleApplicationVersionCatalog() = testProject(TestProject.SIMPLE_APPLICATION_VERSION_CATALOG)

  @Test
  fun testCustomSourceType() = testProject(TestProject.CUSTOM_SOURCE_TYPE)

  @Test
  fun testLightSyncReference() = testProject(TestProject.LIGHT_SYNC_REFERENCE)

  @Test
  fun testPureJavaProject() = testProject(TestProject.PURE_JAVA_PROJECT)

  private fun testProject(testProject: TestProject) {
    if (selfTest) throw ReportUsedProjectException(testProject)

    val testDefinitions =
      tests[testProject].orEmpty()
        .map(::transformTest)
        .filter { it.isCompatible() }
        .groupBy { it.agpVersion }
    if (testDefinitions.keys.size > 1) error("Only one software environment is supposed to be tested at a time")
    val agpVersion = testDefinitions.keys.singleOrNull()
      ?: skipTest("No tests to run!")
    if (!testProject.isCompatibleWith(agpVersion)) skipTest("Project ${testProject.name} is incompatible with $agpVersion")
    val tests = testDefinitions.entries.singleOrNull()?.value.orEmpty()

    val root = prepareGradleProject(
      testProject.template,
      "project",
      agpVersion,
      ndkVersion = SdkConstants.NDK_DEFAULT_VERSION
    )
    testProject.patch(agpVersion, root)
    CapturePlatformModelsProjectResolverExtension.registerTestHelperProjectResolver(projectRule.fixture.testRootDisposable)
    if (agpVersion.modelVersion == ModelVersion.V1) {
      StudioFlags.GRADLE_SYNC_USE_V2_MODEL.override(false)
    }
    try {
      openPreparedProject("project${testProject.pathToOpen}") { project ->
        waitForSourceFolderManagerToProcessUpdates(project)
        val exceptions = tests.mapNotNull {
          println("${it::class.java.simpleName}(${testProject.projectName})\n    $root")
          kotlin.runCatching { it.runTest(root, project) }.exceptionOrNull()
        }
        when {
          exceptions.isEmpty() -> Unit
          exceptions.size == 1 -> throw exceptions.single()
          else -> throw MultipleFailureException(exceptions)
        }
      }
    } finally {
      StudioFlags.GRADLE_SYNC_USE_V2_MODEL.clearOverride()
    }
  }

  private fun transformTest(testProject: TestDef): TestDef {
    return testProject.withAgpVersion(agpVersion)
  }

  @Before
  fun before() {
    // NOTE: We do not re-register the extensions since (1) we do not know whether we removed it and (2) there is no simple way to
    //       re-register it by its class name. It means that this test might affect tests running after this one.

    // [KotlinScriptDependenciesIndexableSetContributor] contributes a lot of classes/sources to index in order to provide Ctrl+Space
    // experience in the code editor. It takes approximately 4 minutes to complete. We unregister the contributor to make our tests
    // run faster.
    IndexableSetContributor.EP_NAME.point.unregisterExtension(KotlinScriptDependenciesIndexableSetContributor::class.java)
  }

  init {
    // Avoid depending on the execution order and initializing icons with dummies.
    try {
      IconManager.activate(CoreIconManager())
    } catch (e: Throwable) {
      e.printStackTrace()
    }
  }
}

private fun skipTest(message: String): Nothing {
  Assume.assumeTrue(message, false)
  error(message)
}

class CurrentAgpV1 : SyncedProjectTest(agpVersion = AGP_CURRENT_V1)
class CurrentAgpV2 : SyncedProjectTest(agpVersion = AGP_CURRENT)

@Ignore
@Suppress("UnconstructableJUnitTestCase")
private class AllTestsForSelfChecks : SyncedProjectTest(selfTest = true, AGP_CURRENT)

private class ReportUsedProjectException(val testProject: TestProject) : Throwable()

/**
 * A test case that ensures all test projects defined in [TestProject] are added to [SyncedProjectTest] test methods.
 */
class SelfCheck {
  @Test
  fun `all test projects are tested`() {
    val testCase = AllTestsForSelfChecks()
    val testMethods = SyncedProjectTest::class.declaredMemberFunctions.filter { it.hasAnnotation<Test>() }
    val testedProjects = testMethods
      .mapNotNull {
        val result = kotlin.runCatching { it.call(testCase) }
        val exception = result.exceptionOrNull() ?: return@mapNotNull null
        val targetException = (exception as? InvocationTargetException)?.targetException ?: return@mapNotNull null
        val testProject = (targetException as? ReportUsedProjectException)?.testProject ?: return@mapNotNull null
        testProject
      }.toSet()
    val notTestedProjects = TestProject.values().filter { it !in testedProjects }
    assertThat(notTestedProjects).isEmpty()
  }
}

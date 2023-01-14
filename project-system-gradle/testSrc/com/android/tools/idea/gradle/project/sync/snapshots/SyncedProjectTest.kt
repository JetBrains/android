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

import com.android.tools.idea.gradle.project.sync.CapturePlatformModelsProjectResolverExtension
import com.android.tools.idea.gradle.project.sync.GradleModuleSystemIntegrationTest
import com.android.tools.idea.gradle.project.sync.HighlightProjectTestDef
import com.android.tools.idea.navigator.AndroidProjectViewSnapshotComparisonTestDef
import com.android.tools.idea.navigator.SourceProvidersTestDef
import com.android.tools.idea.projectsystem.gradle.GradleModuleHierarchyProviderTest
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_70
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.CoreIconManager
import com.intellij.ui.IconManager
import org.junit.Ignore
import org.junit.Test
import java.io.File

/**
 * An entry point to all tests asserting certain properties of synced projects.  See: [SyncedProjectTest.Companion.getTests] for the exact
 * list of assertions applied.
 *
 * This abstract test class allows to run tests in the IDE by running a specific test method below or by running a concrete class
 * representing an AGP version and/or other aspects of the environment.
 */
@RunsInEdt
abstract class SyncedProjectTest(
  selfTest: Boolean = false,
  agpVersion: AgpVersionSoftwareEnvironmentDescriptor
) : SyncedProjectTestBase<TestProject>(agpVersion, selfTest) {

  companion object {
    val tests = (
      IdeModelSnapshotComparisonTestDefinition.tests() +
        SourceProvidersTestDef.tests +
        ProjectStructureSnapshotTestDef.tests +
        AndroidProjectViewSnapshotComparisonTestDef.tests +
        GradleSyncLoggedEventsTestDef.tests +
        GradleModuleHierarchyProviderTest.tests +
        GradleModuleSystemIntegrationTest.tests +
        HighlightProjectTestDef.tests +
        selfChecks()
      ).groupBy { it.testProject }
  }

  @Test
  fun testSimpleApplication() = testProject(TestProject.SIMPLE_APPLICATION)

  @Test
  fun testSimpleApplication_noParallelSync() = testProject(TestProject.SIMPLE_APPLICATION_NO_PARALLEL_SYNC)

  @Test
  fun testSimpleApplication_viaSymLink() = testProject(TestProject.SIMPLE_APPLICATION_VIA_SYMLINK)

  @Test
  fun testSimpleApplication_appViaSymLink() = testProject(TestProject.SIMPLE_APPLICATION_APP_VIA_SYMLINK)

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
  fun testSimpleApplicationNotAtRoot() = testProject(TestProject.SIMPLE_APPLICATION_NOT_AT_ROOT)

  @Test
  fun testSimpleApplicationMultipleRoots() = testProject(TestProject.SIMPLE_APPLICATION_MULTIPLE_ROOTS)

  @Test
  fun testSimpleApplication_withUnnamedDimension() = testProject(TestProject.SIMPLE_APPLICATION_WITH_UNNAMED_DIMENSION)

  @Test
  fun testSimpleApplication_withAndroidCar() = testProject(TestProject.SIMPLE_APPLICATION_WITH_ANDROID_CAR)

  @Test
  fun testSimpleApplication_syncFailed() = testProject(TestProject.SIMPLE_APPLICATION_SYNC_FAILED)

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
  fun testNonStandardSourceSetDependencies_manualTestFixturesWorkaround() = testProject(TestProject.NON_STANDARD_SOURCE_SET_DEPENDENCIES_MANUAL_TEST_FIXTURES_WORKAROUND)

  @Test
  fun testNonStandardSourceSetDependencies_hierarchical() = testProject(TestProject.NON_STANDARD_SOURCE_SET_DEPENDENCIES_HIERARCHICAL)

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
  fun testKotlinMultiplatform_hierarchical() = testProject(TestProject.KOTLIN_MULTIPLATFORM_HIERARCHICAL)

  @Test
  fun testKotlinMultiplatform_hierarchical_withJs() = testProject(TestProject.KOTLIN_MULTIPLATFORM_HIERARCHICAL_WITHJS)

  @Test
  fun testKotlinMultiplatform_jvm() = testProject(TestProject.KOTLIN_MULTIPLATFORM_JVM)

  @Test
  fun testKotlinMultiplatform_jvm_hierarchical() = testProject(TestProject.KOTLIN_MULTIPLATFORM_JVM_HIERARCHICAL)

  @Test
  fun testKotlinMultiplatform_jvm_hierarchical_kmpapp() = testProject(TestProject.KOTLIN_MULTIPLATFORM_JVM_HIERARCHICAL_KMPAPP)

  @Test
  fun testKotlinMultiplatform_jvm_hierarchical_kmpapp_withintermediate() =
    testProject(TestProject.KOTLIN_MULTIPLATFORM_JVM_HIERARCHICAL_KMPAPP_WITHINTERMEDIATE)

  @Test
  fun testMultiFlavor() = testProject(TestProject.MULTI_FLAVOR)

  @Test
  fun testMultiFlavor_switchVariant() = testProject(TestProject.MULTI_FLAVOR_SWITCH_VARIANT)

  @Test
  fun testMultiFlavorWithFiltering() = testProject(TestProject.MULTI_FLAVOR_WITH_FILTERING)

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

  @Test
  fun testBuildSrcWithComposite() = testProject(TestProject.BUILDSRC_WITH_COMPOSITE)

  @Test
  fun testPrivacySandboxSdkProject() = testProject(TestProject.PRIVACY_SANDBOX_SDK)

  @Test
  fun testAppWithBuildFeaturesEnabled() = testProject(TestProject.APP_WITH_BUILD_FEATURES_ENABLED)

  @Test
  fun testNonTransitiveRClassSymbol() = testProject(TestProject.NON_TRANSITIVE_R_CLASS_SYMBOL)

  @Test
  fun testNonTransitiveRClassSymbolTrue() = testProject(TestProject.NON_TRANSITIVE_R_CLASS_SYMBOL_TRUE)

  override fun getTestDefs(testProject: TestProject): List<SyncedProjectTestDef> {
    return tests[testProject].orEmpty()
  }

  override fun setupAdditionalEnvironment() {
    CapturePlatformModelsProjectResolverExtension.registerTestHelperProjectResolver(
      CapturePlatformModelsProjectResolverExtension.IdeModels(),
      projectRule.testRootDisposable
    )
  }

  init {
    // Avoid depending on the execution order and initializing icons with dummies.
    try {
      IconManager.activate(CoreIconManager())
      IconLoader.activate()
    } catch (e: Throwable) {
      e.printStackTrace()
    }
  }
}

@Ignore
@Suppress("UnconstructableJUnitTestCase")
private class AllTestsForSelfChecks : SyncedProjectTest(selfTest = true, AGP_CURRENT)

/**
 * A test case that ensures all test projects defined in [TestProject] are added to [SyncedProjectTest] test methods.
 */
class SyncedProjectTestSelfCheck : SyncedProjectTestSelfCheckBase<SyncedProjectTest>(
  syncedProjectTestCase = SyncedProjectTest::class,
  instance = AllTestsForSelfChecks(),
  allProjects = TestProject.values().toList()
)

private fun selfChecks(): List<SyncedProjectTestDef> {
  return listOf(
    KotlinScriptIndexingDisabled(AGP_CURRENT)
  )
}

/**
 * Verifies that classes normally contributed to the project scope by Gradle Kotlin scripting support are not indexed in tests.
 */
data class KotlinScriptIndexingDisabled(override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor) : SyncedProjectTestDef {
  override val testProject: TestProject
    get() = TestProject.KOTLIN_GRADLE_DSL

  override fun isCompatible(): Boolean {
    return agpVersion >= AGP_70
  }

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): SyncedProjectTestDef {
    return copy(agpVersion = agpVersion)
  }

  override fun runTest(root: File, project: Project) {
    assertThat(DumbService.isDumb(project)).isFalse()
    // NOTE: This class is directly used in the test project to make sure this assertion is not outdated.
    val files = FilenameIndex.getVirtualFilesByName("KotlinJvmCompilerArgumentsProvider.class", GlobalSearchScope.everythingScope(project))
    assertThat(files.isEmpty()).isTrue()
  }

  override val name: String
    get() = "KotlinScriptIndexingDisabled"
}

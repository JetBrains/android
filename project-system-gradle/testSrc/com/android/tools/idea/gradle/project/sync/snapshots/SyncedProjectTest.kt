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
import com.android.tools.idea.testing.AgpIntegrationTestDefinition
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.PathUtil
import org.jetbrains.android.AndroidTestBase
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runners.model.MultipleFailureException
import java.io.File
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.hasAnnotation

class CurrentAgp: SyncedProjectTest(selfTest = false)

/**
 * An entry point to all tests asserting certain properties of synced projects.  See: [SyncedProjectTest.Companion.getTests] for the exact
 * list of assertions applied.
 *
 * This abstract test class allows to run tests in the IDE by running a specific test method below or by running a concrete class
 * representing an AGP version and/or other aspects of the environment.
 */
@RunsInEdt
sealed class SyncedProjectTest(val selfTest: Boolean = false) : GradleIntegrationTest {

  interface TestDef : AgpIntegrationTestDefinition {
    val testProject: TestProject
    fun runTest(root: File, project: Project)
  }

  companion object {
    val tests = IdeModelV2TestDef.tests().groupBy { it.testProject }
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

  private fun testProject(testProject: TestProject) {
    if (selfTest) throw ReportUsedProjectException(testProject)
    val root = prepareGradleProject(
      testProject.template,
      "project"
    )
    testProject.patch(root)
    CapturePlatformModelsProjectResolverExtension.registerTestHelperProjectResolver(projectRule.fixture.testRootDisposable)
    openPreparedProject("project${testProject.pathToOpen}") { project ->
      val exceptions = tests[testProject]?.mapNotNull {
        println("${it::class.java.simpleName}(${it.testProject.projectName})\n    $root")
        kotlin.runCatching { it.runTest(root, project) }.exceptionOrNull()
      }.orEmpty()
      when {
        exceptions.isEmpty() -> Unit
        exceptions.size == 1 -> throw exceptions.single()
        else -> throw MultipleFailureException(exceptions)
      }
    }
  }
}

private class ReportUsedProjectException(val testProject: TestProject) : Throwable()

@Ignore
@Suppress("UnconstructableJUnitTestCase")
private class AllTestsForSelfChecks: SyncedProjectTest(selfTest = true)

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

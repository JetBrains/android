/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AgpIntegrationTestDefinition
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.ModelVersion
import com.android.tools.idea.testing.aggregateAndThrowIfAny
import com.android.tools.idea.testing.runCatchingAndRecord
import com.google.common.truth.Expect
import com.google.common.truth.Truth
import com.intellij.openapi.project.Project
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.hasAnnotation

/**
 * A definition of a readonly (not modifying the project) test to be executed on an already synced project.
 *
 * `SyncedProjectTestDef`s are used to group multiple independent tests that verify the project structure or its interpretation without
 * making any changes to the project itself.  This is to avoid running Gradle sync for the same project multiple times and thus improve
 * test execution time.
 */
interface SyncedProjectTestDef : AgpIntegrationTestDefinition {
  /**
   * The test project this test applies to.
   */
  val testProject: TemplateBasedTestProject


  /**
   * Additional setup to be performed before preparing and opening a project.
   *
   * Note, this setup is intended for registering additional listeners etc. i.e. actions that do not have impact on the way projects are
   * opened and synced.
   */
  fun setup() = Unit

  /**
   * Verifies the structure of the [project] and records any failures to [expect].
   *
   * Note, it is ok to throw exceptions/assertions directly. They will be caught and aggregated in a similar to `Expect` rule manner,
   * however [expect] allows multiple failures to be recorded from a single [SyncedProjectTestDef].
   */
  fun PreparedTestProject.Context.runTest(root: File, project: Project, expect: Expect) = this@SyncedProjectTestDef.runTest(root, project, expect)
  fun runTest(root: File, project: Project, expect: Expect) = runTest(root, project)
  fun runTest(root: File, project: Project): Unit = error("At least one runTest has to be overridden")

  /**
   * Verified any data captured by listeners registered in [setup].
   */
  fun verifyAfterClosing(root: File) = Unit

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): SyncedProjectTestDef
}

/**
 * A base class for a test case that applies multiple [SyncedProjectTestDef]s to multiple [TemplateBasedTestProject]s. See
 * `SyncedProjectTest` as an example.
 */
abstract class SyncedProjectTestBase<TestProject: TemplateBasedTestProject>(
  /**
   * A AGP version this test will run with.
   */
  val agpVersion: AgpVersionSoftwareEnvironmentDescriptor,

  /**
   * if `true` activates a special mode that do not run actual tests but instead verify that the test case implementation implements
   * test methods for all declared projects. See [SyncedProjectTestSelfCheckBase] and its implementations.
   */
  val selfTest: Boolean = false
) {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expectRule: Expect = Expect.createAndEnableStackTrace()

  /**
   * Returns the list of [SyncedProjectTestDef] that should be applied to a synced project by [testProject].
   */
  abstract fun getTestDefs(testProject: TestProject): List<SyncedProjectTestDef>

  protected fun testProject(testProject: TestProject) {
    if (selfTest) throw ReportUsedProjectException(testProject)
    val testDefs = getTestDefs(testProject)
    val testDefinitions =
      testDefs
        .map(::transformTest)
        .filter { it.isCompatible() }
        .groupBy { it.agpVersion }
    if (testDefinitions.keys.size > 1) error("Only one software environment is supposed to be tested at a time")
    val agpVersion = testDefinitions.keys.singleOrNull()
      ?: skipTest("No tests to run!")
    if (!testProject.isCompatibleWith(agpVersion)) skipTest("Project ${testProject.name} is incompatible with $agpVersion")
    val tests = testDefinitions.entries.singleOrNull()?.value.orEmpty()

    setupAdditionalEnvironment()
    if (agpVersion.modelVersion == ModelVersion.V1) {
      StudioFlags.GRADLE_SYNC_USE_V2_MODEL.override(false)
    }
    try {
      val preparedProject = projectRule.prepareTestProject(
        testProject,
        agpVersion = agpVersion
      )

      aggregateAndThrowIfAny {
        tests.forEach {
          runCatchingAndRecord { it.setup() }
        }

        preparedProject.open(
          updateOptions = {
            it.copy(
              disableKtsRelatedIndexing = true
            )
          }
        ) { project ->
          tests.forEach {
            println("${it::class.java.simpleName}(${testProject.projectName})\n    $preparedProject.root")
            runCatchingAndRecord { with(it) { this@open.runTest(preparedProject.root, project, expectRule) } }
          }
        }
        tests.forEach {
          runCatchingAndRecord { it.verifyAfterClosing(preparedProject.root) }
        }
      }
    } finally {
      StudioFlags.GRADLE_SYNC_USE_V2_MODEL.clearOverride()
    }
  }

  protected open fun setupAdditionalEnvironment() = Unit

  private fun transformTest(testProject: SyncedProjectTestDef): SyncedProjectTestDef {
    return testProject.withAgpVersion(agpVersion)
  }
}

private class ReportUsedProjectException(val testProject: TemplateBasedTestProject) : Throwable()

/**
 * A base class for special test cases that ensure all test projects are actually tested by implementations of [SyncedProjectTestBase].
 */
abstract class SyncedProjectTestSelfCheckBase<T: Any>
(
  /**
   * The class of a [SyncedProjectTestBase] implementation. It is expected to be the one that defines test methods, but it can still be
   * abstract.
   */
  private val syncedProjectTestCase: KClass<T>,

  /**
   * A concrete instance of the test case with [SyncedProjectTestBase.selfTest] set to `true`.
   */
  private val instance: T,

  /**
   * The list of projects that are expected to be tested by [syncedProjectTestCase]. Usually something like `TestProject.values().toList()`
   */
  private val allProjects: Collection<TemplateBasedTestProject>
)
{
  @Test
  fun `all test projects are tested`() {
    val testCase = instance
    val testMethods = syncedProjectTestCase.declaredMemberFunctions.filter { it.hasAnnotation<Test>() }
    val testedProjects = testMethods
      .mapNotNull {
        val result = kotlin.runCatching { it.call(testCase) }
        val exception = result.exceptionOrNull() ?: return@mapNotNull null
        val targetException = (exception as? InvocationTargetException)?.targetException ?: return@mapNotNull null
        val testProject = (targetException as? ReportUsedProjectException)?.testProject ?: return@mapNotNull null
        testProject
      }.toSet()
    val notTestedProjects = allProjects.filter { it !in testedProjects }
    Truth.assertThat(notTestedProjects).isEmpty()
  }
}

private fun skipTest(message: String): Nothing {
  Assume.assumeTrue(message, false)
  error(message)
}

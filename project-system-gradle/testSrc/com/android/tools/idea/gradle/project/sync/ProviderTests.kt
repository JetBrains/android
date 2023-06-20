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
package com.android.tools.idea.gradle.project.sync

import com.android.ddmlib.IDevice
import com.android.sdklib.devices.Abi
import com.android.testutils.TestUtils
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.gradle.project.sync.ProviderIntegrationTestCase.CurrentAgp.Companion.NUMBER_OF_EXPECTATIONS
import com.android.tools.idea.gradle.project.sync.snapshots.TemplateBasedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.configuration.AndroidWatchFaceConfiguration
import com.android.tools.idea.testartifacts.TestConfigurationTesting
import com.android.tools.idea.testing.AgpIntegrationTestDefinition
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironment
import com.android.tools.idea.testing.createRunConfigurationFromClass
import com.android.tools.idea.testing.executeMakeBeforeRunStepInTest
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.mockDeviceFor
import com.android.tools.idea.testing.outputCurrentlyRunningTest
import com.android.tools.idea.testing.switchVariant
import com.google.common.truth.Expect
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import org.hamcrest.Matchers
import org.jetbrains.annotations.Contract
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.junit.Assume
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.concurrent.TimeUnit

sealed class Target {
  data class NamedAppTargetRunConfiguration(val externalSystemModuleId: String?) : Target()
  object AppTargetRunConfiguration : Target()
  data class TestTargetRunConfiguration(val testClassFqn: String) : Target()
  data class WatchFaceRunConfiguration(val testClassFqn: String) : Target()
  data class ManuallyAssembled(val gradlePath: String, val forTests: Boolean = false) : Target()
}

interface ValueNormalizers {
  fun File.toTestString(): String
  fun <T> Result<T>.toTestString(toTestString: T.() -> String = { this?.toString() ?: "(null)" }): String
  fun Map<AgpVersionSoftwareEnvironmentDescriptor, String>.forVersion(): String
  fun <T> Map<AgpVersionSoftwareEnvironmentDescriptor, T>.forVersion(): T?
}

data class TestScenario(
  val testProject: TemplateBasedTestProject,
  val viaBundle: Boolean = false,
  val executeMakeBeforeRun: Boolean = true,
  val target: Target = Target.AppTargetRunConfiguration,
  val variant: Pair<String, String>? = null,
  val device: Int = 30
) {
  val name: String
    get() {
      fun Boolean.prefixed(name: String): String = if (this) "-$name" else ""
      fun Target.prefixed(): String = when (this) {
        Target.AppTargetRunConfiguration -> ""
        is Target.TestTargetRunConfiguration -> "-test:$testClassFqn"
        is Target.WatchFaceRunConfiguration -> "-watch_face:$testClassFqn"
        is Target.NamedAppTargetRunConfiguration -> "-app:$externalSystemModuleId"
        is Target.ManuallyAssembled -> "-assemble:$gradlePath${forTests.prefixed("tests")}"
      }

      fun <T : Any> T?.prefixed() = this?.let { "-$it" } ?: ""

      return testProject.projectName +
             viaBundle.prefixed("via-bundle") +
             (!executeMakeBeforeRun).prefixed("before-build") +
             target.prefixed() +
             device.takeUnless { it == 30 }.prefixed() +
             variant.prefixed()
    }
}

interface TestConfiguration {
  val agpVersion: AgpVersionSoftwareEnvironmentDescriptor
}

interface ProviderTestDefinition {
  val scenario: TestScenario
  val IGNORE: TestConfiguration.() -> Unit
  fun verifyExpectations(
    expect: Expect,
    valueNormalizers: ValueNormalizers,
    project: Project,
    runConfiguration: RunConfiguration?,
    assembleResult: AssembleInvocationResult?,
    device: IDevice
  )

  val stackMarker: (() -> Unit) -> Unit
}

interface AggregateTestDefinition : AgpIntegrationTestDefinition, ProviderTestDefinition

fun IntegrationTestEnvironment.runProviderTest(testDefinition: AggregateTestDefinition, expect: Expect, valueNormalizers: ValueNormalizers) {
  val agpVersion = testDefinition.agpVersion
  val testConfiguration = object : TestConfiguration {
    override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = agpVersion
  }

  with(testDefinition) {
    if (!scenario.testProject.isCompatibleWith(agpVersion)) skipTest("Project ${scenario.testProject.name} is incompatible with $agpVersion")
    Assume.assumeThat(runCatching { testConfiguration.IGNORE() }.exceptionOrNull(), Matchers.nullValue())
    outputCurrentlyRunningTest(this)
    val preparedProject = prepareTestProject(scenario.testProject, agpVersion = agpVersion)
    val projectPath = preparedProject.root
    val gradlePropertiesPath = projectPath.resolve("gradle.properties")
    gradlePropertiesPath.writeText(
      gradlePropertiesPath.readText() + "\n android.suppressUnsupportedCompileSdk=${testDefinition.agpVersion.compileSdk}"
    )
    preparedProject.open { project ->
      try {
        val variant = scenario.variant
        if (variant != null) {
          switchVariant(project, variant.first, variant.second)
        }
        fun manuallyAssemble(
          gradlePath: String,
          forTests: Boolean
        ): AssembleInvocationResult {
          val module = project.gradleModule(gradlePath)!!
          return try {
            GradleBuildInvoker.getInstance(project)
              .assemble(arrayOf(module), if (forTests) TestCompileType.ANDROID_TESTS else TestCompileType.NONE)
              .get(3, TimeUnit.MINUTES)
          } finally {
            runInEdtAndWait {
              AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(project)
            }
          }
        }

        fun androidRunConfigurations() = RunManager
          .getInstance(project)
          .allConfigurationsList
          .filterIsInstance<AndroidRunConfiguration>()

        val (runConfiguration, assembleResult) =
          when (val target = scenario.target) {
            Target.AppTargetRunConfiguration ->
              androidRunConfigurations()
                .single()
                .also {
                  it.DEPLOY_APK_FROM_BUNDLE = scenario.viaBundle
                } to null

            is Target.NamedAppTargetRunConfiguration ->
              androidRunConfigurations()
                .single {
                  it.modules.any { module -> ExternalSystemApiUtil.getExternalProjectId(module) == target.externalSystemModuleId }
                }
                .also {
                  it.DEPLOY_APK_FROM_BUNDLE = scenario.viaBundle
                } to null

            is Target.TestTargetRunConfiguration ->
              runReadAction { TestConfigurationTesting.createAndroidTestConfigurationFromClass(project, target.testClassFqn)!! } to null

            is Target.WatchFaceRunConfiguration ->
              runReadAction {
                createRunConfigurationFromClass(project, target.testClassFqn, AndroidWatchFaceConfiguration::class.java)
                  ?: error("Run config for ${target.testClassFqn} not found.")
              } to null

            is Target.ManuallyAssembled ->
              if (scenario.viaBundle) error("viaBundle mode is not supported with ManuallyAssembled test configurations")
              else {
                null to manuallyAssemble(target.gradlePath, target.forTests)
              }
          }

        val device = mockDeviceFor(scenario.device, listOf(Abi.X86, Abi.X86_64), density = 160)
        if (scenario.executeMakeBeforeRun) {
          runConfiguration?.executeMakeBeforeRunStepInTest(device)
        }

        verifyExpectations(expect, valueNormalizers, project, runConfiguration, assembleResult, device)
      }
      finally {
        runInEdtAndWait {
          PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
          AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(project)
        }
      }
    }
  }
}

abstract class ProviderIntegrationTestCase{

  @RunWith(Parameterized::class)
  class CurrentAgp : ProviderIntegrationTestCase() {

    companion object {
      @Suppress("unused")
      @Contract(pure = true)
      @JvmStatic
      @Parameterized.Parameters(name = "{0}")
      fun testProjects(): Collection<*> {
        return tests().map { listOf(it).toTypedArray() }
      }

      const val NUMBER_OF_EXPECTATIONS = 2  // Apk and ApplicationIdProvider's.
      fun tests(): List<AggregateTestDefinition> =
        (APPLICATION_ID_PROVIDER_TESTS + APK_PROVIDER_TESTS)
          .groupBy { it.scenario }
          .map { AggregateTestDefinitionImpl(it.key, it.value) }
    }
  }

  @JvmField
  @Parameterized.Parameter(0)
  var testDefinition: AggregateTestDefinition? = null

  @Test
  fun testProvider() {
    projectRule.runProviderTest(testDefinition!!, expect, valueNormalizers)
  }

  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  var expect = Expect.createAndEnableStackTrace()

  private val m2Dirs by lazy {
    (EmbeddedDistributionPaths.getInstance().findAndroidStudioLocalMavenRepoPaths() +
     TestUtils.getPrebuiltOfflineMavenRepo().toFile())
      .map { File(FileUtil.toCanonicalPath(it.absolutePath)) }
  }

  private val valueNormalizers = object : ValueNormalizers {

    override fun File.toTestString(): String {
      val m2Root = m2Dirs.find { path.startsWith(it.path) }
      return if (m2Root != null) "<M2>/${relativeTo(m2Root).path}" else relativeTo(File(projectRule.getBaseTestPath())).path
    }

    override fun <T> Result<T>.toTestString(toTestString: T.() -> String) =
      (if (this.isSuccess) getOrThrow().toTestString() else null)
      ?: exceptionOrNull()?.let {
        val message = it.message?.replace(projectRule.getBaseTestPath(), "<ROOT>")
        "${it::class.java.simpleName}*> $message"
      }.orEmpty()

    override fun Map<AgpVersionSoftwareEnvironmentDescriptor, String>.forVersion() =
      (this[testDefinition!!.agpVersion] ?: this[AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT])?.trimIndent().orEmpty()

    override fun <T> Map<AgpVersionSoftwareEnvironmentDescriptor, T>.forVersion(): T? =
      (this[testDefinition!!.agpVersion] ?: this[AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT])
  }
}

data class AggregateTestDefinitionImpl(
  override val scenario: TestScenario,
  val definitions: List<ProviderTestDefinition>,
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
) : AggregateTestDefinition {
  override val IGNORE: TestConfiguration.() -> Unit = {
    assumeFalse(definitions.all { test -> kotlin.runCatching { with(test) { IGNORE() } }.isFailure })
  }

  override val stackMarker: (() -> Unit) -> Unit get() = error("")

  override fun verifyExpectations(
    expect: Expect,
    valueNormalizers: ValueNormalizers,
    project: Project,
    runConfiguration: RunConfiguration?,
    assembleResult: AssembleInvocationResult?,
    device: IDevice
  ) {
    val testConfiguration = object : TestConfiguration {
      override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = this@AggregateTestDefinitionImpl.agpVersion
    }
    for (definition in definitions) {
      if (kotlin.runCatching { definition.IGNORE(testConfiguration) }.isFailure) continue
      definition.stackMarker {
        definition.verifyExpectations(expect, valueNormalizers, project, runConfiguration, assembleResult, device)
      }
    }
  }

  override val name: String
    get() = "${scenario.name}${
      if (definitions.size < NUMBER_OF_EXPECTATIONS) "(${definitions.joinToString(",") { it.javaClass.simpleName}})"
      else ""
    }"

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): AgpIntegrationTestDefinition =
    copy(agpVersion = agpVersion)

  override fun toString(): String = displayName()
}

private fun skipTest(message: String): Nothing {
  Assume.assumeTrue(message, false)
  error(message)
}

infix fun <T, V> Array<T>.eachTo(value: V): Array<Pair<T, V>> = map { it to value }.toTypedArray()
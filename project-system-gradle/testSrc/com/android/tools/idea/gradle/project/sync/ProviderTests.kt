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
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.testartifacts.TestConfigurationTesting
import com.android.tools.idea.testing.AgpIntegrationTestDefinition
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.executeMakeBeforeRunStepInTest
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.mockDeviceFor
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.outputCurrentlyRunningTest
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.switchVariant
import com.google.common.truth.Expect
import com.intellij.execution.RunManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.testFramework.runInEdtAndWait
import org.hamcrest.Matchers
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.junit.Assume
import java.io.File
import java.util.concurrent.TimeUnit

sealed class Target {
  open class NamedAppTargetRunConfiguration(val externalSystemModuleId: String?) : Target()
  object AppTargetRunConfiguration : NamedAppTargetRunConfiguration(externalSystemModuleId = null)
  class TestTargetRunConfiguration(val testClassFqn: String) : Target()
  class ManuallyAssembled(val gradlePath: String, val forTests: Boolean = false) : Target()
}

interface ValueNormalizers {
  fun File.toTestString(): String
  fun <T> Result<T>.toTestString(toTestString: T.() -> String = { this?.toString() ?: "(null)" }): String
  fun Map<AgpVersionSoftwareEnvironmentDescriptor, String>.forVersion(): String
}

data class TestScenario(
  val testProject: String,
  val viaBundle: Boolean = false,
  val executeMakeBeforeRun: Boolean = true,
  val target: Target = Target.AppTargetRunConfiguration,
  val variant: Pair<String, String>? = null,
  val device: Int = 30,
  val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
) {
  val name: String
    get() {
      fun Boolean.named(name: String): String = if (this) "/$name" else ""
      fun Target.named(): String = when (this) {
        Target.AppTargetRunConfiguration -> ""
        is Target.TestTargetRunConfiguration -> "/test"
        is Target.NamedAppTargetRunConfiguration -> "/app:$externalSystemModuleId"
        is Target.ManuallyAssembled -> "/assemble:$gradlePath${forTests.named("tests")}"
      }
      return "$testProject${viaBundle.named("via-bundle")}${(!executeMakeBeforeRun).named("before-build")}${target.named()}"
    }
}

interface ProviderTestDefinition {
  val scenario: TestScenario
  val IGNORE: ProviderTestDefinition.() -> Unit
  val agpVersion: AgpVersionSoftwareEnvironmentDescriptor get() = scenario.agpVersion
  fun verifyExpectations(
    expect: Expect,
    valueNormalizers: ValueNormalizers,
    project: Project,
    runConfiguration: AndroidRunConfigurationBase?,
    assembleResult: AssembleInvocationResult?,
    device: IDevice
  )
}

fun GradleIntegrationTest.runProviderTest(testDefinition: ProviderTestDefinition, expect: Expect, valueNormalizers: ValueNormalizers) {
  with(testDefinition) {
    Assume.assumeThat(runCatching { IGNORE() }.exceptionOrNull(), Matchers.nullValue())
    if (this is AgpIntegrationTestDefinition<*>) {
      outputCurrentlyRunningTest(this)
    }
    prepareGradleProject(
      scenario.testProject,
      "project",
      gradleVersion = scenario.agpVersion.gradleVersion,
      gradlePluginVersion = scenario.agpVersion.agpVersion,
      kotlinVersion = scenario.agpVersion.kotlinVersion
    )

    openPreparedProject("project") { project ->
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
        }
        finally {
          runInEdtAndWait {
            AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(project)
          }
        }
      }

      val (runConfiguration, assembleResult) =
        when (val target = scenario.target) {
          is Target.NamedAppTargetRunConfiguration ->
            RunManager
              .getInstance(project)
              .allConfigurationsList
              .filterIsInstance<AndroidRunConfiguration>()
              .single {
                target.externalSystemModuleId == null ||
                it.modules.any { module -> ExternalSystemApiUtil.getExternalProjectId(module) == target.externalSystemModuleId }
              }
              .also {
                it.DEPLOY_APK_FROM_BUNDLE = scenario.viaBundle
              } to null
          is Target.TestTargetRunConfiguration ->
            runReadAction { TestConfigurationTesting.createAndroidTestConfigurationFromClass(project, target.testClassFqn)!! } to null
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
  }
}

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

import com.android.sdklib.devices.Abi
import com.android.testutils.TestUtils
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_35
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_40
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_41
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
import com.android.tools.idea.gradle.project.sync.ApplicationIdProviderIntegrationTestCase.TargetRunConfiguration.TestTargetRunConfiguration
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass
import com.android.tools.idea.testing.AgpIntegrationTestDefinition
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.executeMakeBeforeRunStepInTest
import com.android.tools.idea.testing.mockDeviceFor
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.outputCurrentlyRunningTest
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.switchVariant
import com.google.common.truth.Expect
import com.intellij.execution.RunManager
import com.intellij.openapi.util.io.FileUtil
import org.hamcrest.Matchers.nullValue
import org.jetbrains.annotations.Contract
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.junit.Assume.assumeThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

abstract class ApplicationIdProviderIntegrationTestCase : GradleIntegrationTest {

  @RunWith(Parameterized::class)
  class CurrentAgp : ApplicationIdProviderIntegrationTestCase() {

    companion object {
      @Suppress("unused")
      @Contract(pure = true)
      @JvmStatic
      @Parameterized.Parameters(name = "{0}")
      fun testProjects(): Collection<*> {
        return tests.map { listOf(it).toTypedArray() }
      }
    }
  }

  companion object {
    val tests =
      listOf(
        TestDefinition(
          name = "RUN_CONFIG_ACTIVITY before build",
          testProject = TestProjectPaths.RUN_CONFIG_ACTIVITY,
          executeMakeBeforeRun = false,
          expectPackageName = mapOf(
            AGP_CURRENT to "com.example.unittest",
            AGP_35 to "from.gradle.debug",
            AGP_40 to "from.gradle.debug",
          ),
          expectTestPackageName = mapOf(
            AGP_CURRENT to "(null)",
          )
        ),
        TestDefinition(
          name = "RUN_CONFIG_ACTIVITY after build",
          testProject = TestProjectPaths.RUN_CONFIG_ACTIVITY,
          expectPackageName = "from.gradle.debug",
          expectTestPackageName = "(null)"
        ),
        TestDefinition(
          name = "APPLICATION_ID_SUFFIX before build",
          testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
          executeMakeBeforeRun = false,
          expectPackageName = mapOf(
            AGP_CURRENT to "one.name",
            AGP_35 to "one.name.defaultConfig.debug",
            AGP_40 to "one.name.defaultConfig.debug",
          ),
          expectTestPackageName = mapOf(
            AGP_CURRENT to "(null)"
          )
        ),
        TestDefinition(
          name = "APPLICATION_ID_SUFFIX after build",
          testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
          expectPackageName = "one.name.defaultConfig.debug",
          expectTestPackageName = "(null)"
        ),
        TestDefinition(
          name = "APPLICATION_ID_SUFFIX run configuration via bundle",
          viaBundle = true,
          testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
          // TODO(b/190357145): Fix ApplicationId when fixed in AGP or decided how to handle this.
          expectPackageName = mapOf(
            AGP_CURRENT to "one.name",
            AGP_35 to "one.name.defaultConfig.debug",
            AGP_40 to "one.name.defaultConfig.debug",
          ),
          expectTestPackageName = mapOf(
            AGP_CURRENT to "(null)"
          )
        ),
        TestDefinition(
          name = "APPLICATION_ID_SUFFIX test run configuration",
          targetRunConfiguration = TestTargetRunConfiguration("one.name.ExampleInstrumentedTest"),
          testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
          expectPackageName = "one.name.defaultConfig.debug",
          expectTestPackageName = "one.name.test_app"
        ),
        TestDefinition(
          IGNORE = { if (agpVersion != AGP_CURRENT) error("Variant API is not supported by this AGP version.") },
          name = "APPLICATION_ID_VARIANT_API before build",
          testProject = TestProjectPaths.APPLICATION_ID_VARIANT_API,
          executeMakeBeforeRun = false,
          expectPackageName = "one.name",
          expectTestPackageName = "(null)"
        ),
        TestDefinition(
          IGNORE = { if (agpVersion != AGP_CURRENT) error("Variant API is not supported by this AGP version.") },
          name = "APPLICATION_ID_VARIANT_API after build",
          testProject = TestProjectPaths.APPLICATION_ID_VARIANT_API,
          expectPackageName = "one.dynamic.name.debug",
          expectTestPackageName = "(null)"
        ),
        TestDefinition(
          IGNORE = { if (agpVersion != AGP_CURRENT) error("Variant API is not supported by this AGP version.") },
          name = "APPLICATION_ID_VARIANT_API run configuration via bundle",
          viaBundle = true,
          testProject = TestProjectPaths.APPLICATION_ID_VARIANT_API,
          // TODO(b/190357145): Fix ApplicationId when fixed in AGP or decided how to handle this.
          expectPackageName = "one.name",
          expectTestPackageName = "(null)"
        ),
        TestDefinition(
          IGNORE = { if (agpVersion != AGP_CURRENT) error("Variant API is not supported by this AGP version.") },
          name = "APPLICATION_ID_VARIANT_API test run configuration",
          targetRunConfiguration = TestTargetRunConfiguration("one.name.ExampleInstrumentedTest"),
          testProject = TestProjectPaths.APPLICATION_ID_VARIANT_API,
          expectPackageName = "one.dynamic.name.debug",
          expectTestPackageName = "one.dynamic.name.debug.test"
        ),
        TestDefinition(
          IGNORE = {
            if (agpVersion == AGP_CURRENT) {
              error("Skip for the current AGP to save time in favor of 'APPLICATION_ID_SUFFIX test run configuration'")
            }
          },
          name = "SIMPLE_APPLICATION test run configuration",
          testProject = TestProjectPaths.SIMPLE_APPLICATION,
          targetRunConfiguration = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "google.simpleapplication.test"
        ),
        TestDefinition(
          name = "PROJECT_WITH_APP_AND_LIB_DEPENDENCY test run configuration",
          testProject = TestProjectPaths.PROJECT_WITH_APP_AND_LIB_DEPENDENCY,
          targetRunConfiguration = TestTargetRunConfiguration("com.example.projectwithappandlib.lib.ExampleInstrumentedTest"),
          expectPackageName = mapOf(
            AGP_CURRENT to "com.example.projectwithappandlib.lib.test",
            AGP_35 to "com.example.projectwithappandlib.lib.test",
            AGP_40 to "com.example.projectwithappandlib.lib.test",
            AGP_41 to "com.example.projectwithappandlib.lib.test"
          ),
          expectTestPackageName = mapOf(
            AGP_CURRENT to "com.example.projectwithappandlib.lib.test",
            AGP_35 to "com.example.projectwithappandlib.lib.test",
            AGP_40 to "com.example.projectwithappandlib.lib.test",
            AGP_41 to "com.example.projectwithappandlib.lib.test"
          )
        ),
        TestDefinition(
          name = "TEST_ONLY_MODULE test run configuration before build",
          testProject = TestProjectPaths.TEST_ONLY_MODULE,
          executeMakeBeforeRun = false,
          targetRunConfiguration = TestTargetRunConfiguration("com.example.android.app.ExampleTest"),
          expectPackageName = "com.example.android.app",
          expectTestPackageName = "com.example.android.app.testmodule"
        ),
        TestDefinition(
          name = "TEST_ONLY_MODULE test run configuration after build",
          testProject = TestProjectPaths.TEST_ONLY_MODULE,
          targetRunConfiguration = TestTargetRunConfiguration("com.example.android.app.ExampleTest"),
          expectPackageName = "com.example.android.app",
          expectTestPackageName = "com.example.android.app.testmodule"
        ),
        TestDefinition(
          name = "TEST_ONLY_MODULE test run configuration 2 before build",
          testProject = TestProjectPaths.TEST_ONLY_MODULE,
          executeMakeBeforeRun = false,
          targetRunConfiguration = TestTargetRunConfiguration("com.example.android.test2.ExampleTest"),
          expectPackageName = "com.example.android.app",
          expectTestPackageName = "com.example.android.test2"
        ),
        TestDefinition(
          name = "TEST_ONLY_MODULE test run configuration 2 after build",
          testProject = TestProjectPaths.TEST_ONLY_MODULE,
          targetRunConfiguration = TestTargetRunConfiguration("com.example.android.test2.ExampleTest"),
          expectPackageName = "com.example.android.app",
          expectTestPackageName = "com.example.android.test2"
        ),
        TestDefinition(
          name = "DYNAMIC_APP run configuration before build",
          testProject = TestProjectPaths.DYNAMIC_APP,
          executeMakeBeforeRun = false,
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "(null)"
        ),
        TestDefinition(
          name = "DYNAMIC_APP run configuration after build",
          testProject = TestProjectPaths.DYNAMIC_APP,
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "(null)"
        ),
        TestDefinition(
          name = "DYNAMIC_APP run configuration pre L device",
          device = 19,
          testProject = TestProjectPaths.DYNAMIC_APP,
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "(null)"
        ),
        TestDefinition(
          name = "DYNAMIC_APP test run configuration pre L device",
          device = 19,
          testProject = TestProjectPaths.DYNAMIC_APP,
          targetRunConfiguration = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "google.simpleapplication.test"
        ),
        TestDefinition(
          name = "DYNAMIC_APP test run configuration",
          testProject = TestProjectPaths.DYNAMIC_APP,
          targetRunConfiguration = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "google.simpleapplication.test"
        ),
        TestDefinition(
          name = "DYNAMIC_APP feature test run configuration",
          testProject = TestProjectPaths.DYNAMIC_APP,
          targetRunConfiguration = TestTargetRunConfiguration("com.example.instantapp.ExampleInstrumentedTest"),
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "com.example.feature1.test"
        ),
        TestDefinition(
          IGNORE = { if (agpVersion != AGP_35) error("instant apps are not supported by this version of AGP. ") },
          name = "INSTANT_APP run configuration",
          testProject = TestProjectPaths.INSTANT_APP,
          expectPackageName = "com.example.instantapp",
          expectTestPackageName = "(null)"
        )
      )
  }

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels()

  @get:Rule
  var expect = Expect.create()

  @get:Rule
  var testName = TestName()

  sealed class TargetRunConfiguration {
    object AppTargetRunConfiguration : TargetRunConfiguration()
    data class TestTargetRunConfiguration(val testClassFqn: String) : TargetRunConfiguration()
  }

  data class TestDefinition(
    val IGNORE: TestDefinition.() -> Unit = { },
    override val name: String = "",
    val viaBundle: Boolean = false,
    val device: Int = 30,
    val testProject: String = "",
    val variant: Pair<String, String>? = null,
    override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AGP_CURRENT,
    val executeMakeBeforeRun: Boolean = true,
    val targetRunConfiguration: TargetRunConfiguration = TargetRunConfiguration.AppTargetRunConfiguration,

    val expectPackageName: Map<AgpVersionSoftwareEnvironmentDescriptor, String>,
    val expectTestPackageName: Map<AgpVersionSoftwareEnvironmentDescriptor, String>,
  ) : AgpIntegrationTestDefinition<TestDefinition> {
    constructor (
      IGNORE: TestDefinition.() -> Unit = { },
      name: String = "",
      viaBundle: Boolean = false,
      device: Int = 30,
      testProject: String = "",
      variant: Pair<String, String>? = null,
      agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AGP_CURRENT,
      executeMakeBeforeRun: Boolean = true,
      targetRunConfiguration: TargetRunConfiguration = TargetRunConfiguration.AppTargetRunConfiguration,
      expectPackageName: String,
      expectTestPackageName: String
    ) : this(
      IGNORE = IGNORE,
      name = name,
      viaBundle = viaBundle,
      device = device,
      testProject = testProject,
      variant = variant,
      agpVersion = agpVersion,
      executeMakeBeforeRun = executeMakeBeforeRun,
      targetRunConfiguration = targetRunConfiguration,
      expectPackageName = mapOf(AGP_CURRENT to expectPackageName),
      expectTestPackageName = mapOf(AGP_CURRENT to expectTestPackageName)
    )

    override fun toString(): String = displayName()

    override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): TestDefinition = copy(agpVersion = agpVersion)
  }

  @JvmField
  @Parameterized.Parameter(0)
  var testDefinition: TestDefinition? = null

  @Test
  fun testApplicationIdProvider() {
    with(testDefinition!!) {
      assumeThat(runCatching { IGNORE() }.exceptionOrNull(), nullValue())
      outputCurrentlyRunningTest(this)

      fun Map<AgpVersionSoftwareEnvironmentDescriptor, String>.forVersion() = (this[agpVersion] ?: this[AGP_CURRENT])?.trimIndent().orEmpty()

      prepareGradleProject(
        testProject,
        "project",
        gradleVersion = agpVersion.gradleVersion,
        gradlePluginVersion = agpVersion.agpVersion,
        kotlinVersion = agpVersion.kotlinVersion
      )

      openPreparedProject("project") { project ->
        if (variant != null) {
          switchVariant(project, variant.first, variant.second)
        }
        val runConfiguration = runReadAction {
          when (targetRunConfiguration) {
            TargetRunConfiguration.AppTargetRunConfiguration ->
              RunManager
                .getInstance(project)
                .allConfigurationsList
                .filterIsInstance<AndroidRunConfiguration>()
                .single()
                .also {
                  it.DEPLOY_APK_FROM_BUNDLE = viaBundle
                }
            is TestTargetRunConfiguration ->
              createAndroidTestConfigurationFromClass(project, targetRunConfiguration.testClassFqn)!!
          }
        }
        val device = mockDeviceFor(device, listOf(Abi.X86, Abi.X86_64), density = 160)
        if (executeMakeBeforeRun) {
          runConfiguration.executeMakeBeforeRunStepInTest(device)
        }
        val applicationIdProvider = project.getProjectSystem().getApplicationIdProvider(runConfiguration)!!
        val packageName = runCatching { applicationIdProvider.packageName }
        val testPackageName = runCatching { applicationIdProvider.testPackageName }

        expect.that(packageName.toTestString()).isEqualTo(expectPackageName.forVersion())
        expect.that(testPackageName.toTestString()).isEqualTo(expectTestPackageName.forVersion())
      }
    }
  }

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = TestProjectPaths.TEST_DATA_PATH
  override fun getAdditionalRepos(): Collection<File> = listOf()

  private val m2Dirs by lazy {
    (EmbeddedDistributionPaths.getInstance().findAndroidStudioLocalMavenRepoPaths() +
     TestUtils.getPrebuiltOfflineMavenRepo().toFile())
      .map { File(FileUtil.toCanonicalPath(it.absolutePath)) }
  }

  private fun File.toTestString(): String {
    val m2Root = m2Dirs.find { path.startsWith(it.path) }
    return if (m2Root != null) "<M2>/${relativeTo(m2Root).path}" else relativeTo(File(getBaseTestPath())).path
  }

  private fun <T> Result<T>.toTestString() =
    (if (this.isSuccess) getOrThrow() ?: "(null)" else null)
      ?.toString()
    ?: exceptionOrNull()?.let {
      val message = it.message?.replace(getBaseTestPath(), "<ROOT>")
      "${it::class.java.simpleName}*> $message"
    }.orEmpty()
}

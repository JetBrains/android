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
import com.android.tools.idea.gradle.project.sync.ApplicationIdProviderIntegrationTestCase.AgpVersion.AGP_35
import com.android.tools.idea.gradle.project.sync.ApplicationIdProviderIntegrationTestCase.AgpVersion.AGP_40
import com.android.tools.idea.gradle.project.sync.ApplicationIdProviderIntegrationTestCase.AgpVersion.AGP_41
import com.android.tools.idea.gradle.project.sync.ApplicationIdProviderIntegrationTestCase.AgpVersion.CURRENT
import com.android.tools.idea.gradle.project.sync.ApplicationIdProviderIntegrationTestCase.TargetRunConfiguration.TestTargetRunConfiguration
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.openPreparedProject
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
            CURRENT to "com.example.unittest",
            AGP_35 to "from.gradle.debug",
            AGP_40 to "from.gradle.debug",
          ),
          expectTestPackageName = mapOf(
            CURRENT to "com.example.unittest.test",
            AGP_35 to "from.gradle.debug.test",
            AGP_40 to "from.gradle.debug.test",
          )
        ),
        TestDefinition(
          name = "RUN_CONFIG_ACTIVITY after build",
          testProject = TestProjectPaths.RUN_CONFIG_ACTIVITY,
          expectPackageName = "from.gradle.debug",
          expectTestPackageName = "from.gradle.debug.test"
        ),
        TestDefinition(
          name = "APPLICATION_ID_SUFFIX before build",
          testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
          executeMakeBeforeRun = false,
          expectPackageName = mapOf(
            CURRENT to "one.name",
            AGP_35 to "one.name.defaultConfig.debug",
            AGP_40 to "one.name.defaultConfig.debug",
          ),
          expectTestPackageName = mapOf(
            CURRENT to "one.name.test_app",
            AGP_35 to "one.name.test_app",
            AGP_40 to "one.name.test_app",
          )
        ),
        TestDefinition(
          name = "APPLICATION_ID_SUFFIX after build",
          testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
          expectPackageName = "one.name.defaultConfig.debug",
          expectTestPackageName = "one.name.test_app"
        ),
        TestDefinition(
          name = "APPLICATION_ID_SUFFIX run configuration via bundle",
          viaBundle = true,
          testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
          // TODO(b/190357145): Fix ApplicationId when fixed in AGP or decided how to handle this.
          expectPackageName = mapOf(
            CURRENT to "one.name",
            AGP_35 to "one.name.defaultConfig.debug",
            AGP_40 to "one.name.defaultConfig.debug",
          ),
          expectTestPackageName = mapOf(
            CURRENT to "one.name.test_app",
            AGP_35 to "one.name.test_app",
            AGP_40 to "one.name.test_app",
          )
        ),
        TestDefinition(
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
            CURRENT to "com.example.projectwithappandlib.lib.test",
            AGP_35 to "com.example.projectwithappandlib.lib.test",
            AGP_40 to "com.example.projectwithappandlib.lib.test",
            AGP_41 to "com.example.projectwithappandlib.lib.test"
          ),
          expectTestPackageName = mapOf(
            CURRENT to "com.example.projectwithappandlib.lib.test",
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
          expectPackageName = mapOf(
            CURRENT to "com.example.android.app",
            AGP_35 to "com.example.android.app",
            AGP_40 to "com.example.android.app"
          ),
          expectTestPackageName = mapOf(
            CURRENT to "com.example.android.app",
            AGP_35 to "com.example.android.app.testmodule",
            AGP_40 to "com.example.android.app.testmodule"
          )
        ),
        TestDefinition(
          name = "TEST_ONLY_MODULE test run configuration after build",
          testProject = TestProjectPaths.TEST_ONLY_MODULE,
          targetRunConfiguration = TestTargetRunConfiguration("com.example.android.app.ExampleTest"),
          expectPackageName = "com.example.android.app",
          expectTestPackageName = "com.example.android.app.testmodule"
        ),
        TestDefinition(
          name = "DYNAMIC_APP run configuration before build",
          testProject = TestProjectPaths.DYNAMIC_APP,
          executeMakeBeforeRun = false,
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "google.simpleapplication.test"
        ),
        TestDefinition(
          name = "DYNAMIC_APP run configuration after build",
          testProject = TestProjectPaths.DYNAMIC_APP,
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "google.simpleapplication.test"
        ),
        TestDefinition(
          name = "DYNAMIC_APP run configuration pre L device",
          device = 19,
          testProject = TestProjectPaths.DYNAMIC_APP,
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "google.simpleapplication.test"
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
          expectTestPackageName = "com.example.instantapp.test"
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

  enum class AgpVersion(val forGradle: String?) {
    CURRENT(null),
    AGP_35("3.5.0"),
    AGP_40("4.0.0"),
    AGP_41("4.1.0")
  }

  data class TestDefinition(
    val IGNORE: TestDefinition.() -> Unit = { },
    val name: String = "",
    val viaBundle: Boolean = false,
    val device: Int = 30,
    val testProject: String = "",
    val variant: Pair<String, String>? = null,
    val agpVersion: AgpVersion = CURRENT,
    val gradleVersion: String? = null,
    val kotlinVersion: String? = null,
    val executeMakeBeforeRun: Boolean = true,
    val targetRunConfiguration: TargetRunConfiguration = TargetRunConfiguration.AppTargetRunConfiguration,

    val expectPackageName: Map<AgpVersion, String>,
    val expectTestPackageName: Map<AgpVersion, String>,
  ) {
    constructor (
      IGNORE: TestDefinition.() -> Unit = { },
      name: String = "",
      viaBundle: Boolean = false,
      device: Int = 30,
      testProject: String = "",
      variant: Pair<String, String>? = null,
      agpVersion: AgpVersion = CURRENT,
      gradleVersion: String? = null,
      kotlinVersion: String? = null,
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
      gradleVersion = gradleVersion,
      kotlinVersion = kotlinVersion,
      executeMakeBeforeRun = executeMakeBeforeRun,
      targetRunConfiguration = targetRunConfiguration,
      expectPackageName = mapOf(CURRENT to expectPackageName),
      expectTestPackageName = mapOf(CURRENT to expectTestPackageName)
    )

    override fun toString(): String = name
  }

  @JvmField
  @Parameterized.Parameter(0)
  var testDefinition: TestDefinition? = null

  @Test
  fun testApplicationIdProvider() {
    with(testDefinition!!) {
      assumeThat(runCatching { IGNORE() }.exceptionOrNull(), nullValue())

      fun Map<AgpVersion, String>.forVersion() = (this[agpVersion] ?: this[CURRENT])?.trimIndent().orEmpty()

      prepareGradleProject(
        testProject,
        "project",
        gradleVersion = gradleVersion,
        gradlePluginVersion = agpVersion.forGradle,
        kotlinVersion = kotlinVersion
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
    getOrNull()?.toString()
    ?: exceptionOrNull()?.let {
      val message = it.message?.replace(getBaseTestPath(), "<ROOT>")
      "${it::class.java.simpleName}*> $message"
    }.orEmpty()
}

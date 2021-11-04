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
import com.android.testutils.TestUtils
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult
import com.android.tools.idea.gradle.project.sync.Target.TestTargetRunConfiguration
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.testing.AgpIntegrationTestDefinition
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_35
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_40
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_41
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Expect
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.Contract
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
          TestScenario(
            testProject = TestProjectPaths.RUN_CONFIG_ACTIVITY,
            executeMakeBeforeRun = false,
          ),
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
          TestScenario(
            testProject = TestProjectPaths.RUN_CONFIG_ACTIVITY,
          ),
          expectPackageName = "from.gradle.debug",
          expectTestPackageName = "(null)"
        ),
        TestDefinition(
          TestScenario(
            testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
            executeMakeBeforeRun = false,
          ),
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
          TestScenario(
            testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
          ),
          expectPackageName = "one.name.defaultConfig.debug",
          expectTestPackageName = "(null)"
        ),
        TestDefinition(
          TestScenario(
            viaBundle = true,
            testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
          ),
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
          TestScenario(
            target = TestTargetRunConfiguration("one.name.ExampleInstrumentedTest"),
            testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
          ),
          expectPackageName = "one.name.defaultConfig.debug",
          expectTestPackageName = "one.name.test_app"
        ),
        TestDefinition(
          TestScenario(
            testProject = TestProjectPaths.APPLICATION_ID_VARIANT_API,
            executeMakeBeforeRun = false,
          ),
          IGNORE = { if (agpVersion != AGP_CURRENT) error("Variant API is not supported by this AGP version.") },
          expectPackageName = "one.name",
          expectTestPackageName = "(null)"
        ),
        TestDefinition(
          TestScenario(
            testProject = TestProjectPaths.APPLICATION_ID_VARIANT_API,
          ),
          IGNORE = { if (agpVersion != AGP_CURRENT) error("Variant API is not supported by this AGP version.") },
          expectPackageName = "one.dynamic.name.debug",
          expectTestPackageName = "(null)"
        ),
        TestDefinition(
          TestScenario(
            viaBundle = true,
            testProject = TestProjectPaths.APPLICATION_ID_VARIANT_API,
          ),
          IGNORE = { if (agpVersion != AGP_CURRENT) error("Variant API is not supported by this AGP version.") },
          // TODO(b/190357145): Fix ApplicationId when fixed in AGP or decided how to handle this.
          expectPackageName = "one.name",
          expectTestPackageName = "(null)"
        ),
        TestDefinition(
          TestScenario(
            target = TestTargetRunConfiguration("one.name.ExampleInstrumentedTest"),
            testProject = TestProjectPaths.APPLICATION_ID_VARIANT_API,
          ),
          IGNORE = { if (agpVersion != AGP_CURRENT) error("Variant API is not supported by this AGP version.") },
          expectPackageName = "one.dynamic.name.debug",
          expectTestPackageName = "one.dynamic.name.debug.test"
        ),
        TestDefinition(
          TestScenario(
            testProject = TestProjectPaths.SIMPLE_APPLICATION,
            target = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
          ),
          IGNORE = {
            if (agpVersion == AGP_CURRENT) {
              error("Skip for the current AGP to save time in favor of 'APPLICATION_ID_SUFFIX test run configuration'")
            }
          },
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "google.simpleapplication.test"
        ),
        TestDefinition(
          TestScenario(
            testProject = TestProjectPaths.PROJECT_WITH_APP_AND_LIB_DEPENDENCY,
            target = TestTargetRunConfiguration("com.example.projectwithappandlib.lib.ExampleInstrumentedTest"),
          ),
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
          TestScenario(
            testProject = TestProjectPaths.TEST_ONLY_MODULE,
            executeMakeBeforeRun = false,
            target = TestTargetRunConfiguration("com.example.android.app.ExampleTest"),
          ),
          expectPackageName = "com.example.android.app",
          expectTestPackageName = "com.example.android.app.testmodule"
        ),
        TestDefinition(
          TestScenario(
            testProject = TestProjectPaths.TEST_ONLY_MODULE,
            target = TestTargetRunConfiguration("com.example.android.app.ExampleTest"),
          ),
          expectPackageName = "com.example.android.app",
          expectTestPackageName = "com.example.android.app.testmodule"
        ),
        TestDefinition(
          TestScenario(
            testProject = TestProjectPaths.TEST_ONLY_MODULE,
            executeMakeBeforeRun = false,
            target = TestTargetRunConfiguration("com.example.android.test2.ExampleTest"),
          ),
          expectPackageName = "com.example.android.app",
          expectTestPackageName = "com.example.android.test2"
        ),
        TestDefinition(
          TestScenario(
            testProject = TestProjectPaths.TEST_ONLY_MODULE,
            target = TestTargetRunConfiguration("com.example.android.test2.ExampleTest"),
          ),
          expectPackageName = "com.example.android.app",
          expectTestPackageName = "com.example.android.test2"
        ),
        TestDefinition(
          TestScenario(
            testProject = TestProjectPaths.DYNAMIC_APP,
            executeMakeBeforeRun = false,
          ),
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "(null)"
        ),
        TestDefinition(
          TestScenario(
            testProject = TestProjectPaths.DYNAMIC_APP,
          ),
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "(null)"
        ),
        TestDefinition(
          TestScenario(
            device = 19,
            testProject = TestProjectPaths.DYNAMIC_APP,
          ),
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "(null)"
        ),
        TestDefinition(
          TestScenario(
            device = 19,
            testProject = TestProjectPaths.DYNAMIC_APP,
            target = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
          ),
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "google.simpleapplication.test"
        ),
        TestDefinition(
          TestScenario(
            testProject = TestProjectPaths.DYNAMIC_APP,
            target = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
          ),
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "google.simpleapplication.test"
        ),
        TestDefinition(
          TestScenario(
            testProject = TestProjectPaths.DYNAMIC_APP,
            target = TestTargetRunConfiguration("com.example.instantapp.ExampleInstrumentedTest"),
          ),
          expectPackageName = "google.simpleapplication",
          expectTestPackageName = "com.example.feature1.test"
        ),
        TestDefinition(
          TestScenario(
            testProject = TestProjectPaths.INSTANT_APP,
          ),
          IGNORE = { if (agpVersion != AGP_35) error("instant apps are not supported by this version of AGP. ") },
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

  data class TestDefinition(
    override val scenario: TestScenario,
    override val IGNORE: ProviderTestDefinition.() -> Unit = { },
    val expectPackageName: Map<AgpVersionSoftwareEnvironmentDescriptor, String>,
    val expectTestPackageName: Map<AgpVersionSoftwareEnvironmentDescriptor, String>,
  ) : AgpIntegrationTestDefinition<TestDefinition>, ProviderTestDefinition {
    constructor (
      scenario: TestScenario,
      IGNORE: ProviderTestDefinition.() -> Unit = { },
      expectPackageName: String,
      expectTestPackageName: String
    ) : this(
      IGNORE = IGNORE,
      scenario = scenario,
      expectPackageName = mapOf(AGP_CURRENT to expectPackageName),
      expectTestPackageName = mapOf(AGP_CURRENT to expectTestPackageName)
    )

    override val name: String
      get() = scenario.name

    override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor
      get() = scenario.agpVersion

    override fun toString(): String = displayName()

    override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): TestDefinition =
      copy(scenario = scenario.copy(agpVersion = agpVersion))

    override fun verifyExpectations(
      expect: Expect,
      valueNormalizers: ValueNormalizers,
      project: Project,
      runConfiguration: AndroidRunConfigurationBase?,
      assembleResult: AssembleInvocationResult?,
      device: IDevice
    ) {
      val applicationIdProvider = project.getProjectSystem().getApplicationIdProvider(runConfiguration!!)!!
      val packageName = runCatching { applicationIdProvider.packageName }
      val testPackageName = runCatching { applicationIdProvider.testPackageName }

      with(valueNormalizers) {
        expect.that(packageName.toTestString()).isEqualTo(expectPackageName.forVersion())
        expect.that(testPackageName.toTestString()).isEqualTo(expectTestPackageName.forVersion())
      }
    }
  }

  @JvmField
  @Parameterized.Parameter(0)
  var testDefinition: TestDefinition? = null

  @Test
  fun testApplicationIdProvider() {
    runProviderTest(testDefinition!!, expect, valueNormalizers)
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

  private val valueNormalizers = object : ValueNormalizers {

    override fun File.toTestString(): String {
      val m2Root = m2Dirs.find { path.startsWith(it.path) }
      return if (m2Root != null) "<M2>/${relativeTo(m2Root).path}" else relativeTo(File(getBaseTestPath())).path
    }

    override fun <T> Result<T>.toTestString(toTestString: T.() -> String) =
      (if (this.isSuccess) getOrThrow().toTestString() else null)
      ?: exceptionOrNull()?.let {
        val message = it.message?.replace(getBaseTestPath(), "<ROOT>")
        "${it::class.java.simpleName}*> $message"
      }.orEmpty()

    override fun Map<AgpVersionSoftwareEnvironmentDescriptor, String>.forVersion() =
      (this[testDefinition!!.scenario.agpVersion] ?: this[AGP_CURRENT])?.trimIndent().orEmpty()
  }
}

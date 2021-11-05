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
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult
import com.android.tools.idea.gradle.project.sync.Target.TestTargetRunConfiguration
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_35
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_40
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_41
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Expect
import com.intellij.openapi.project.Project

internal val APPLICATION_ID_PROVIDER_TESTS: List<ProviderTestDefinition> =
  listOf(
    def(
      stackMarker = { it() },
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
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.RUN_CONFIG_ACTIVITY,
      ),
      expectPackageName = "from.gradle.debug",
      expectTestPackageName = "(null)"
    ),
    def(
      stackMarker = { it() },
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
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
      ),
      expectPackageName = "one.name.defaultConfig.debug",
      expectTestPackageName = "(null)"
    ),
    def(
      stackMarker = { it() },
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
    def(
      stackMarker = { it() },
      TestScenario(
        target = TestTargetRunConfiguration("one.name.ExampleInstrumentedTest"),
        testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
      ),
      expectPackageName = "one.name.defaultConfig.debug",
      expectTestPackageName = "one.name.test_app"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.APPLICATION_ID_VARIANT_API,
        executeMakeBeforeRun = false,
      ),
      IGNORE = { if (agpVersion != AGP_CURRENT) error("Variant API is not supported by this AGP version.") },
      expectPackageName = "one.name",
      expectTestPackageName = "(null)"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.APPLICATION_ID_VARIANT_API,
      ),
      IGNORE = { if (agpVersion != AGP_CURRENT) error("Variant API is not supported by this AGP version.") },
      expectPackageName = "one.dynamic.name.debug",
      expectTestPackageName = "(null)"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        viaBundle = true,
        testProject = TestProjectPaths.APPLICATION_ID_VARIANT_API,
      ),
      IGNORE = { if (agpVersion != AGP_CURRENT) error("Variant API is not supported by this AGP version.") },
      // TODO(b/190357145): Fix ApplicationId when fixed in AGP or decided how to handle this.
      expectPackageName = "one.name",
      expectTestPackageName = "(null)"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        target = TestTargetRunConfiguration("one.name.ExampleInstrumentedTest"),
        testProject = TestProjectPaths.APPLICATION_ID_VARIANT_API,
      ),
      IGNORE = { if (agpVersion != AGP_CURRENT) error("Variant API is not supported by this AGP version.") },
      expectPackageName = "one.dynamic.name.debug",
      expectTestPackageName = "one.dynamic.name.debug.test"
    ),
    def(
      stackMarker = { it() },
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
    def(
      stackMarker = { it() },
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
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.TEST_ONLY_MODULE,
        executeMakeBeforeRun = false,
        target = TestTargetRunConfiguration("com.example.android.app.ExampleTest"),
      ),
      expectPackageName = "com.example.android.app",
      expectTestPackageName = "com.example.android.app.testmodule"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.TEST_ONLY_MODULE,
        target = TestTargetRunConfiguration("com.example.android.app.ExampleTest"),
      ),
      expectPackageName = "com.example.android.app",
      expectTestPackageName = "com.example.android.app.testmodule"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.TEST_ONLY_MODULE,
        executeMakeBeforeRun = false,
        target = TestTargetRunConfiguration("com.example.android.test2.ExampleTest"),
      ),
      expectPackageName = "com.example.android.app",
      expectTestPackageName = "com.example.android.test2"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.TEST_ONLY_MODULE,
        target = TestTargetRunConfiguration("com.example.android.test2.ExampleTest"),
      ),
      expectPackageName = "com.example.android.app",
      expectTestPackageName = "com.example.android.test2"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.DYNAMIC_APP,
        executeMakeBeforeRun = false,
      ),
      expectPackageName = "google.simpleapplication",
      expectTestPackageName = "(null)"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.DYNAMIC_APP,
      ),
      expectPackageName = "google.simpleapplication",
      expectTestPackageName = "(null)"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        device = 19,
        testProject = TestProjectPaths.DYNAMIC_APP,
      ),
      expectPackageName = "google.simpleapplication",
      expectTestPackageName = "(null)"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        device = 19,
        testProject = TestProjectPaths.DYNAMIC_APP,
        target = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
      ),
      expectPackageName = "google.simpleapplication",
      expectTestPackageName = "google.simpleapplication.test"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.DYNAMIC_APP,
        target = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
      ),
      expectPackageName = "google.simpleapplication",
      expectTestPackageName = "google.simpleapplication.test"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.DYNAMIC_APP,
        target = TestTargetRunConfiguration("com.example.instantapp.ExampleInstrumentedTest"),
      ),
      expectPackageName = "google.simpleapplication",
      expectTestPackageName = "com.example.feature1.test"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProjectPaths.INSTANT_APP,
      ),
      IGNORE = { if (agpVersion != AGP_35) error("instant apps are not supported by this version of AGP. ") },
      expectPackageName = "com.example.instantapp",
      expectTestPackageName = "(null)"
    )
  )

private fun def(
  stackMarker: (() -> Unit) -> Unit, // Is supposed to be implemented as { it() }.
  scenario: TestScenario,
  IGNORE: TestConfiguration.() -> Unit = { },
  expectPackageName: String,
  expectTestPackageName: String? = null,
) = ApplicationIdProviderTest(
  scenario = scenario,
  IGNORE = IGNORE,
  expectPackageName = mapOf(AGP_CURRENT to expectPackageName),
  expectTestPackageName = expectTestPackageName?.let { mapOf(AGP_CURRENT to expectTestPackageName) } ?: emptyMap(),
  stackMarker = stackMarker
)

private fun def(
  stackMarker: (() -> Unit) -> Unit, // Is supposed to be implemented as { it() }.
  scenario: TestScenario,
  IGNORE: TestConfiguration.() -> Unit = { },
  expectPackageName: Map<AgpVersionSoftwareEnvironmentDescriptor, String>,
  expectTestPackageName: Map<AgpVersionSoftwareEnvironmentDescriptor, String> = emptyMap(),
) = ApplicationIdProviderTest(
  scenario = scenario,
  IGNORE = IGNORE,
  expectPackageName = expectPackageName,
  expectTestPackageName = expectTestPackageName,
  stackMarker = stackMarker
)


private data class ApplicationIdProviderTest(
  override val scenario: TestScenario,
  override val IGNORE: TestConfiguration.() -> Unit = { },
  override val stackMarker: (() -> Unit) -> Unit, // Is supposed to be implemented as { it() }.
  val expectPackageName: Map<AgpVersionSoftwareEnvironmentDescriptor, String>,
  val expectTestPackageName: Map<AgpVersionSoftwareEnvironmentDescriptor, String>,
) : ProviderTestDefinition {

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

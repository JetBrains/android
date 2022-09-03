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
import com.android.tools.idea.gradle.project.sync.issues.SyncIssues.Companion.syncIssues
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_35
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_40
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_41
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_72
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Expect
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

internal val APPLICATION_ID_PROVIDER_TESTS: List<ProviderTestDefinition> =
  listOf(
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = AndroidCoreTestProject.RUN_CONFIG_ACTIVITY,
        executeMakeBeforeRun = false,
      ),
      expectPackageName = "from.gradle.debug",
      expectTestPackageName = "(null)",
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = AndroidCoreTestProject.APPLICATION_ID_SUFFIX,
        executeMakeBeforeRun = false,
      ),
      expectPackageName = "one.name.defaultConfig.debug",
      expectTestPackageName = "(null)"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        target = TestTargetRunConfiguration("one.name.ExampleInstrumentedTest"),
        testProject = AndroidCoreTestProject.APPLICATION_ID_SUFFIX,
        executeMakeBeforeRun = false,
      ),
      expectPackageName = "one.name.defaultConfig.debug",
      expectTestPackageName = "one.name.test_app"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = AndroidCoreTestProject.APPLICATION_ID_VARIANT_API,
        executeMakeBeforeRun = false,
      ),
      IGNORE = { if (agpVersion != AGP_CURRENT) error("Variant API is not supported by this AGP version.") },
      expectPackageName = "one.dynamic.name.debug",
      expectTestPackageName = "(null)"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        target = TestTargetRunConfiguration("one.name.ExampleInstrumentedTest"),
        testProject = AndroidCoreTestProject.APPLICATION_ID_VARIANT_API,
        executeMakeBeforeRun = false,
      ),
      IGNORE = { if (agpVersion != AGP_CURRENT && agpVersion != AGP_72) error("Application ID must be available during sync for model v1, so only test with model v2.") },
      expectPackageName = "one.dynamic.name.debug",
      expectTestPackageName = "one.dynamic.name.debug.test"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = AndroidCoreTestProject.APPLICATION_ID_VARIANT_API_BROKEN,
        executeMakeBeforeRun = false, // Build is broken by the application ID not being available
        ),
      IGNORE = { if (agpVersion != AGP_CURRENT && agpVersion != AGP_72) error("Application ID must be available during sync for model v1, so only test with model v2.") },
      expectPackageName = mapOf(
        AGP_CURRENT to "TestLoggerAssertionError*> Could not get applicationId for Application_ID_broken_in_variant_API.app.main. Project type: PROJECT_TYPE_APP",
        AGP_72 to "TestLoggerAssertionError*> Could not get applicationId for Application_ID_broken_in_variant_API.app.main. Project type: PROJECT_TYPE_APP"
      ),
      expectTestPackageName = mapOf(
        AGP_CURRENT to "(null)",
        AGP_72 to "(null)",
      ),
      expectSyncIssueContent = mapOf(
        AGP_CURRENT to listOf(
          "Failed to read applicationId for debug.\nSetting the application ID to the output of a task in the variant api is not supported",
          "Failed to read applicationId for debugAndroidTest.\nSetting the application ID to the output of a task in the variant api is not supported",
          "Failed to read applicationId for release.\nSetting the application ID to the output of a task in the variant api is not supported",
        ),
        AGP_72 to listOf(
          "Failed to read applicationId for debug.\nSetting the application ID to the output of a task in the variant api is not supported",
          "Failed to read applicationId for debugAndroidTest.\nSetting the application ID to the output of a task in the variant api is not supported",
          "Failed to read applicationId for release.\nSetting the application ID to the output of a task in the variant api is not supported",
        ),
      )
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProject.SIMPLE_APPLICATION,
        target = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
        executeMakeBeforeRun = false,
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
        testProject = AndroidCoreTestProject.PROJECT_WITH_APP_AND_LIB_DEPENDENCY,
        target = TestTargetRunConfiguration("com.example.projectwithappandlib.lib.ExampleInstrumentedTest"),
        executeMakeBeforeRun = false,
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
        testProject = TestProject.TEST_ONLY_MODULE,
        executeMakeBeforeRun = false,
        target = TestTargetRunConfiguration("com.example.android.app.ExampleTest"),
      ),
      expectPackageName = "com.example.android.app",
      expectTestPackageName = "com.example.android.app.testmodule"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = TestProject.TEST_ONLY_MODULE,
        executeMakeBeforeRun = false,
        target = TestTargetRunConfiguration("com.example.android.test2.ExampleTest"),
      ),
      expectPackageName = "com.example.android.app",
      expectTestPackageName = "com.example.android.test2"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = AndroidCoreTestProject.DYNAMIC_APP,
        executeMakeBeforeRun = false,
      ),
      expectPackageName = "google.simpleapplication",
      expectTestPackageName = "(null)"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = AndroidCoreTestProject.DYNAMIC_APP,
        target = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
        executeMakeBeforeRun = false,
      ),
      expectPackageName = "google.simpleapplication",
      expectTestPackageName = "google.simpleapplication.test"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = AndroidCoreTestProject.DYNAMIC_APP,
        target = TestTargetRunConfiguration("com.example.feature1.ExampleInstrumentedTest"),
        executeMakeBeforeRun = false,
      ),
      expectPackageName = "google.simpleapplication",
      expectTestPackageName = "com.example.feature1.test"
    ),
    def(
      stackMarker = { it() },
      TestScenario(
        testProject = AndroidCoreTestProject.INSTANT_APP,
        executeMakeBeforeRun = false,
      ),
      IGNORE = { if (agpVersion != AGP_35) error("instant apps are not supported by this version of AGP. ") },
      expectPackageName = "com.example.instantapp",
      expectTestPackageName = "(null)", // The instant-app wrapper project does not have tests.
      expectSyncIssueContent = listOf(
        "The com.android.feature plugin is deprecated and will be removed by the end of 2019. " +
        "Please switch to using dynamic-features or libraries. " +
        "For more information on converting your application to using Android App Bundles, please visit " +
        "https://developer.android.com/topic/google-play-instant/feature-module-migration",
        "The com.android.instantapp plugin is deprecated and will be removed by the end of 2019. " +
        "Please switch to using the Android App Bundle to build your instant app. " +
        "For more information on migrating to Android App Bundles, please visit " +
        "https://developer.android.com/topic/google-play-instant/feature-module-migration",
      ),
    )
  )

private fun def(
  stackMarker: (() -> Unit) -> Unit, // Is supposed to be implemented as { it() }.
  scenario: TestScenario,
  IGNORE: TestConfiguration.() -> Unit = { },
  expectPackageName: String,
  expectTestPackageName: String? = null,
  expectSyncIssueContent: List<String> = emptyList(),
  ) = ApplicationIdProviderTest(
  scenario = scenario,
  IGNORE = IGNORE,
  expectPackageName = mapOf(AGP_CURRENT to expectPackageName),
  expectTestPackageName = expectTestPackageName?.let { mapOf(AGP_CURRENT to expectTestPackageName) } ?: emptyMap(),
  stackMarker = stackMarker,
  expectSyncIssueContent = mapOf(AGP_CURRENT to expectSyncIssueContent),
)

private fun def(
  stackMarker: (() -> Unit) -> Unit, // Is supposed to be implemented as { it() }.
  scenario: TestScenario,
  IGNORE: TestConfiguration.() -> Unit = { },
  expectPackageName: Map<AgpVersionSoftwareEnvironmentDescriptor, String>,
  expectTestPackageName: Map<AgpVersionSoftwareEnvironmentDescriptor, String> = emptyMap(),
  expectSyncIssueContent: Map<AgpVersionSoftwareEnvironmentDescriptor, List<String>> = emptyMap(),
) = ApplicationIdProviderTest(
  scenario = scenario,
  IGNORE = IGNORE,
  expectPackageName = expectPackageName,
  expectTestPackageName = expectTestPackageName,
  stackMarker = stackMarker,
  expectSyncIssueContent = expectSyncIssueContent,
)


private data class ApplicationIdProviderTest(
  override val scenario: TestScenario,
  override val IGNORE: TestConfiguration.() -> Unit = { },
  override val stackMarker: (() -> Unit) -> Unit, // Is supposed to be implemented as { it() }.
  val expectPackageName: Map<AgpVersionSoftwareEnvironmentDescriptor, String>,
  val expectTestPackageName: Map<AgpVersionSoftwareEnvironmentDescriptor, String>,
  val expectSyncIssueContent: Map<AgpVersionSoftwareEnvironmentDescriptor, List<String>>,
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
      val syncIssueMessages = ModuleManager.getInstance(project).modules.flatMap { it.syncIssues() }.map { it.message }
      expect.that(syncIssueMessages).containsExactlyElementsIn(expectSyncIssueContent.forVersion() ?: emptyList<String>())
    }
  }
}

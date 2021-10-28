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
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_35
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_40
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
import com.android.tools.idea.gradle.project.sync.ApkProviderIntegrationTestCase.TestConfiguration.ManuallyAssembled
import com.android.tools.idea.gradle.project.sync.ApkProviderIntegrationTestCase.TestConfiguration.NamedAppTargetRunConfiguration
import com.android.tools.idea.gradle.project.sync.ApkProviderIntegrationTestCase.TestConfiguration.TestTargetRunConfiguration
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.getBuiltApksForSelectedVariant
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass
import com.android.tools.idea.testing.AgpIntegrationTestDefinition
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.AGP_41
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.executeMakeBeforeRunStepInTest
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.mockDeviceFor
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.outputCurrentlyRunningTest
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.switchVariant
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.getExternalProjectId
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.runInEdtAndWait
import org.hamcrest.Matchers.nullValue
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.Contract
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.junit.Assume.assumeThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.concurrent.TimeUnit

abstract class ApkProviderIntegrationTestCase : GradleIntegrationTest {

  @RunWith(Parameterized::class)
  class CurrentAgp : ApkProviderIntegrationTestCase() {

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
          name = "COMPOSITE_BUILD :app run configuration",
          testProject = TestProjectPaths.COMPOSITE_BUILD,
          testConfiguration = NamedAppTargetRunConfiguration(externalSystemModuleId = ":app"),
          expectApks =
            """
              ApplicationId: com.test.compositeapp
              File: project/app/build/outputs/apk/debug/app-debug.apk
              Files:
                project.app -> project/app/build/outputs/apk/debug/app-debug.apk
              RequiredInstallationOptions: []
          """.let {
              listOf(
                AGP_35 to it,
                AGP_40 to it,
                AGP_41 to it,
                AGP_CURRENT to """
                    ApplicationId: com.test.compositeapp
                    File: project/app/build/intermediates/apk/debug/app-debug.apk
                    Files:
                      project.app -> project/app/build/intermediates/apk/debug/app-debug.apk
                    RequiredInstallationOptions: []
                """,
              )
            }.toMap(),
        ),
        TestDefinition(
          name = "COMPOSITE_BUILD TestCompositeLib1:app run configuration",
          testProject = TestProjectPaths.COMPOSITE_BUILD,
          testConfiguration = NamedAppTargetRunConfiguration(externalSystemModuleId = "TestCompositeLib1:app"),
          expectApks =
            """
              ApplicationId: com.test.composite1
              File: project/TestCompositeLib1/app/build/outputs/apk/debug/app-debug.apk
              Files:
                TestCompositeLib1.app -> project/TestCompositeLib1/app/build/outputs/apk/debug/app-debug.apk
              RequiredInstallationOptions: []
            """.let {
                  listOf(
                    AGP_35 to it,
                    AGP_40 to it,
                    AGP_41 to it,
                    AGP_CURRENT to """
              ApplicationId: com.test.composite1
              File: project/TestCompositeLib1/app/build/intermediates/apk/debug/app-debug.apk
              Files:
                TestCompositeLib1.app -> project/TestCompositeLib1/app/build/intermediates/apk/debug/app-debug.apk
              RequiredInstallationOptions: []
            """,
                  )
            }.toMap(),
        ),
        TestDefinition(
          name = "APPLICATION_ID_SUFFIX before build",
          testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
          executeMakeBeforeRun = false,
          expectApks = mapOf(
            AGP_CURRENT to """
              ApkProvisionException*> Error loading build artifacts from: <ROOT>/project/app/build/intermediates/apk_ide_redirect_file/debug/redirect.txt
            """,
            AGP_41 to """
              ApkProvisionException*> Error loading build artifacts from: <ROOT>/project/app/build/outputs/apk/debug/output-metadata.json
            """,
            AGP_40 to """
              ApkProvisionException*> Couldn't get post build model. Module: Application_ID_Suffix_Test_App.app Variant: debug
            """,
            AGP_35 to """
              ApkProvisionException*> Couldn't get post build model. Module: Application_ID_Suffix_Test_App.app Variant: debug
            """
          )
        ),
        TestDefinition(
          name = "APPLICATION_ID_SUFFIX run configuration",
          testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
          expectApks =
         """
            ApplicationId: one.name.defaultConfig.debug
            File: project/app/build/outputs/apk/debug/app-debug.apk
            Files:
              Application_ID_Suffix_Test_App.app -> project/app/build/outputs/apk/debug/app-debug.apk
            RequiredInstallationOptions: []
          """.let {
           listOf(
             AGP_35 to it,
             AGP_40 to it,
             AGP_41 to it,
             AGP_CURRENT to """
            ApplicationId: one.name.defaultConfig.debug
            File: project/app/build/intermediates/apk/debug/app-debug.apk
            Files:
              Application_ID_Suffix_Test_App.app -> project/app/build/intermediates/apk/debug/app-debug.apk
            RequiredInstallationOptions: []
          """,
           )
         }.toMap()
        ),
        TestDefinition(
          name = "APPLICATION_ID_SUFFIX run configuration via bundle",
          viaBundle = true,
          testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
          // TODO(b/190357145): Fix ApplicationId when fixed in AGP or decided how to handle this.
          expectApks = mapOf(
            AGP_CURRENT to """
              ApplicationId: one.name
              File: *>java.lang.IllegalArgumentException
              Files:
                base -> project/app/build/intermediates/extracted_apks/debug/base-master.apk
                base -> project/app/build/intermediates/extracted_apks/debug/base-mdpi.apk
              RequiredInstallationOptions: []
            """,
            AGP_40 to """
              ApplicationId: one.name.defaultConfig.debug
              File: *>java.lang.IllegalArgumentException
              Files:
                base -> project/app/build/intermediates/extracted_apks/debug/out/base-master.apk
                base -> project/app/build/intermediates/extracted_apks/debug/out/base-mdpi.apk
              RequiredInstallationOptions: []
            """,
            AGP_35 to """
              ApplicationId: one.name.defaultConfig.debug
              File: *>java.lang.IllegalArgumentException
              Files:
                base -> project/app/build/intermediates/extracted_apks/debug/extractApksForDebug/out/base-master.apk
                base -> project/app/build/intermediates/extracted_apks/debug/extractApksForDebug/out/base-mdpi.apk
              RequiredInstallationOptions: []
            """
          )
        ),
        TestDefinition(
          name = "APPLICATION_ID_SUFFIX assemble",
          testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
          testConfiguration = ManuallyAssembled(":app", forTests = false),
          expectApks = """
            ApplicationId: one.name.defaultConfig.debug
            File: project/app/build/outputs/apk/debug/app-debug.apk
            Files:
              Application_ID_Suffix_Test_App.app -> project/app/build/outputs/apk/debug/app-debug.apk
            RequiredInstallationOptions: []
          """
        ),
        TestDefinition(
          name = "SIMPLE_APPLICATION test run configuration",
          testProject = TestProjectPaths.SIMPLE_APPLICATION,
          testConfiguration = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
          expectApks =
          """
            ApplicationId: google.simpleapplication
            File: project/app/build/outputs/apk/debug/app-debug.apk
            Files:
              project.app -> project/app/build/outputs/apk/debug/app-debug.apk
            RequiredInstallationOptions: []

            ApplicationId: google.simpleapplication.test
            File: project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            Files:
               -> project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            RequiredInstallationOptions: []
          """.let {
              listOf(
                AGP_35 to it,
                AGP_40 to it,
                AGP_41 to it,
                AGP_CURRENT to """
            ApplicationId: google.simpleapplication
            File: project/app/build/intermediates/apk/debug/app-debug.apk
            Files:
              project.app -> project/app/build/intermediates/apk/debug/app-debug.apk
            RequiredInstallationOptions: []

            ApplicationId: google.simpleapplication.test
            File: project/app/build/intermediates/apk/androidTest/debug/app-debug-androidTest.apk
            Files:
               -> project/app/build/intermediates/apk/androidTest/debug/app-debug-androidTest.apk
            RequiredInstallationOptions: []
          """,
              )
            }.toMap()
        ),
        TestDefinition(
          name = "SIMPLE_APPLICATION assemble for tests",
          testProject = TestProjectPaths.SIMPLE_APPLICATION,
          testConfiguration = ManuallyAssembled(":app", forTests = true),
          expectApks = """
            ApplicationId: google.simpleapplication
            File: project/app/build/outputs/apk/debug/app-debug.apk
            Files:
              project.app -> project/app/build/outputs/apk/debug/app-debug.apk
            RequiredInstallationOptions: []

            ApplicationId: google.simpleapplication.test
            File: project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            Files:
               -> project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            RequiredInstallationOptions: []
          """),
        TestDefinition(
          name = "TEST_ONLY_MODULE test run configuration",
          testProject = TestProjectPaths.TEST_ONLY_MODULE,
          testConfiguration = TestTargetRunConfiguration("com.example.android.app.ExampleTest"),
          expectApks = 
          """
            ApplicationId: com.example.android.app
            File: project/app/build/outputs/apk/debug/app-debug.apk
            Files:
               -> project/app/build/outputs/apk/debug/app-debug.apk
            RequiredInstallationOptions: []

            ApplicationId: com.example.android.app.testmodule
            File: project/test/build/outputs/apk/debug/test-debug.apk
            Files:
              project.test -> project/test/build/outputs/apk/debug/test-debug.apk
            RequiredInstallationOptions: []
          """.let {
            listOf(
              AGP_35 to it,
              AGP_40 to it,
              AGP_41 to it,
              AGP_CURRENT to
                """
            ApplicationId: com.example.android.app
            File: project/app/build/intermediates/apk/debug/app-debug.apk
            Files:
               -> project/app/build/intermediates/apk/debug/app-debug.apk
            RequiredInstallationOptions: []

            ApplicationId: com.example.android.app.testmodule
            File: project/test/build/intermediates/apk/debug/test-debug.apk
            Files:
              project.test -> project/test/build/intermediates/apk/debug/test-debug.apk
            RequiredInstallationOptions: []
          """,
            )
          }.toMap(),
        ),
        TestDefinition(
          name = "DYNAMIC_APP run configuration",
          testProject = TestProjectPaths.DYNAMIC_APP,
          expectApks =
          """
            ApplicationId: google.simpleapplication
            File: *>java.lang.IllegalArgumentException
            Files:
              simpleApplication.app -> project/app/build/outputs/apk/debug/app-debug.apk
              simpleApplication.dependsOnFeature1 -> project/dependsOnFeature1/build/outputs/apk/debug/dependsOnFeature1-debug.apk
              simpleApplication.feature1 -> project/feature1/build/outputs/apk/debug/feature1-debug.apk
            RequiredInstallationOptions: []
          """.let {
              listOf(
                AGP_35 to it,
                AGP_40 to it,
                AGP_41 to it,
                AGP_CURRENT to """
            ApplicationId: google.simpleapplication
            File: *>java.lang.IllegalArgumentException
            Files:
              simpleApplication.app -> project/app/build/intermediates/apk/debug/app-debug.apk
              simpleApplication.dependsOnFeature1 -> project/dependsOnFeature1/build/intermediates/apk/debug/dependsOnFeature1-debug.apk
              simpleApplication.feature1 -> project/feature1/build/intermediates/apk/debug/feature1-debug.apk
            RequiredInstallationOptions: []
          """
              )
            }.toMap(),
        ),
        TestDefinition(
          IGNORE = { TODO("b/189190337") },
          name = "DYNAMIC_APP run configuration pre L device",
          device = 19,
          testProject = TestProjectPaths.DYNAMIC_APP,
          expectApks = mapOf(
            AGP_CURRENT to """
            ApplicationId: google.simpleapplication
            File: project/app/build/intermediates/extracted_apks/debug/standalone-mdpi.apk
            Files:
              standalone -> project/app/build/intermediates/extracted_apks/debug/standalone-mdpi.apk
            RequiredInstallationOptions: []
          """,
            AGP_35 to """
            ApplicationId: google.simpleapplication
            File: project/app/build/outputs/extracted_apks/debug/standalone-mdpi.apk
            Files:
              standalone -> project/app/build/outputs/extracted_apks/debug/standalone-mdpi.apk
            RequiredInstallationOptions: []
          """,
          )

        ),
        TestDefinition(
          name = "DYNAMIC_APP test run configuration",
          testProject = TestProjectPaths.DYNAMIC_APP,
          testConfiguration = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
          expectApks =
            """
                ApplicationId: google.simpleapplication
                File: *>java.lang.IllegalArgumentException
                Files:
                  simpleApplication.app -> project/app/build/outputs/apk/debug/app-debug.apk
                  simpleApplication.dependsOnFeature1 -> project/dependsOnFeature1/build/outputs/apk/debug/dependsOnFeature1-debug.apk
                  simpleApplication.feature1 -> project/feature1/build/outputs/apk/debug/feature1-debug.apk
                RequiredInstallationOptions: []

                ApplicationId: google.simpleapplication.test
                File: project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
                Files:
                   -> project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
                RequiredInstallationOptions: []
            """.let {
              listOf(
                AGP_35 to it,
                AGP_40 to it,
                AGP_41 to it,
                AGP_CURRENT to """
              ApplicationId: google.simpleapplication
              File: *>java.lang.IllegalArgumentException
              Files:
                simpleApplication.app -> project/app/build/intermediates/apk/debug/app-debug.apk
                simpleApplication.dependsOnFeature1 -> project/dependsOnFeature1/build/intermediates/apk/debug/dependsOnFeature1-debug.apk
                simpleApplication.feature1 -> project/feature1/build/intermediates/apk/debug/feature1-debug.apk
              RequiredInstallationOptions: []

              ApplicationId: google.simpleapplication.test
              File: project/app/build/intermediates/apk/androidTest/debug/app-debug-androidTest.apk
              Files:
                 -> project/app/build/intermediates/apk/androidTest/debug/app-debug-androidTest.apk
              RequiredInstallationOptions: []
            """,
              )
            }.toMap()
        ),
        TestDefinition(
          IGNORE = { TODO("b/189190337") },
          name = "DYNAMIC_APP test run configuration pre L device",
          device = 19,
          testProject = TestProjectPaths.DYNAMIC_APP,
          testConfiguration = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
          expectApks = mapOf(
            AGP_CURRENT to """
            ApplicationId: google.simpleapplication
            File: project/app/build/intermediates/extracted_apks/debug/standalone-mdpi.apk
            Files:
              standalone -> project/app/build/intermediates/extracted_apks/debug/standalone-mdpi.apk
            RequiredInstallationOptions: []

            ApplicationId: google.simpleapplication.test
            File: project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            Files:
               -> project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            RequiredInstallationOptions: []
          """,
            AGP_35 to """
            ApplicationId: google.simpleapplication
            File: project/app/build/outputs/extracted_apks/debug/standalone-mdpi.apk
            Files:
              standalone -> project/app/build/outputs/extracted_apks/debug/standalone-mdpi.apk
            RequiredInstallationOptions: []

            ApplicationId: google.simpleapplication.test
            File: project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            Files:
               -> project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            RequiredInstallationOptions: []
          """,
          )
        ),
        TestDefinition(
          // Do not run with the current version of the AGP.
          IGNORE = { if (agpVersion == AGP_CURRENT) TODO("b/189202602") },
          name = "DYNAMIC_APP feature test run configuration",
          testProject = TestProjectPaths.DYNAMIC_APP,
          testConfiguration = TestTargetRunConfiguration("com.example.instantapp.ExampleInstrumentedTest"),
          expectApks = mapOf(
            AGP_CURRENT to """
              ApplicationId: google.simpleapplication
              File: *>java.lang.IllegalArgumentException
              Files:
                base -> project/app/build/intermediates/extracted_apks/debug/base-master.apk
                base -> project/app/build/intermediates/extracted_apks/debug/base-mdpi.apk
                dependsOnFeature1 -> project/app/build/intermediates/extracted_apks/debug/dependsOnFeature1-master.apk
                dependsOnFeature1 -> project/app/build/intermediates/extracted_apks/debug/dependsOnFeature1-mdpi.apk
                feature1 -> project/app/build/intermediates/extracted_apks/debug/feature1-master.apk
                feature1 -> project/app/build/intermediates/extracted_apks/debug/feature1-mdpi.apk
              RequiredInstallationOptions: []

              ApplicationId: com.example.feature1.test
              File: project/feature1/build/outputs/apk/androidTest/debug/feature1-debug-androidTest.apk
              Files:
                 -> project/feature1/build/outputs/apk/androidTest/debug/feature1-debug-androidTest.apk
              RequiredInstallationOptions: []
            """,
            AGP_40 to """
              ApplicationId: google.simpleapplication
              File: *>java.lang.IllegalArgumentException
              Files:
                base -> project/app/build/intermediates/extracted_apks/debug/out/base-master.apk
                base -> project/app/build/intermediates/extracted_apks/debug/out/base-mdpi.apk
                dependsOnFeature1 -> project/app/build/intermediates/extracted_apks/debug/out/dependsOnFeature1-master.apk
                dependsOnFeature1 -> project/app/build/intermediates/extracted_apks/debug/out/dependsOnFeature1-mdpi.apk
                feature1 -> project/app/build/intermediates/extracted_apks/debug/out/feature1-master.apk
                feature1 -> project/app/build/intermediates/extracted_apks/debug/out/feature1-mdpi.apk
              RequiredInstallationOptions: []

              ApplicationId: com.example.feature1.test
              File: project/feature1/build/outputs/apk/androidTest/debug/feature1-debug-androidTest.apk
              Files:
                 -> project/feature1/build/outputs/apk/androidTest/debug/feature1-debug-androidTest.apk
              RequiredInstallationOptions: []
            """,
            AGP_35 to """
              ApplicationId: google.simpleapplication
              File: *>java.lang.IllegalArgumentException
              Files:
                base -> project/app/build/intermediates/extracted_apks/debug/extractApksForDebug/out/base-master.apk
                base -> project/app/build/intermediates/extracted_apks/debug/extractApksForDebug/out/base-mdpi.apk
                dependsOnFeature1 -> project/app/build/intermediates/extracted_apks/debug/extractApksForDebug/out/dependsOnFeature1-master.apk
                dependsOnFeature1 -> project/app/build/intermediates/extracted_apks/debug/extractApksForDebug/out/dependsOnFeature1-mdpi.apk
                feature1 -> project/app/build/intermediates/extracted_apks/debug/extractApksForDebug/out/feature1-master.apk
                feature1 -> project/app/build/intermediates/extracted_apks/debug/extractApksForDebug/out/feature1-mdpi.apk
              RequiredInstallationOptions: []

              ApplicationId: com.example.feature1.test
              File: project/feature1/build/outputs/apk/androidTest/debug/feature1-debug-androidTest.apk
              Files:
                 -> project/feature1/build/outputs/apk/androidTest/debug/feature1-debug-androidTest.apk
              RequiredInstallationOptions: []
            """
          )
        ),
        TestDefinition(
          name = "BUDDY_APKS test run configuration",
          testProject = TestProjectPaths.BUDDY_APKS,
          testConfiguration = TestTargetRunConfiguration("google.testapplication.ApplicationTest"),
          expectApks =
          """
            ApplicationId: google.testapplication
            File: project/app/build/outputs/apk/debug/app-debug.apk
            Files:
              project.app -> project/app/build/outputs/apk/debug/app-debug.apk
            RequiredInstallationOptions: []

            ApplicationId: google.testapplication.test
            File: project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            Files:
               -> project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
            RequiredInstallationOptions: []

            ApplicationId: com.linkedin.android.testbutler
            File: <M2>/com/linkedin/testbutler/test-butler-app/1.3.1/test-butler-app-1.3.1.apk
            Files:
               -> <M2>/com/linkedin/testbutler/test-butler-app/1.3.1/test-butler-app-1.3.1.apk
            RequiredInstallationOptions: [FORCE_QUERYABLE, GRANT_ALL_PERMISSIONS]
          """.let {
            listOf(
              AGP_35 to it,
              AGP_40 to it,
              AGP_41 to it,
              AGP_CURRENT to """
            ApplicationId: google.testapplication
            File: project/app/build/intermediates/apk/debug/app-debug.apk
            Files:
              project.app -> project/app/build/intermediates/apk/debug/app-debug.apk
            RequiredInstallationOptions: []

            ApplicationId: google.testapplication.test
            File: project/app/build/intermediates/apk/androidTest/debug/app-debug-androidTest.apk
            Files:
               -> project/app/build/intermediates/apk/androidTest/debug/app-debug-androidTest.apk
            RequiredInstallationOptions: []

            ApplicationId: com.linkedin.android.testbutler
            File: <M2>/com/linkedin/testbutler/test-butler-app/1.3.1/test-butler-app-1.3.1.apk
            Files:
               -> <M2>/com/linkedin/testbutler/test-butler-app/1.3.1/test-butler-app-1.3.1.apk
            RequiredInstallationOptions: [FORCE_QUERYABLE, GRANT_ALL_PERMISSIONS]
          """
            )
          }.toMap()
        ),
        TestDefinition(
          name = "SIMPLE_APPLICATION validate release",
          testProject = TestProjectPaths.SIMPLE_APPLICATION,
          variant = ":app" to "release",
          expectValidate = mapOf(
            AGP_CURRENT to "The apk for your currently selected variant cannot be signed. " +
              "Please specify a signing configuration for this variant (release).",
            AGP_40 to "The apk for your currently selected variant cannot be signed. " +
              "Please specify a signing configuration for this variant (release).",
            AGP_35 to "The apk for your currently selected variant cannot be signed. " +
              "Please specify a signing configuration for this variant (release)."
          ),
          expectApks =
            """
              ApplicationId: google.simpleapplication
              File: project/app/build/outputs/apk/release/app-release-unsigned.apk
              Files:
                project.app -> project/app/build/outputs/apk/release/app-release-unsigned.apk
              RequiredInstallationOptions: []
            """.let {
              listOf(
                AGP_35 to it,
                AGP_40 to it,
                AGP_41 to it,
                AGP_CURRENT to """
              ApplicationId: google.simpleapplication
              File: project/app/build/intermediates/apk/release/app-release-unsigned.apk
              Files:
                project.app -> project/app/build/intermediates/apk/release/app-release-unsigned.apk
              RequiredInstallationOptions: []
            """,
              )
            }.toMap()
        ),
      )
  }

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels()

  @get:Rule
  var testName = TestName()

  sealed class TestConfiguration {
    open class NamedAppTargetRunConfiguration(val externalSystemModuleId: String?) : TestConfiguration()
    object AppTargetRunConfiguration : NamedAppTargetRunConfiguration(externalSystemModuleId = null)
    class TestTargetRunConfiguration(val testClassFqn: String) : TestConfiguration()
    class ManuallyAssembled(val gradlePath: String, val forTests: Boolean = false) : TestConfiguration()
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
    val testConfiguration: TestConfiguration = TestConfiguration.AppTargetRunConfiguration,
    val expectApks: Map<AgpVersionSoftwareEnvironmentDescriptor, String> = mapOf(),
    val expectValidate: Map<AgpVersionSoftwareEnvironmentDescriptor, String> = mapOf(),
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
      testConfiguration: TestConfiguration = TestConfiguration.AppTargetRunConfiguration,
      expectApks: String,
      expectValidate: String? = null
    ) : this(
      IGNORE = IGNORE,
      name = name,
      viaBundle = viaBundle,
      device = device,
      testProject = testProject,
      variant = variant,
      agpVersion = agpVersion,
      executeMakeBeforeRun = executeMakeBeforeRun,
      testConfiguration = testConfiguration,
      expectApks = mapOf(AGP_CURRENT to expectApks),
      expectValidate = expectValidate?.let { mapOf(AGP_CURRENT to expectValidate) } ?: emptyMap()
    )

    override fun toString(): String = displayName()

    override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): TestDefinition = copy(agpVersion = agpVersion)
  }

  @JvmField
  @Parameterized.Parameter(0)
  var testDefinition: TestDefinition? = null

  @Test
  fun testApkProvider() {
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
        val device = mockDeviceFor(device, listOf(Abi.X86, Abi.X86_64), density = 160)

        fun getApkProviderFromRunConfiguration(runConfiguration: AndroidRunConfigurationBase): ApkProvider {
          if (executeMakeBeforeRun) {
            runConfiguration.executeMakeBeforeRunStepInTest(device)
          }
          return project.getProjectSystem().getApkProvider(runConfiguration)!!
        }

        fun getApkProviderByRunningGradleAssembleTask(
          gradlePath: String,
          forTests: Boolean
        ): ApkProvider {
          val module = project.gradleModule(gradlePath)!!
          val androidFacet = AndroidFacet.getInstance(module)!!
          val assembleResult = try {
            GradleBuildInvoker.getInstance(project)
              .assemble(arrayOf(module), if (forTests) TestCompileType.ANDROID_TESTS else TestCompileType.NONE)
              .get(3, TimeUnit.MINUTES)
          }
          finally {
            runInEdtAndWait {
              AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(project)
            }
          }

          return object : ApkProvider {
            override fun validate(): List<ValidationError> = emptyList()
            override fun getApks(ignored: IDevice): Collection<ApkInfo> =
              assembleResult.getBuiltApksForSelectedVariant(androidFacet, forTests).orEmpty()
          }
        }

        val apkProviderGetter: () -> ApkProvider = runReadAction {
          when (testConfiguration) {
            is NamedAppTargetRunConfiguration ->
              RunManager
                .getInstance(project)
                .allConfigurationsList
                .filterIsInstance<AndroidRunConfiguration>()
                .single {
                  testConfiguration.externalSystemModuleId == null ||
                  it.modules.any { module -> getExternalProjectId(module) == testConfiguration.externalSystemModuleId }
                }
                .also {
                  it.DEPLOY_APK_FROM_BUNDLE = viaBundle
                }.let { { getApkProviderFromRunConfiguration(it) } }
            is TestTargetRunConfiguration ->
              createAndroidTestConfigurationFromClass(project, testConfiguration.testClassFqn)!!
                .let { { getApkProviderFromRunConfiguration(it) } }
            is ManuallyAssembled ->
              if (viaBundle) error("viaBundle mode is not supported with ManuallyAssembled test configurations")
              else
                fun(): ApkProvider =
                  getApkProviderByRunningGradleAssembleTask(testConfiguration.gradlePath, testConfiguration.forTests)
          }
        }

        val apkProvider = apkProviderGetter()
        assertThat(apkProvider.validate().joinToString { it.message }).isEqualTo(expectValidate.forVersion())

        val apks = runCatching { apkProvider.getApks(device) }
        assertThat(apks.toTestString()).isEqualTo(expectApks.forVersion())
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

  private fun ApkInfo.toTestString(): String {
    val filesString = files
      .sortedBy { it.apkFile.toTestString() }
      .joinToString("\n        ") { "${it.moduleName} -> ${it.apkFile.toTestString()}" }
    return """
      ApplicationId: ${this.applicationId}
      File: ${runCatching { file.toTestString() }.let { it.getOrNull() ?: "*>${it.exceptionOrNull()}" }}
      Files:
        $filesString
      RequiredInstallationOptions: ${this.requiredInstallOptions}""".trimIndent()
  }

  private fun Collection<ApkInfo>.toTestString() = joinToString("\n\n") { it.toTestString() }

  private fun Result<Collection<ApkInfo>>.toTestString() =
    getOrNull()?.toTestString()
    ?: exceptionOrNull()?.let {
      val message = it.message?.replace(getBaseTestPath(), "<ROOT>")
      "${it::class.java.simpleName}*> $message"
    }.orEmpty()
}

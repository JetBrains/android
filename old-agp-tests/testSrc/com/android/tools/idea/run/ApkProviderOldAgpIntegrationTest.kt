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
package com.android.tools.idea.run

import com.android.tools.idea.gradle.project.sync.ApkProviderIntegrationTestCase
import com.android.tools.idea.testing.TestProjectPaths
import org.jetbrains.annotations.Contract
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ApkProviderOldAgpIntegrationTest : ApkProviderIntegrationTestCase() {

  companion object {
    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testProjects(): Collection<*> {
      return tests.map { listOf(it).toTypedArray() }
    }

    private val baseDefinition = TestDefinition(gradleVersion = "5.5", agpVersion = "3.5.0")

    private val tests = listOf(
      baseDefinition.copy(
        name = "APPLICATION_ID_SUFFIX before build",
        testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
        executeMakeBeforeRun = false,
        expectApks = """
          ApkProvisionException*> Couldn't get post build model. Module: Application_ID_Suffix_Test_App.app Variant: debug
        """
      ),
      baseDefinition.copy(
        name = "APPLICATION_ID_SUFFIX run configuration",
        testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
        expectApks = """
            ApplicationId: one.name.debug
            File: project/app/build/outputs/apk/debug/app-debug.apk
            Files:
              Application_ID_Suffix_Test_App.app -> project/app/build/outputs/apk/debug/app-debug.apk
            RequiredInstallationOptions: []
          """
      ),
      baseDefinition.copy(
        name = "SIMPLE_APPLICATION test run configuration",
        testProject = TestProjectPaths.SIMPLE_APPLICATION,
        targetRunConfiguration = TargetRunConfiguration.TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
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
          """
      ),
      baseDefinition.copy(
        name = "TEST_ONLY_MODULE test run configuration",
        testProject = TestProjectPaths.TEST_ONLY_MODULE,
        targetRunConfiguration = TargetRunConfiguration.TestTargetRunConfiguration("com.example.android.app.ExampleTest"),
        expectApks = """
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
          """
      ),
      baseDefinition.copy(
        name = "DYNAMIC_APP run configuration",
        testProject = TestProjectPaths.DYNAMIC_APP,
        expectApks = """
            ApplicationId: google.simpleapplication
            File: *>java.lang.IllegalArgumentException
            Files:
              simpleApplication.app -> project/app/build/outputs/apk/debug/app-debug.apk
              simpleApplication.dependsOnFeature1 -> project/dependsOnFeature1/build/outputs/apk/debug/dependsOnFeature1-debug.apk
              simpleApplication.feature1 -> project/feature1/build/outputs/apk/debug/feature1-debug.apk
            RequiredInstallationOptions: []
          """
      ),
      baseDefinition.copy(
        name = "DYNAMIC_APP test run configuration",
        testProject = TestProjectPaths.DYNAMIC_APP,
        targetRunConfiguration = TargetRunConfiguration.TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
        expectApks = """
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
          """
      ),
      baseDefinition.copy(
        name = "DYNAMIC_APP feature test run configuration",
        testProject = TestProjectPaths.DYNAMIC_APP,
        targetRunConfiguration = TargetRunConfiguration.TestTargetRunConfiguration("com.example.instantapp.ExampleInstrumentedTest"),
        expectApks = """
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
      ),
      baseDefinition.copy(
        name = "BUDDY_APKS test run configuration",
        testProject = TestProjectPaths.BUDDY_APKS,
        targetRunConfiguration = TargetRunConfiguration.TestTargetRunConfiguration("google.testapplication.ApplicationTest"),
        expectApks = """
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
          """
      ),
      baseDefinition.copy(
        name = "SIMPLE_APPLICATION validate release",
        testProject = TestProjectPaths.SIMPLE_APPLICATION,
        variant = ":app" to "release",
        expectValidate = "The apk for your currently selected variant (app-release.apk) is not signed. " +
                         "Please specify a signing configuration for this variant (release).",
        expectApks = """
            ApplicationId: google.simpleapplication
            File: project/app/build/outputs/apk/release/app-release-unsigned.apk
            Files:
              project.app -> project/app/build/outputs/apk/release/app-release-unsigned.apk
            RequiredInstallationOptions: []
          """
      ),
    )
  }
}
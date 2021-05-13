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
import com.android.tools.idea.gradle.project.sync.ApkProviderIntegrationTestCase.TargetRunConfiguration.TestTargetRunConfiguration
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.testartifacts.TestConfigurationTesting.createAndroidTestConfigurationFromClass
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.switchVariant
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.Contract
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

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

      private val tests = listOf(
        TestDefinition(
          name = "APPLICATION_ID_SUFFIX before build",
          testProject = TestProjectPaths.APPLICATION_ID_SUFFIX,
          executeMakeBeforeRun = false,
          expectApks = """
            ApkProvisionException*> Error loading build artifacts from: <ROOT>/project/app/build/outputs/apk/debug/output-metadata.json
          """
        ),
        TestDefinition(
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
        TestDefinition(
          name = "SIMPLE_APPLICATION test run configuration",
          testProject = TestProjectPaths.SIMPLE_APPLICATION,
          targetRunConfiguration = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
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
        TestDefinition(
          name = "TEST_ONLY_MODULE test run configuration",
          testProject = TestProjectPaths.TEST_ONLY_MODULE,
          targetRunConfiguration = TestTargetRunConfiguration("com.example.android.app.ExampleTest"),
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
        TestDefinition(
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
        TestDefinition(
          name = "DYNAMIC_APP test run configuration",
          testProject = TestProjectPaths.DYNAMIC_APP,
          targetRunConfiguration = TestTargetRunConfiguration("google.simpleapplication.ApplicationTest"),
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
        TestDefinition(
          name = "DYNAMIC_APP feature test run configuration",
          testProject = TestProjectPaths.DYNAMIC_APP,
          targetRunConfiguration = TestTargetRunConfiguration("com.example.instantapp.ExampleInstrumentedTest"),
          expectApks = """
            ApplicationId: google.simpleapplication
            File: *>java.lang.IllegalArgumentException
            Files:
              base -> project/app/build/intermediates/extracted_apks/debug/base-master.apk
              base -> project/app/build/intermediates/extracted_apks/debug/base-mdpi.apk
            RequiredInstallationOptions: []

            ApplicationId: com.example.feature1.test
            File: project/feature1/build/outputs/apk/androidTest/debug/feature1-debug-androidTest.apk
            Files:
               -> project/feature1/build/outputs/apk/androidTest/debug/feature1-debug-androidTest.apk
            RequiredInstallationOptions: []
          """
        ),
        TestDefinition(
          name = "BUDDY_APKS test run configuration",
          testProject = TestProjectPaths.BUDDY_APKS,
          targetRunConfiguration = TestTargetRunConfiguration("google.testapplication.ApplicationTest"),
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
        TestDefinition(
          name = "SIMPLE_APPLICATION validate release",
          testProject = TestProjectPaths.SIMPLE_APPLICATION,
          variant = ":app" to "release",
          expectValidate = "The apk for your currently selected variant (app-release-unsigned.apk) is not signed." +
                           " Please specify a signing configuration for this variant (release).",
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

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels()

  @get:Rule
  var testName = TestName()

  private val device = mockDeviceFor(30, listOf(Abi.X86_64, Abi.X86))

  sealed class TargetRunConfiguration {
    object AppTargetRunConfiguration : TargetRunConfiguration()
    data class TestTargetRunConfiguration(val testClassFqn: String) : TargetRunConfiguration()
  }

  data class TestDefinition(
    val name: String = "",
    val testProject: String = "",
    val variant: Pair<String, String>? = null,
    val agpVersion: String? = null,
    val gradleVersion: String? = null,
    val executeMakeBeforeRun: Boolean = true,
    val targetRunConfiguration: TargetRunConfiguration = TargetRunConfiguration.AppTargetRunConfiguration,
    val expectApks: String = "",
    val expectValidate: String = ""
  ) {
    override fun toString(): String = name
  }

  @JvmField
  @Parameterized.Parameter(0)
  var testDefinition: TestDefinition? = null

  @Test
  fun testApkProvider() {
    with(testDefinition!!) {
      prepareGradleProject(testProject, "project", gradleVersion = gradleVersion, gradlePluginVersion = agpVersion)

      openPreparedProject("project") { project ->
        if (variant != null) {
          switchVariant(project, variant.first, variant.second)
        }
        val runConfiguration = runReadAction {
          when (targetRunConfiguration) {
            TargetRunConfiguration.AppTargetRunConfiguration ->
              RunManager.getInstance(project).allConfigurationsList.filterIsInstance<AndroidRunConfiguration>().single()
            is TestTargetRunConfiguration ->
              createAndroidTestConfigurationFromClass(project, targetRunConfiguration.testClassFqn)!!
          }
        }
        if (executeMakeBeforeRun) {
          runConfiguration.executeMakeBeforeRunStepInTest(device)
        }
        val apkProvider = project.getProjectSystem().getApkProvider(runConfiguration)!!

        assertThat(apkProvider.validate().joinToString { it.message }).isEqualTo(expectValidate.trimIndent())

        val apks = runCatching { apkProvider.getApks(device) }
        assertThat(apks.toTestString()).isEqualTo(expectApks.trimIndent())
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

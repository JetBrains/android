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
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvisionException
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
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

class ApkProviderIntegrationTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels()

  @get:Rule
  var testName = TestName()

  private val device = mockDeviceFor(30, listOf(Abi.X86_64, Abi.X86))

  @Test
  fun `APPLICATION_ID_SUFFIX before build`() {
    prepareGradleProject(TestProjectPaths.APPLICATION_ID_SUFFIX, "project")
    openPreparedProject("project") { project ->
      val runConfiguration = RunManager.getInstance(project).allConfigurationsList.filterIsInstance<AndroidRunConfiguration>().single()
      val apkProvider = project.getProjectSystem().getApkProvider(runConfiguration)!!

      val apks = runCatching { apkProvider.getApks(device) }
      assertThat(apks.exceptionOrNull()).isInstanceOf(ApkProvisionException::class.java)
    }
  }

  @Test
  fun `APPLICATION_ID_SUFFIX run configuration`() {
    prepareGradleProject(TestProjectPaths.APPLICATION_ID_SUFFIX, "project")
    openPreparedProject("project") { project ->
      val runConfiguration = runReadAction {
        RunManager.getInstance(project).allConfigurationsList.filterIsInstance<AndroidRunConfiguration>().single()
      }
      runConfiguration.executeMakeBeforeRunStepInTest(device)
      val apkProvider = project.getProjectSystem().getApkProvider(runConfiguration)!!

      val apks = runCatching { apkProvider.getApks(device) }
      assertThat(apks.toTestString()).isEqualTo("""
        ApplicationId: one.name.debug
        File: project/app/build/outputs/apk/debug/app-debug.apk
        Files:
          Application_ID_Suffix_Test_App.app -> project/app/build/outputs/apk/debug/app-debug.apk
        RequiredInstallationOptions: []""".trimIndent())
      assertThat(apkProvider.validate()).isEmpty()
    }
  }

  @Test
  fun `SIMPLE_APPLICATION test run configuration`() {
    prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")

    openPreparedProject("project") { project ->
      val runConfiguration = runReadAction {
        createAndroidTestConfigurationFromClass(project, "google.simpleapplication.ApplicationTest")!!
      }
      runConfiguration.executeMakeBeforeRunStepInTest(device)
      val apkProvider = project.getProjectSystem().getApkProvider(runConfiguration)!!

      val apks = runCatching { apkProvider.getApks(device) }
      assertThat(apks.toTestString()).isEqualTo("""
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
      """.trimIndent())
      assertThat(apkProvider.validate()).isEmpty()
    }
  }

  @Test
  fun `TEST_ONLY_MODULE test run configuration`() {
    prepareGradleProject(TestProjectPaths.TEST_ONLY_MODULE, "project")

    openPreparedProject("project") { project ->
      val runConfiguration = runReadAction {
        createAndroidTestConfigurationFromClass(project, "com.example.android.app.ExampleTest")!!
      }
      runConfiguration.executeMakeBeforeRunStepInTest(device)
      val apkProvider = project.getProjectSystem().getApkProvider(runConfiguration)!!

      val apks = runCatching { apkProvider.getApks(device) }
      assertThat(apks.toTestString()).isEqualTo("""
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
      """.trimIndent())
      assertThat(apkProvider.validate()).isEmpty()
    }
  }

  @Test
  fun `DYNAMIC_APP run configuration`() {
    prepareGradleProject(TestProjectPaths.DYNAMIC_APP, "project")
    openPreparedProject("project") { project ->
      val runConfiguration = runReadAction {
        RunManager.getInstance(project).allConfigurationsList.filterIsInstance<AndroidRunConfiguration>().single()
      }
      runConfiguration.executeMakeBeforeRunStepInTest(device)
      val apkProvider = project.getProjectSystem().getApkProvider(runConfiguration)!!

      val apks = runCatching { apkProvider.getApks(device) }
      assertThat(apks.toTestString()).isEqualTo("""
        ApplicationId: google.simpleapplication
        File: *>java.lang.IllegalArgumentException
        Files:
          simpleApplication.app -> project/app/build/outputs/apk/debug/app-debug.apk
          simpleApplication.dependsOnFeature1 -> project/dependsOnFeature1/build/outputs/apk/debug/dependsOnFeature1-debug.apk
          simpleApplication.feature1 -> project/feature1/build/outputs/apk/debug/feature1-debug.apk
        RequiredInstallationOptions: []
      """.trimIndent())
      assertThat(apkProvider.validate()).isEmpty()
    }
  }

  @Test
  fun `DYNAMIC_APP test run configuration`() {
    prepareGradleProject(TestProjectPaths.DYNAMIC_APP, "project")

    openPreparedProject("project") { project ->
      val runConfiguration = runReadAction {
        createAndroidTestConfigurationFromClass(project, "google.simpleapplication.ApplicationTest")!!
      }
      runConfiguration.executeMakeBeforeRunStepInTest(device)
      val apkProvider = project.getProjectSystem().getApkProvider(runConfiguration)!!

      val apks = runCatching { apkProvider.getApks(device) }
      assertThat(apks.toTestString()).isEqualTo("""
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
      """.trimIndent())
      assertThat(apkProvider.validate()).isEmpty()
    }
  }

  @Test
  fun `DYNAMIC_APP feature test run configuration`() {
    prepareGradleProject(TestProjectPaths.DYNAMIC_APP, "project")

    openPreparedProject("project") { project ->
      val runConfiguration = runReadAction {
        createAndroidTestConfigurationFromClass(project, "com.example.instantapp.ExampleInstrumentedTest")!!
      }
      runConfiguration.executeMakeBeforeRunStepInTest(device)
      val apkProvider = project.getProjectSystem().getApkProvider(runConfiguration)!!

      val apks = runCatching { apkProvider.getApks(device) }
      assertThat(apks.toTestString()).isEqualTo("""
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
      """.trimIndent())
      assertThat(apkProvider.validate()).isEmpty()
    }
  }

  @Test
  fun `BUDDY_APKS test run configuration`() {
    prepareGradleProject(TestProjectPaths.BUDDY_APKS, "project")

    openPreparedProject("project") { project ->
      val runConfiguration = runReadAction {
        createAndroidTestConfigurationFromClass(project, "google.testapplication.ApplicationTest")!!
      }
      runConfiguration.executeMakeBeforeRunStepInTest(device)
      val apkProvider = project.getProjectSystem().getApkProvider(runConfiguration)!!

      val apks = runCatching { apkProvider.getApks(device) }
      assertThat(apks.toTestString()).isEqualTo("""
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
      """.trimIndent())
      assertThat(apkProvider.validate()).isEmpty()
    }
  }

  @Test
  fun `SIMPLE_APPLICATION validate release`() {
    prepareGradleProject(TestProjectPaths.SIMPLE_APPLICATION, "project")

    openPreparedProject("project") { project ->
      switchVariant(project, ":app", "release")
      val runConfiguration = runReadAction {
        RunManager.getInstance(project).allConfigurationsList.filterIsInstance<AndroidRunConfiguration>().single()
      }
      runConfiguration.executeMakeBeforeRunStepInTest(device)
      val apkProvider = project.getProjectSystem().getApkProvider(runConfiguration)!!

      assertThat(apkProvider.validate().joinToString { it.message })
        .isEqualTo("The apk for your currently selected variant (app-release-unsigned.apk) is not signed." +
                   " Please specify a signing configuration for this variant (release).")

      val apks = runCatching { apkProvider.getApks(device) }
      assertThat(apks.toTestString()).isEqualTo("""
        ApplicationId: google.simpleapplication
        File: project/app/build/outputs/apk/release/app-release-unsigned.apk
        Files:
          project.app -> project/app/build/outputs/apk/release/app-release-unsigned.apk
        RequiredInstallationOptions: []
      """.trimIndent())
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

  private fun Result<Collection<ApkInfo>>.toTestString() = getOrNull()?.toTestString() ?: exceptionOrNull()?.message.orEmpty()
}

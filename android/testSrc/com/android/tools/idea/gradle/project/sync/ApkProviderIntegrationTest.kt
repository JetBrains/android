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

import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.GradleApkProvider
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.RunManager
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File

class ApkProviderIntegrationTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels()

  @get:Rule
  var testName = TestName()

  @Test
  fun `APPLICATION_ID_SUFFIX before build`() {
    prepareGradleProject(TestProjectPaths.APPLICATION_ID_SUFFIX, "project")
    openPreparedProject("project") { project ->
      val runConfiguration = RunManager.getInstance(project).allConfigurationsList.filterIsInstance<AndroidRunConfiguration>().single()
      // Testing through GradleApkProvider to avoid stubbing of IDevice.
      val apkProvider = project.getProjectSystem().getApkProvider(runConfiguration) as GradleApkProvider

      val apks = runCatching { apkProvider.getApks(listOf(Abi.X86_64.toString(), Abi.X86.toString()), AndroidVersion(30)) }
      assertThat(apks.exceptionOrNull()).isInstanceOf(ApkProvisionException::class.java)
    }
  }

  @Test
  fun `APPLICATION_ID_SUFFIX after build`() {
    prepareGradleProject(TestProjectPaths.APPLICATION_ID_SUFFIX, "project")
    openPreparedProject("project") { project ->
      val runConfiguration = RunManager.getInstance(project).allConfigurationsList.filterIsInstance<AndroidRunConfiguration>().single()
      runConfiguration.executeMakeBeforeRunStepInTest()
      // Testing through GradleApkProvider to avoid stubbing of IDevice.
      val apkProvider = project.getProjectSystem().getApkProvider(runConfiguration) as GradleApkProvider

      val apks = runCatching { apkProvider.getApks(listOf(Abi.X86_64.toString(), Abi.X86.toString()), AndroidVersion(30)) }
      assertThat(apks.toTestString()).isEqualTo("""
        ApplicationId: one.name.debug
        File: project/app/build/outputs/apk/debug/app-debug.apk
        Files: Application_ID_Suffix_Test_App.app -> project/app/build/outputs/apk/debug/app-debug.apk
        RequiredInstallationOptions: []""".trimIndent())
    }
  }

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = TestProjectPaths.TEST_DATA_PATH
  override fun getAdditionalRepos(): Collection<File> = listOf()

  private fun ApkInfo.toTestString() = """
    ApplicationId: ${this.applicationId}
    File: ${this.file.relativeTo(File(getBaseTestPath()))}
    Files: ${this.files.joinToString("\n      ") { "${it.moduleName} -> ${it.apkFile.relativeTo(File(getBaseTestPath()))}" }}
    RequiredInstallationOptions: ${this.requiredInstallOptions}""".trimIndent()

  private fun List<ApkInfo>.toTestString() = joinToString("\n\n") { it.toTestString() }

  private fun Result<List<ApkInfo>>.toTestString() = getOrNull()?.toTestString() ?: exceptionOrNull()?.message.orEmpty()
}

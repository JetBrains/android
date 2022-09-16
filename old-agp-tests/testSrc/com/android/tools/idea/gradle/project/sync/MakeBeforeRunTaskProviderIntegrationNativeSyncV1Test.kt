/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.executeMakeBeforeRunStepInTest
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.mockDeviceFor
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.testing.withSimulatedSyncError
import com.google.common.truth.Truth
import com.intellij.execution.RunManager
import org.junit.Assert.fail
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

@OldAgpTest(agpVersions = ["4.1.0"], gradleVersions = ["6.7.1"])
@Ignore("b/247097801")
class MakeBeforeRunTaskProviderIntegrationNativeSyncV1Test : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels()

  @Test
  fun testModelsAreNotFetchedForSyncedAbi() {
    prepareGradleProject(
      testProjectPath = TestProjectPaths.DEPENDENT_NATIVE_MODULES,
      name = "project",
      agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_41,
    )
    openPreparedProject("project") { project ->
      val selectedVariant = NdkFacet.getInstance(project.gradleModule(":app") ?: error(":app module not found"))?.selectedVariantAbi
      Truth.assertThat(selectedVariant?.abi).isEqualTo(Abi.X86.toString())

      fun attemptRunningOn(abi: Abi) {
        withSimulatedSyncError(errorMessage) {
          val runConfiguration = RunManager.getInstance(project).allConfigurationsList.filterIsInstance<AndroidRunConfiguration>().single()
          runConfiguration.executeMakeBeforeRunStepInTest(DeviceFutures.forDevices(listOf(mockDeviceFor(23, listOf(abi)))))
        }
      }

      // Running on X86 should not require any additional models.
      attemptRunningOn(Abi.X86)

      // Make sure simulated sync exceptions work in this context and the call above indeed passes without fetching any models.
      try {
        attemptRunningOn(Abi.ARMEABI_V7A)
        fail("resolveProjectInfo() is expected to run and throw a simulated exception")
      }
      catch (e: Exception) {
        Truth.assertThat(e.message).contains(errorMessage)
      }
    }
  }

  @Test
  fun testModelsAreFetchedForNotSyncedAbi() {
    prepareGradleProject(
      testProjectPath = TestProjectPaths.DEPENDENT_NATIVE_MODULES,
      name = "project",
      agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_41,
    )
    openPreparedProject("project") { project ->
      val ndkFacet = NdkFacet.getInstance(project.gradleModule(":app") ?: error(":app module not found"))
      val selectedVariant = ndkFacet?.selectedVariantAbi
      Truth.assertThat(selectedVariant?.abi).isEqualTo(Abi.X86.toString())
      Truth.assertThat(ndkFacet?.ndkModuleModel?.ndkModel?.syncedVariantAbis?.map { it.abi }).containsExactly(Abi.X86.toString())

      fun runOn(abi: Abi) {
        val runConfiguration = RunManager.getInstance(project).allConfigurationsList.filterIsInstance<AndroidRunConfiguration>().single()
        runConfiguration.executeMakeBeforeRunStepInTest(DeviceFutures.forDevices(listOf(mockDeviceFor(23, listOf(abi)))))
      }

      runOn(Abi.ARMEABI_V7A)

      Truth.assertThat(selectedVariant?.abi).isEqualTo(Abi.X86.toString())
      Truth.assertThat(ndkFacet?.ndkModuleModel?.ndkModel?.syncedVariantAbis?.map { it.abi })
        .containsExactly(Abi.X86.toString(), Abi.ARMEABI_V7A.toString())

      // Assert that a subsequent run on ARM should not trigger sync again
      fun attemptRunningOn(abi: Abi) {
        withSimulatedSyncError(errorMessage) {
          val runConfiguration = RunManager.getInstance(project).allConfigurationsList.filterIsInstance<AndroidRunConfiguration>().single()
          runConfiguration.executeMakeBeforeRunStepInTest(DeviceFutures.forDevices(listOf(mockDeviceFor(23, listOf(abi)))))
        }
      }

      attemptRunningOn(Abi.ARMEABI_V7A)

      Truth.assertThat(selectedVariant?.abi).isEqualTo(Abi.X86.toString())
      Truth.assertThat(ndkFacet?.ndkModuleModel?.ndkModel?.syncedVariantAbis?.map { it.abi })
        .containsExactly(Abi.X86.toString(), Abi.ARMEABI_V7A.toString())
    }
  }

  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = TestProjectPaths.TEST_DATA_PATH
  override fun getAdditionalRepos(): Collection<File> = listOf()
}

private const val errorMessage: String = "Unexpected attempt to resolve a project."

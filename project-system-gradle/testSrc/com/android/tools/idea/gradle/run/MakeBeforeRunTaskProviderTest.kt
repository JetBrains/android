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
package com.android.tools.idea.gradle.run

import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider.SyncNeeded
import com.android.tools.idea.run.AndroidDevice
import com.android.tools.idea.run.AndroidDeviceSpec
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.google.common.base.Charsets
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.PlatformTestCase
import com.intellij.util.ThreeState
import org.apache.commons.io.FileUtils
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations.initMocks
import org.mockito.invocation.InvocationOnMock
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class MakeBeforeRunTaskProviderTest : PlatformTestCase() {
  @Mock
  private lateinit var myDevice: AndroidDevice
  @Mock
  private lateinit var myLaunchedDevice: IDevice
  @Mock
  private lateinit var myRunConfiguration: AndroidRunConfiguration

  private lateinit var myModules: Array<Module>

  override fun setUp() {
    super.setUp()
    initMocks(this)
    `when`(myDevice.launchedDevice).thenReturn(Futures.immediateFuture(myLaunchedDevice))
    `when`(myLaunchedDevice.version).thenAnswer { myDevice.version }
    setupDeviceConfig(myLaunchedDevice,
                      "  config: mcc310-mnc410-es-rUS,fr-rFR-ldltr-sw411dp-w411dp-h746dp-normal-long-notround" +
                      "-lowdr-nowidecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27")
  }

  private fun setUpTestProject(
    vararg modules: Pair<String, AndroidProjectBuilder> = arrayOf(":" to AndroidProjectBuilder())
  ) = setUpTestProject(null, *modules)

  private fun setUpTestProject(agpVersion: String?, vararg modules: Pair<String, AndroidProjectBuilder>) {
    setupTestProjectFromAndroidModel(
      project,
      File(project.basePath!!),
      *modules.map {
        AndroidModuleModelBuilder(
          it.first,
          agpVersion = agpVersion,
          selectedBuildVariant = "debug",
          projectBuilder = it.second
        )
      }.toTypedArray()
    )
    myModules = ModuleManager.getInstance(project).modules
  }

  fun testCommonArguments() {
    setUpTestProject()
    val arguments = MakeBeforeRunTaskProvider.getCommonArguments(myModules, myRunConfiguration, deviceSpec())
    assertTrue(arguments.contains("-Pandroid.injected.enableStableIds=true"))
  }

  fun testCommonArguments_nonAndroidRunConfiguration() {
    setUpTestProject()
    val arguments = MakeBeforeRunTaskProvider.getCommonArguments(myModules, null, null)
    assertTrue(arguments.contains("-Pandroid.injected.enableStableIds=true"))
  }

  fun testDeviceSpecificArguments() {
    setUpTestProject()
    `when`(myDevice.version).thenReturn(AndroidVersion(20, null))
    `when`(myDevice.density).thenReturn(640)
    `when`(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86))
    val arguments = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules,
                                                                         myRunConfiguration,
                                                                         deviceSpec(myDevice))
    assertTrue(arguments.contains("-Pandroid.injected.build.api=20"))
    assertTrue(arguments.contains("-Pandroid.injected.build.abi=armeabi,x86"))
    for (argument in arguments) {
      assertFalse("codename should not be set for a released version",
                  argument.startsWith("-Pandroid.injected.build.codename"))
    }
  }

  fun testPreviewDeviceArguments() {
    setUpTestProject()
    `when`(myDevice.version).thenReturn(AndroidVersion(23, "N"))
    `when`(myDevice.density).thenReturn(640)
    `when`(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI))
    val arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, deviceSpec(myDevice))
    assertTrue(arguments.contains("-Pandroid.injected.build.api=23"))
    assertTrue(arguments.contains("-Pandroid.injected.build.codename=N"))
  }

  fun testPreviewDeviceArgumentsForBundleConfiguration() {
    setUpTestProject()
    myRunConfiguration = mock(AndroidRunConfiguration::class.java)
    `when`(myDevice.version).thenReturn(AndroidVersion(23, "N"))
    `when`(myDevice.density).thenReturn(640)
    `when`(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI))
    myRunConfiguration.DEPLOY = true
    myRunConfiguration.DEPLOY_APK_FROM_BUNDLE = true
    val arguments = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules,
                                                                         myRunConfiguration,
                                                                         deviceSpec(myDevice))
    val expectedJson = "{\"sdk_version\":23,\"codename\":\"N\",\"screen_density\":640,\"supported_abis\":[\"armeabi\"],\"supported_locales\":[\"es\",\"fr\"]}"
    assertExpectedJsonFile(arguments, expectedJson)
  }

  fun testDeviceArgumentsForBundleConfigurationWithEnabledDynamicFeatures() {
    // Setup additional Dynamic Feature modules
    setUpTestProject(
      ":" to AndroidProjectBuilder(dynamicFeatures = { listOf(":feature1", ":feature2") }),
      ":feature1" to AndroidProjectBuilder(projectType = { IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE }),
      ":feature2" to AndroidProjectBuilder(projectType = { IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE })
    )

    `when`(myDevice.version).thenReturn(AndroidVersion(23, "N"))
    `when`(myDevice.density).thenReturn(640)
    `when`(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI))
    myRunConfiguration.DEPLOY = true
    myRunConfiguration.DEPLOY_APK_FROM_BUNDLE = true

    val arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, deviceSpec(myDevice))
    assertTrue(arguments.contains("-Pandroid.injected.modules.install.list=feature1,feature2"))
  }

  /**
   * For a pre-L device, deploying an app with at least a dynamic feature should result
   * in using the "select apks from bundle" task (as opposed to the regular "assemble" task.
   */
  fun testDeviceArgumentsForPreLollipopDeviceWithDynamicFeature() { // Setup an additional Dynamic Feature module
    setUpTestProject(
      ":" to AndroidProjectBuilder(dynamicFeatures = { listOf(":feature1") }),
      ":feature1" to AndroidProjectBuilder(projectType = { IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE })
    )
    // Setup a pre-L device
    `when`(myDevice.version).thenReturn(AndroidVersion(20))
    `when`(myDevice.density).thenReturn(640)
    `when`(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI))
    // Invoke method and check result matches arguments needed for invoking "select apks from bundle" task
    // (as opposed to the regular "assemble" task
    val arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, deviceSpec(myDevice))
    assertExpectedJsonFile(arguments, "{\"sdk_version\":20,\"screen_density\":640,\"supported_abis\":[\"armeabi\"]}")
  }

  /**
   * For a pre-L device, deploying an app with no a dynamic feature should result
   * in using the regular "assemble" task.
   */
  fun testDeviceArgumentsForPreLollipopDevice() { // Setup a pre-L device
    setUpTestProject()
    `when`(myDevice.version).thenReturn(AndroidVersion(20))
    `when`(myDevice.density).thenReturn(640)
    `when`(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI))
    // Invoke method and check result matches arguments needed for invoking "select apks from bundle" task
    // (as opposed to the regular "assemble" task
    val arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, deviceSpec(myDevice))
    assertTrue(arguments.contains("-Pandroid.injected.build.api=20"))
    assertTrue(arguments.contains("-Pandroid.injected.build.abi=armeabi"))
  }

  fun testMultipleDeviceArgumentsMatchingApiLevels() {
    setUpTestProject()
    val device1 = mock(AndroidDevice::class.java)
    val device2 = mock(AndroidDevice::class.java)
    `when`(device1.version).thenReturn(AndroidVersion(22, null))
    `when`(device1.density).thenReturn(640)
    `when`(device1.abis).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86))
    `when`(device2.version).thenReturn(AndroidVersion(22, null))
    `when`(device2.density).thenReturn(480)
    `when`(device2.abis).thenReturn(ImmutableList.of(Abi.X86, Abi.X86_64))
    val arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, deviceSpec(device1, device2))
    assertTrue(arguments.contains("-Pandroid.injected.build.api=22"))
    for (argument in arguments) {
      assertFalse("ABIs should not be passed to Gradle when there are multiple devices",
                  argument.startsWith("-Pandroid.injected.build.abi"))
    }
  }

  fun testMultipleDeviceArgumentsDifferingApiLevels() {
    setUpTestProject()
    val device1 = mock(AndroidDevice::class.java)
    val device2 = mock(AndroidDevice::class.java)
    `when`(device1.version).thenReturn(AndroidVersion(23, null))
    `when`(device1.density).thenReturn(640)
    `when`(device1.abis).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86))
    `when`(device2.version).thenReturn(AndroidVersion(22, null))
    `when`(device2.density).thenReturn(480)
    `when`(device2.abis).thenReturn(ImmutableList.of(Abi.X86, Abi.X86_64))
    val arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, deviceSpec(device1, device2))
    for (argument in arguments) {
      assertFalse("Api levels should not be passed to Gradle when there are multiple devices with different values",
                  argument.startsWith("-Pandroid.injected.build.api"))
      assertFalse("ABIs should not be passed to Gradle when there are multiple devices",
                  argument.startsWith("-Pandroid.injected.build.abi"))
    }
  }

  fun testRunGradleSyncWithPostBuildSyncSupported() {
    setUpTestProject("3.5.0", ":" to AndroidProjectBuilder())
    `when`(myRunConfiguration.modules).thenReturn(arrayOf(module))
    val syncState = IdeComponents(myProject).mockProjectService(GradleSyncState::class.java)
    `when`(syncState.isSyncNeeded()).thenReturn(ThreeState.YES)
    val provider = MakeBeforeRunTaskProvider(myProject)
    // Gradle sync should not be invoked.
    assertThat(provider.isSyncNeeded(listOf(Abi.ARMEABI.toString()))).isEqualTo(SyncNeeded.NOT_NEEDED)
  }

  fun testRunGradleSyncWithBuildOutputFileSupported() {
    setUpTestProject("4.1.0", ":" to AndroidProjectBuilder())
    val syncState = IdeComponents(myProject).mockProjectService(GradleSyncState::class.java)
    `when`(syncState.isSyncNeeded()).thenReturn(ThreeState.YES)
    `when`(myRunConfiguration.modules).thenReturn(myModules)
    val provider = MakeBeforeRunTaskProvider(myProject)
    // Gradle sync should not be invoked since the build output file is expected to be available.
    assertThat(provider.isSyncNeeded(listOf(Abi.ARMEABI.toString()))).isEqualTo(SyncNeeded.NOT_NEEDED)
  }

  companion object {
    private fun assertExpectedJsonFile(arguments: List<String>, expectedJson: String) {
      assertThat(arguments.size).isEqualTo(1)
      val args = arguments[0]
      assertThat(args).startsWith("-Pandroid.inject.apkselect.config=")
      val path = args.substring(args.lastIndexOf('=') + 1)
      assertThat(path).isNotEmpty()
      val jsonFile = File(path)
      assertThat(jsonFile.exists()).isTrue()
      assertThat(FileUtils.readFileToString(jsonFile, Charset.forName("UTF-8"))).isEqualTo(expectedJson)
      jsonFile.delete()
    }

    private fun setupDeviceConfig(device: IDevice, @Suppress("SameParameterValue") config: String) {
      doAnswer { invocation: InvocationOnMock ->
        // get the 2nd arg (the receiver to feed it the lines).
        val receiver = invocation.getArgument<IShellOutputReceiver>(1)
        val byteArray = (config + "\n").toByteArray(
          Charsets.UTF_8)
        receiver.addOutput(byteArray, 0, byteArray.size)
        null
      }.`when`(device).executeShellCommand(ArgumentMatchers.anyString(),
                                           ArgumentMatchers.any(),
                                           ArgumentMatchers.anyLong(),
                                           ArgumentMatchers.any())
    }
  }
}

private const val MAX_TIMEOUT_MILLISECONDS: Long = 50_000

private fun deviceSpec(vararg devices: AndroidDevice): AndroidDeviceSpec? =
  createSpec(devices.toList(), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)

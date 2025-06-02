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
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeAndroidProjectImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeVariantAbiImpl
import com.android.tools.idea.gradle.model.impl.ndk.v1.IdeNativeVariantInfoImpl
import com.android.tools.idea.gradle.project.ProjectStructure
import com.android.tools.idea.gradle.project.model.V1NdkModel
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.run.MakeBeforeRunTaskProvider.SyncNeeded
import com.android.tools.idea.projectsystem.gradle.RunConfigurationGradleContext
import com.android.tools.idea.run.AndroidDevice
import com.android.tools.idea.run.profiler.ProfilingMode
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.android.tools.idea.util.androidFacet
import com.google.common.base.Charsets
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.ThreeState
import org.apache.commons.io.FileUtils
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations.initMocks
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class MakeBeforeRunTaskProviderTest : HeavyPlatformTestCase() {

  @Mock
  private lateinit var myDevice: AndroidDevice

  @Mock
  private lateinit var myLaunchedDevice: IDevice

  @Mock
  private lateinit var myRunConfiguration: RunConfigurationGradleContext

  private lateinit var myModules: Array<Module>

  override fun setUp() {
    super.setUp()
    initMocks(this)
    whenever(myDevice.launchedDevice).thenReturn(Futures.immediateFuture(myLaunchedDevice))
    whenever(myLaunchedDevice.version).thenAnswer { myDevice.version }
    setupDeviceConfig(myLaunchedDevice,
                      "  config: mcc310-mnc410-es-rUS,fr-rFR-ldltr-sw411dp-w411dp-h746dp-normal-long-notround" +
                      "-lowdr-nowidecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27")
  }

  private fun setUpTestProject(
    vararg modules: Pair<String, AndroidProjectBuilder> = arrayOf(":" to AndroidProjectBuilder())
  ) = setUpTestProject(null, *modules)

  private fun setUpTestProject(agpVersion: String?,
                               vararg modules: Pair<String, AndroidProjectBuilder>) {
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
    myModules = ProjectStructure.getInstance(project).appHolderModules.toTypedArray()
    myRunConfiguration = RunConfigurationGradleContext(
      androidFacet = project.gradleModule(modules.first().first)?.androidFacet!!,
      isTestConfiguration = false,
      isAdvancedProfilingEnabled = false,
      profilerProperties = null,
      alwaysDeployApkFromBundle = false,
      deployAsInstant = false,
      disabledDynamicFeatureModuleNames = emptySet(),
      supportsPrivacySandbox = false,
    )
  }

  fun testCommonArguments() {
    setUpTestProject()
    val arguments = MakeBeforeRunTaskProvider.getCommonArguments(myModules, myRunConfiguration,
                                                                 targetDeviceSpec(),
                                                                 deviceSpecs(),
                                                                 ProfilingMode.NOT_SET)
    assertTrue(arguments.contains("-Pandroid.injected.enableStableIds=true"))
  }

  fun testCommonArguments_nonAndroidRunConfiguration() {
    setUpTestProject()
    val arguments = MakeBeforeRunTaskProvider.getCommonArguments(myModules, null,
                                                                 targetDeviceSpec(),
                                                                 deviceSpecs(),
                                                                 ProfilingMode.NOT_SET)
    assertTrue(arguments.contains("-Pandroid.injected.enableStableIds=true"))
  }

  fun testDeviceSpecificArguments() {
    setUpTestProject()
    whenever(myDevice.version).thenReturn(AndroidVersion(20, null))
    whenever(myDevice.density).thenReturn(640)
    whenever(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86))
    whenever(myDevice.appPreferredAbi).thenReturn(null)
    val arguments = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules,
                                                                         myRunConfiguration,
                                                                         targetDeviceSpec(myDevice),
                                                                         deviceSpecs(myDevice))
    assertTrue(arguments.contains("-Pandroid.injected.build.api=20"))
    assertTrue(arguments.contains("-Pandroid.injected.build.abi=armeabi,x86"))
    for (argument in arguments) {
      assertFalse("codename should not be set for a released version",
                  argument.startsWith("-Pandroid.injected.build.codename"))
    }
  }

  fun `test when device api level injection is disabled`() {
    StudioFlags.API_OPTIMIZATION_ENABLE.override(false)
    setUpTestProject()
    whenever(myDevice.version).thenReturn(AndroidVersion(20, null))
    whenever(myDevice.appPreferredAbi).thenReturn(null)
    val arguments = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules,
                                                                         myRunConfiguration,
                                                                         targetDeviceSpec(myDevice),
                                                                         deviceSpecs(myDevice))
    assertFalse(arguments.contains("-Pandroid.injected.build.api=20"))

    StudioFlags.API_OPTIMIZATION_ENABLE.clearOverride()
  }

  fun testResizableDevice() {
    setUpTestProject()
    whenever(myDevice.version).thenReturn(AndroidVersion(33, null))
    whenever(myDevice.density).thenReturn(640)
    whenever(myDevice.supportsMultipleScreenFormats()).thenReturn(true)
    whenever(myDevice.appPreferredAbi).thenReturn(null)
    val arguments = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules,
                                                                         myRunConfiguration,
                                                                         targetDeviceSpec(myDevice),
                                                                         deviceSpecs(myDevice))
    assertTrue(arguments.contains("-Pandroid.injected.build.api=33"))
  }

  fun testPreviewDeviceArguments() {
    setUpTestProject()
    whenever(myDevice.version).thenReturn(AndroidVersion(23, "N"))
    whenever(myDevice.density).thenReturn(640)
    whenever(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI))
    whenever(myDevice.appPreferredAbi).thenReturn(null)
    val arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration,
                                                           targetDeviceSpec(myDevice),
                                                           deviceSpecs(myDevice))
    assertTrue(arguments.contains("-Pandroid.injected.build.api=23"))
    assertTrue(arguments.contains("-Pandroid.injected.build.codename=N"))
  }

  fun testPreviewDeviceArgumentsForBundleConfiguration() {
    setUpTestProject()
    whenever(myDevice.version).thenReturn(AndroidVersion(23, "N"))
    whenever(myDevice.density).thenReturn(640)
    whenever(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI))
    whenever(myDevice.appPreferredAbi).thenReturn(null)
    myRunConfiguration = myRunConfiguration.copy(alwaysDeployApkFromBundle = true)
    val arguments = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules,
                                                                         myRunConfiguration,
                                                                         targetDeviceSpec(myDevice),
                                                                         deviceSpecs(myDevice))
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

    whenever(myDevice.version).thenReturn(AndroidVersion(23, "N"))
    whenever(myDevice.density).thenReturn(640)
    whenever(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI))
    whenever(myDevice.appPreferredAbi).thenReturn(null)
    myRunConfiguration = myRunConfiguration.copy(alwaysDeployApkFromBundle = true)

    val arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration,
                                                           targetDeviceSpec(myDevice),
                                                           deviceSpecs(myDevice))
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
    whenever(myDevice.version).thenReturn(AndroidVersion(20))
    whenever(myDevice.density).thenReturn(640)
    whenever(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI))
    whenever(myDevice.appPreferredAbi).thenReturn(null)
    // Invoke method and check result matches arguments needed for invoking "select apks from bundle" task
    // (as opposed to the regular "assemble" task
    val arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration,
                                                           targetDeviceSpec(myDevice),
                                                           deviceSpecs(myDevice))
    assertExpectedJsonFile(arguments, "{\"sdk_version\":20,\"screen_density\":640,\"supported_abis\":[\"armeabi\"]}")
  }

  /**
   * For a pre-L device, deploying an app with no a dynamic feature should result
   * in using the regular "assemble" task.
   */
  fun testDeviceArgumentsForPreLollipopDevice() { // Setup a pre-L device
    setUpTestProject()
    whenever(myDevice.version).thenReturn(AndroidVersion(20))
    whenever(myDevice.density).thenReturn(640)
    whenever(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI))
    whenever(myDevice.appPreferredAbi).thenReturn(null)
    // Invoke method and check result matches arguments needed for invoking "select apks from bundle" task
    // (as opposed to the regular "assemble" task
    val arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration,
                                                           targetDeviceSpec(myDevice),
                                                           deviceSpecs(myDevice))
    assertTrue(arguments.contains("-Pandroid.injected.build.api=20"))
    assertTrue(arguments.contains("-Pandroid.injected.build.abi=armeabi"))
  }

  fun testMultipleDeviceArgumentsMatchingApiLevels() {
    setUpTestProject()
    val device1 = mock(AndroidDevice::class.java)
    val device2 = mock(AndroidDevice::class.java)
    whenever(device1.version).thenReturn(AndroidVersion(22, null))
    whenever(device1.density).thenReturn(640)
    whenever(device1.abis).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86))
    whenever(device2.version).thenReturn(AndroidVersion(22, null))
    whenever(device2.density).thenReturn(480)
    whenever(device2.abis).thenReturn(ImmutableList.of(Abi.X86, Abi.X86_64))
    val arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration,
                                                           targetDeviceSpec(device1, device2),
                                                           deviceSpecs(device1, device2))
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
    whenever(device1.version).thenReturn(AndroidVersion(23, null))
    whenever(device1.density).thenReturn(640)
    whenever(device1.abis).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86))
    whenever(device2.version).thenReturn(AndroidVersion(22, null))
    whenever(device2.density).thenReturn(480)
    whenever(device2.abis).thenReturn(ImmutableList.of(Abi.X86, Abi.X86_64))
    val arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration,
                                                           targetDeviceSpec(device1, device2),
                                                           deviceSpecs(device1, device2))
    for (argument in arguments) {
      assertFalse("Api levels should not be passed to Gradle when there are multiple devices with different values",
                  argument.startsWith("-Pandroid.injected.build.api"))
      assertFalse("ABIs should not be passed to Gradle when there are multiple devices",
                  argument.startsWith("-Pandroid.injected.build.abi"))
    }
  }

  fun testRunGradleSyncWithPostBuildSyncSupported() {
    setUpTestProject("3.5.0", ":" to AndroidProjectBuilder())
    val syncState = IdeComponents(myProject).mockProjectService(GradleSyncState::class.java)
    whenever(syncState.isSyncNeeded()).thenReturn(ThreeState.YES)
    val provider = MakeBeforeRunTaskProvider()
    // Gradle sync should not be invoked.
    assertThat(provider.isSyncNeeded(myProject, listOf(Abi.ARMEABI.toString()))).isEqualTo(SyncNeeded.NOT_NEEDED)
  }

  fun testRunGradleSyncWithBuildOutputFileSupported() {
    setUpTestProject("4.1.0", ":" to AndroidProjectBuilder())
    val syncState = IdeComponents(myProject).mockProjectService(GradleSyncState::class.java)
    whenever(syncState.isSyncNeeded()).thenReturn(ThreeState.YES)
    val provider = MakeBeforeRunTaskProvider()
    // Gradle sync should not be invoked since the build output file is expected to be available.
    assertThat(provider.isSyncNeeded(myProject, listOf(Abi.ARMEABI.toString()))).isEqualTo(SyncNeeded.NOT_NEEDED)
  }

  fun testRunGradleSyncWithV1Project() {
    val selected = Abi.ARMEABI_V7A
    val synced = Abi.X86_64
    val availableNotSynced1 = Abi.ARM64_V8A
    val availableNotSynced2 = Abi.X86
    val notAvailable1 = Abi.ARMEABI
    val notAvailable2 = Abi.MIPS

    setUpTestProject("4.1.0", ":" to AndroidProjectBuilder(
      ndkModel = {
        v1NativeModel(
          selectedAbi = selected,
          syncedAbis = listOf(synced, selected),
          allAbis = listOf(availableNotSynced2, synced, selected, availableNotSynced1)
        )
      }
    ))
    val provider = MakeBeforeRunTaskProvider()
    // Don't trigger sync if it is already synced
    assertThat(provider.isSyncNeeded(myProject, selected)).isEqualTo(SyncNeeded.NOT_NEEDED)
    assertThat(provider.isSyncNeeded(myProject, synced)).isEqualTo(SyncNeeded.NOT_NEEDED)
    assertThat(provider.isSyncNeeded(myProject, selected, synced)).isEqualTo(SyncNeeded.NOT_NEEDED)
    // Trigger sync if it is available and not already synced
    assertThat(provider.isSyncNeeded(myProject, availableNotSynced1)).isEqualTo(SyncNeeded.NATIVE_VARIANTS_SYNC_NEEDED)
    assertThat(provider.isSyncNeeded(myProject, availableNotSynced1, availableNotSynced2)).isEqualTo(SyncNeeded.NATIVE_VARIANTS_SYNC_NEEDED)
    // Trigger sync even if only one of the devices ABIs is available and not already synced
    assertThat(provider.isSyncNeeded(myProject, availableNotSynced1, notAvailable1)).isEqualTo(SyncNeeded.NATIVE_VARIANTS_SYNC_NEEDED)
    assertThat(provider.isSyncNeeded(myProject, availableNotSynced1, selected)).isEqualTo(SyncNeeded.NATIVE_VARIANTS_SYNC_NEEDED)
    // Don't trigger sync if the model is not available
    assertThat(provider.isSyncNeeded(myProject, notAvailable1)).isEqualTo(SyncNeeded.NOT_NEEDED)
    assertThat(provider.isSyncNeeded(myProject, notAvailable1, notAvailable2)).isEqualTo(SyncNeeded.NOT_NEEDED)
    assertThat(provider.isSyncNeeded(myProject, notAvailable1, selected)).isEqualTo(SyncNeeded.NOT_NEEDED)

  }

  private fun runningDeviceWithSerial(version: AndroidVersion, adbSerial: String): AndroidDevice {
    val launchedDevice = mock(IDevice::class.java).also {
      whenever(it.serialNumber).thenReturn(adbSerial)
    }
    return mock(AndroidDevice::class.java).also {
      whenever(it.version).thenReturn(version)
      whenever(it.serial).thenReturn("Mock running device, version=$version serial=$adbSerial")
      whenever(it.isRunning).thenReturn(true)
      whenever(it.launchedDevice).thenReturn(Futures.immediateFuture(launchedDevice))
    }
  }

  fun `test when device serials injection`() {
    StudioFlags.INJECT_DEVICE_SERIAL_ENABLED.override(true)
    setUpTestProject()
    val device1 = runningDeviceWithSerial(AndroidVersion(20), "device_1")
    val device2 = runningDeviceWithSerial(AndroidVersion(20), "device_2")
    val arguments = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules,
                                                                         myRunConfiguration,
                                                                         targetDeviceSpec(device1, device2),
                                                                         deviceSpecs(device1, device2))
    assertThat(arguments).contains("-Pinternal.android.inject.device.serials=device_1,device_2")
    StudioFlags.INJECT_DEVICE_SERIAL_ENABLED.clearOverride()

    val argumentsWithDisabledFlag = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules,
                                                                                         myRunConfiguration,
                                                                                         targetDeviceSpec(device1, device2),
                                                                                         deviceSpecs(device1, device2))
    assertThat(argumentsWithDisabledFlag).doesNotContain("-Pinternal.android.inject.device.serials=device_1,device_2")
  }

  private fun MakeBeforeRunTaskProvider.isSyncNeeded(project: Project, vararg abi: Abi) = isSyncNeeded(project, abi.map { it.toString() })

  private fun v1NativeModel(selectedAbi: Abi, syncedAbis: List<Abi>, allAbis: List<Abi>): V1NdkModel {
    assertThat(syncedAbis).contains(selectedAbi)
    assertThat(allAbis).containsAllIn(syncedAbis)
    return V1NdkModel(
      androidProject = IdeNativeAndroidProjectImpl(
        modelVersion = "3.6.0",
        name = "moduleName",
        buildFiles = listOf(),
        variantInfos = mapOf(
          "debug" to IdeNativeVariantInfoImpl(abiNames = allAbis.map { it.toString() }, buildRootFolderMap = emptyMap())),
        artifacts = listOf(),
        toolChains = listOf(),
        settings = listOf(),
        fileExtensions = mapOf(),
        buildSystems = listOf(),
        defaultNdkVersion = "21.0.0",
        ndkVersion = "21.0.0",
        apiVersion = 12,
      ),
      nativeVariantAbis = syncedAbis.map {
        IdeNativeVariantAbiImpl(
          buildFiles = emptyList(),
          artifacts = emptyList(),
          toolChains = emptyList(),
          settings = emptyList(),
          fileExtensions = emptyMap(),
          variantName = "debug",
          abi = it.toString(),
        )
      },
    )
  }

  fun testProfilingMode() {
    setUpTestProject()
    whenever(myDevice.version).thenReturn(AndroidVersion(23, "N"))
    whenever(myDevice.appPreferredAbi).thenReturn(null)

    val arguments = MakeBeforeRunTaskProvider.getCommonArguments(myModules, myRunConfiguration,
                                                                   targetDeviceSpec(myDevice),
                                                                   deviceSpecs(myDevice),
                                                                   ProfilingMode.PROFILEABLE)
    assertThat(arguments).contains("-Pandroid.profilingMode=profileable")

    val argsWithoutProfilingMode = MakeBeforeRunTaskProvider.getCommonArguments(myModules, myRunConfiguration,
                                                                                  targetDeviceSpec(myDevice),
                                                                                  deviceSpecs(myDevice),
                                                                                  ProfilingMode.NOT_SET)
    assertThat(argsWithoutProfilingMode).containsNoneOf("-Pandroid.profilingMode=profileable", "Pandroid.profilingMode=debuggable")
  }

  fun testSdkRuntimeDeviceSpecIncludedInCurrentAgp() {
    setUpTestProject(":" to AndroidProjectBuilder())
    whenever(myDevice.supportsSdkRuntime).thenReturn(true)
    whenever(myDevice.version).thenReturn(AndroidVersion(34, "14"))
    whenever(myDevice.appPreferredAbi).thenReturn(null)

    val bundleRunConfig = myRunConfiguration.copy(alwaysDeployApkFromBundle = true)
    val argsCurrentAgp = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, bundleRunConfig,
                                                                              targetDeviceSpec(myDevice),
                                                                              deviceSpecs(myDevice))
    assertExpectedJsonFile(argsCurrentAgp,
                           "{\"sdk_version\":34,\"codename\":\"14\",\"sdk_runtime\":{\"supported\":true},\"supported_locales\":[\"es\",\"fr\"]}")
  }

  fun testSdkRuntimeDeviceSpecNotIncludedInAgp7_3() {
    // DeviceSpec 'sdk_runtime' config only supported from AGP >= 7.4 .
    setUpTestProject("7.3.0", ":" to AndroidProjectBuilder())
    whenever(myDevice.supportsSdkRuntime).thenReturn(true)
    whenever(myDevice.version).thenReturn(AndroidVersion(34, "14"))
    whenever(myDevice.appPreferredAbi).thenReturn(null)

    val bundleRunConfig = myRunConfiguration.copy(alwaysDeployApkFromBundle = true)
    val argsAgp7_3 = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, bundleRunConfig,
                                                                          targetDeviceSpec(myDevice),
                                                                          deviceSpecs(myDevice))
    assertExpectedJsonFile(argsAgp7_3, "{\"sdk_version\":34,\"codename\":\"14\",\"supported_locales\":[\"es\",\"fr\"]}")
  }

  companion object {
    private fun assertExpectedJsonFile(arguments: List<String>, vararg expectedJson: String) {
      assertThat(arguments.size).isEqualTo(1)
      val args = arguments[0]
      assertThat(args).startsWith("-Pandroid.inject.apkselect.config=")
      val path = args.substring(args.lastIndexOf('=') + 1)
      assertThat(path).isNotEmpty()
      val paths = path.split(',')
      assertThat(paths).hasSize(expectedJson.size)
      val actualJsons = paths.map { p ->
        val jsonFile = File(p)
        assertThat(jsonFile.exists()).isTrue()
        FileUtils.readFileToString(jsonFile, Charset.forName("UTF-8"))
      }
      assertThat(actualJsons).isEqualTo(expectedJson.toList())
      paths.forEach { File(it).delete() }
    }

    private fun setupDeviceConfig(device: IDevice, @Suppress("SameParameterValue") config: String) {
      doAnswer { invocation: InvocationOnMock ->
        // get the 2nd arg (the receiver to feed it the lines).
        val receiver = invocation.getArgument<IShellOutputReceiver>(1)
        val byteArray = (config + "\n").toByteArray(
          Charsets.UTF_8)
        receiver.addOutput(byteArray, 0, byteArray.size)
        null
      }.whenever(device).executeShellCommand(ArgumentMatchers.anyString(),
                                             ArgumentMatchers.any(),
                                             ArgumentMatchers.anyLong(),
                                             ArgumentMatchers.any())
    }
  }
}

private const val MAX_TIMEOUT_MILLISECONDS: Long = 50_000

private fun targetDeviceSpec(vararg devices: AndroidDevice): ProcessedDeviceSpec.SingleDeviceSpec =
  createTargetDeviceSpec(devices.toList(), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)

private fun deviceSpecs(vararg devices: AndroidDevice): ProcessedDeviceSpec.MultipleDeviceSpec =
  createDeviceSpecs(devices.toList(), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.AndroidProjectTypes
import com.android.SdkConstants
import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.ide.common.gradle.model.IdeAndroidProject
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.android.tools.idea.gradle.stubs.gradle.GradleProjectStub
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.run.AndroidAppRunConfigurationBase
import com.android.tools.idea.run.AndroidDevice
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.testing.Facets
import com.android.tools.idea.testing.IdeComponents
import com.google.common.base.Charsets
import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.Module
import com.intellij.testFramework.PlatformTestCase
import com.intellij.util.ThreeState
import org.apache.commons.io.FileUtils
import org.gradle.tooling.model.GradleProject
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.MockitoAnnotations.initMocks
import org.mockito.invocation.InvocationOnMock
import java.io.File

class MakeBeforeRunTaskProviderTest : PlatformTestCase() {
  @Mock
  private lateinit var myAndroidModel: AndroidModuleModel
  @Mock
  private lateinit var myIdeAndroidProject: IdeAndroidProject
  @Mock
  private lateinit var myDevice: AndroidDevice
  @Mock
  private lateinit var myLaunchedDevice: IDevice
  @Mock
  private lateinit var myRunConfiguration: AndroidAppRunConfigurationBase
  @Mock
  private lateinit var myAndroidModel2: AndroidModuleModel
  @Mock
  private lateinit var myIdeAndroidProject2: IdeAndroidProject

  private lateinit var myModules: Array<Module>

  override fun setUp() {
    super.setUp()
    initMocks(this)
    `when`(myDevice.launchedDevice).thenReturn(Futures.immediateFuture(myLaunchedDevice))
    `when`(myLaunchedDevice.version).thenAnswer { invocation: InvocationOnMock? -> myDevice.version }
    setupDeviceConfig(myLaunchedDevice,
                      "  config: mcc310-mnc410-es-rUS,fr-rFR-ldltr-sw411dp-w411dp-h746dp-normal-long-notround" +
                      "-lowdr-nowidecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27")
    myModules = arrayOf(module)
    setUpModuleAsAndroidModule()
  }

  fun testDeviceSpecificArguments() {
    `when`(myDevice.version).thenReturn(AndroidVersion(20, null))
    `when`(myDevice.density).thenReturn(640)
    `when`(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI, Abi.X86))
    val arguments = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules,
                                                                         myRunConfiguration,
                                                                         listOf(myDevice))
    assertTrue(arguments.contains("-Pandroid.injected.build.api=20"))
    assertTrue(arguments.contains("-Pandroid.injected.build.abi=armeabi,x86"))
    for (argument in arguments) {
      assertFalse("codename should not be set for a released version",
                  argument.startsWith("-Pandroid.injected.build.codename"))
    }
  }

  fun testPreviewDeviceArguments() {
    `when`(myDevice.version).thenReturn(AndroidVersion(23, "N"))
    `when`(myDevice.density).thenReturn(640)
    `when`(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI))
    val arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, listOf(myDevice))
    assertTrue(arguments.contains("-Pandroid.injected.build.api=23"))
    assertTrue(arguments.contains("-Pandroid.injected.build.codename=N"))
  }

  fun testPreviewDeviceArgumentsForBundleConfiguration() {
    myRunConfiguration = Mockito.mock(AndroidRunConfiguration::class.java)
    `when`(myDevice.version).thenReturn(AndroidVersion(23, "N"))
    `when`(myDevice.density).thenReturn(640)
    `when`(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI))
    myRunConfiguration.DEPLOY = true
    myRunConfiguration.DEPLOY_APK_FROM_BUNDLE = true
    val arguments = MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules,
                                                                         myRunConfiguration,
                                                                         listOf(myDevice))
    val expectedJson = "{\"sdk_version\":23,\"screen_density\":640,\"supported_abis\":[\"armeabi\"],\"supported_locales\":[\"es\",\"fr\"]}"
    assertExpectedJsonFile(arguments, expectedJson)
  }

  /**
   * For a pre-L device, deploying an app with at least a dynamic feature should result
   * in using the "select apks from bundle" task (as opposed to the regular "assemble" task.
   */
  fun testDeviceArgumentsForPreLollipopDeviceWithDynamicFeature() { // Setup an additional Dynamic Feature module
    val featureModule = createModule("feature1")
    setUpModuleAsAndroidModule(featureModule, myAndroidModel2, myIdeAndroidProject2)
    `when`(myIdeAndroidProject2.projectType).thenReturn(AndroidProjectTypes.PROJECT_TYPE_DYNAMIC_FEATURE)
    `when`(myIdeAndroidProject.dynamicFeatures).thenReturn(ImmutableList.of(":feature1"))
    // Setup a pre-L device
    `when`(myDevice.version).thenReturn(AndroidVersion(20))
    `when`(myDevice.density).thenReturn(640)
    `when`(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI))
    myModules = arrayOf(module, featureModule)
    // Invoke method and check result matches arguments needed for invoking "select apks from bundle" task
    // (as opposed to the regular "assemble" task
    val arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, listOf(myDevice))
    assertExpectedJsonFile(arguments, "{\"sdk_version\":20,\"screen_density\":640,\"supported_abis\":[\"armeabi\"]}")
  }

  /**
   * For a pre-L device, deploying an app with no a dynamic feature should result
   * in using the regular "assemble" task.
   */
  fun testDeviceArgumentsForPreLollipopDevice() { // Setup a pre-L device
    `when`(myDevice.version).thenReturn(AndroidVersion(20))
    `when`(myDevice.density).thenReturn(640)
    `when`(myDevice.abis).thenReturn(ImmutableList.of(Abi.ARMEABI))
    // Invoke method and check result matches arguments needed for invoking "select apks from bundle" task
    // (as opposed to the regular "assemble" task
    val arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, listOf(myDevice))
    assertTrue(arguments.contains("-Pandroid.injected.build.api=20"))
    assertTrue(arguments.contains("-Pandroid.injected.build.abi=armeabi"))
  }

  fun testMultipleDeviceArguments() {
    val device1 = Mockito.mock(
      AndroidDevice::class.java)
    val device2 = Mockito.mock(
      AndroidDevice::class.java)
    `when`(device1.version).thenReturn(AndroidVersion(23, null))
    `when`(device1.density).thenReturn(640)
    `when`(device1.abis).thenReturn(
      ImmutableList.of(Abi.ARMEABI, Abi.X86))
    `when`(device2.version).thenReturn(AndroidVersion(22, null))
    `when`(device2.density).thenReturn(480)
    `when`(device2.abis).thenReturn(
      ImmutableList.of(Abi.X86, Abi.X86_64))
    val arguments =
      MakeBeforeRunTaskProvider.getDeviceSpecificArguments(myModules, myRunConfiguration, ImmutableList.of(device1, device2))
    assertTrue(arguments.contains("-Pandroid.injected.build.api=22"))
    for (argument in arguments) {
      assertFalse("ABIs should not be passed to Gradle when there are multiple devices",
                  argument.startsWith("-Pandroid.injected.build.abi"))
    }
  }

  private fun setUpModuleAsAndroidModule() {
    setUpModuleAsAndroidModule(module, myAndroidModel, myIdeAndroidProject)
  }

  private fun setUpModuleAsAndroidModule(module: Module,
                                         androidModel: AndroidModuleModel?,
                                         ideAndroidProject: IdeAndroidProject?) {
    setUpModuleAsGradleModule(module)
    `when`<IdeAndroidProject?>(androidModel!!.androidProject).thenReturn(ideAndroidProject)
    val androidModelFeatures = Mockito.mock(AndroidModelFeatures::class.java)
    `when`(androidModelFeatures.isTestedTargetVariantsSupported).thenReturn(false)
    `when`(androidModel.features).thenReturn(androidModelFeatures)
    val androidFacet = Facets.createAndAddAndroidFacet(module)
    val state = androidFacet.configuration.state
    assertNotNull(state)
    state.ASSEMBLE_TASK_NAME = "assembleTask2"
    state.AFTER_SYNC_TASK_NAMES = Sets.newHashSet("afterSyncTask1", "afterSyncTask2")
    state.COMPILE_JAVA_TASK_NAME = "compileTask2"
    AndroidModel.set(androidFacet, androidModel)
  }

  private fun setUpModuleAsGradleModule(module: Module) {
    val gradleFacet = Facets.createAndAddGradleFacet(module)
    gradleFacet.configuration.GRADLE_PROJECT_PATH = SdkConstants.GRADLE_PATH_SEPARATOR + module.name
    val gradleProjectStub: GradleProject =
      GradleProjectStub(emptyList(),
                        SdkConstants.GRADLE_PATH_SEPARATOR + module.name,
                        Projects.getBaseDirPath(
                          project))
    val model = GradleModuleModel(module.name, gradleProjectStub, emptyList(), null, null, null,
                                  null)
    gradleFacet.setGradleModuleModel(model)
  }

  fun testRunGradleSyncWithPostBuildSyncSupported() {
    val app = createModule("app")
    setUpModuleAsAndroidModule(app, myAndroidModel, myIdeAndroidProject)
    `when`(myRunConfiguration.modules).thenReturn(arrayOf(app))
    // Simulate the case when post build sync is supported.
    `when`(myAndroidModel.features.isPostBuildSyncSupported).thenReturn(true)
    val syncInvoker = IdeComponents(myProject).mockApplicationService(GradleSyncInvoker::class.java)
    val syncState = IdeComponents(
      myProject).mockProjectService(
      GradleSyncState::class.java)
    `when`(syncState.isSyncNeeded()).thenReturn(ThreeState.YES)
    val provider = MakeBeforeRunTaskProvider(myProject)
    // Invoke method to test.
    provider.runGradleSyncIfNeeded(myRunConfiguration, Mockito.mock(DataContext::class.java))
    // Gradle sync should not be invoked.
    Mockito.verify(syncInvoker, Mockito.never()).requestProjectSync(
      ArgumentMatchers.eq(myProject), ArgumentMatchers.any(
      GradleSyncInvoker.Request::class.java), ArgumentMatchers.any<GradleSyncListener>())
  }

  fun testRunGradleSyncWithPostBuildSyncNotSupported() {
    val app = createModule("app")
    setUpModuleAsAndroidModule(app, myAndroidModel, myIdeAndroidProject)
    `when`(myRunConfiguration.modules).thenReturn(
      arrayOf(app))
    // Simulate the case when post build sync is NOT supported.
    `when`(myAndroidModel.features.isPostBuildSyncSupported).thenReturn(false)
    val syncInvoker = IdeComponents(myProject).mockApplicationService(GradleSyncInvoker::class.java)
    val syncState = IdeComponents(
      myProject).mockProjectService(
      GradleSyncState::class.java)
    `when`(syncState.isSyncNeeded()).thenReturn(ThreeState.YES)
    val provider = MakeBeforeRunTaskProvider(myProject)
    // Invoke method to test.
    provider.runGradleSyncIfNeeded(myRunConfiguration, Mockito.mock(DataContext::class.java))
    // Gradle sync should be invoked to make sure Android models are up-to-date.
    Mockito.verify(syncInvoker, Mockito.times(1)).requestProjectSync(
      ArgumentMatchers.eq(myProject), ArgumentMatchers.any(
      GradleSyncInvoker.Request::class.java), ArgumentMatchers.any<GradleSyncListener>())
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
      assertThat(FileUtils.readFileToString(jsonFile)).isEqualTo(expectedJson)
      jsonFile.delete()
    }

    private fun setupDeviceConfig(device: IDevice, config: String) {
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
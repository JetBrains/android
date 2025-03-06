/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.preview

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ScreenSize
import com.android.resources.UiMode
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.ConfigurationModelModule
import com.android.tools.configurations.ConfigurationSettings
import com.android.tools.configurations.ResourceResolverCache
import com.android.tools.configurations.ThemeInfoProvider
import com.android.tools.idea.layoutlib.LayoutLibrary
import com.android.tools.layoutlib.LayoutlibContext
import com.android.tools.module.AndroidModuleInfo
import com.android.tools.module.ModuleDependencies
import com.android.tools.module.ModuleKey
import com.android.tools.module.ViewClass
import com.android.tools.res.ResourceRepositoryManager
import com.android.tools.sdk.AndroidPlatform
import com.android.tools.sdk.CompatibilityRenderTarget
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PreviewConfigurationTest {

  @Test
  fun testPreviewConfigurationCleaner() {
    Assert.assertEquals(
      PreviewConfiguration.cleanAndGet(-120, null, 1, 1, "", 2f, null, "", 2),
      PreviewConfiguration.cleanAndGet(-120, null, -2, -10, null, 2f, 0, null, 2),
    )

    Assert.assertEquals(
      PreviewConfiguration.cleanAndGet(
        9000,
        null,
        MAX_DIMENSION,
        MAX_DIMENSION,
        null,
        null,
        null,
        "id:device",
      ),
      PreviewConfiguration.cleanAndGet(9000, null, 500000, 500000, null, 1f, 0, "id:device"),
    )

    Assert.assertEquals(
      PreviewConfiguration.cleanAndGet(12, null, 120, MAX_DIMENSION, null, -1f, 123, null, -1),
      PreviewConfiguration.cleanAndGet(12, null, 120, 500000, null, 0f, 123, null, null),
    )
  }

  @Test
  fun testGetUiModeForDevice() {
    val deviceWear = createDevice(tagId = "android-wear")
    val deviceTv = createDevice(tagId = "android-tv")
    val deviceAutomotive = createDevice(tagId = "android-automotive")
    val deviceDesktop = createDevice(tagId = "android-desktop")
    val deviceXr = createDevice(tagId = "android-xr")
    val deviceThings = createDevice(tagId = "android-things")
    val devicePhone = createDevice(tagId = null)
    val deviceNull: Device? = null

    assertThat(getUiModeForDevice(deviceWear)).isEqualTo(UiMode.WATCH)
    assertThat(getUiModeForDevice(deviceTv)).isEqualTo(UiMode.TELEVISION)
    assertThat(getUiModeForDevice(deviceAutomotive)).isEqualTo(UiMode.CAR)
    assertThat(getUiModeForDevice(deviceDesktop)).isEqualTo(UiMode.DESK)
    assertThat(getUiModeForDevice(deviceXr)).isEqualTo(UiMode.VR_HEADSET)
    assertThat(getUiModeForDevice(deviceThings)).isEqualTo(UiMode.APPLIANCE)
    assertThat(getUiModeForDevice(devicePhone)).isEqualTo(UiMode.NORMAL)
    assertThat(getUiModeForDevice(deviceNull)).isEqualTo(UiMode.NORMAL)
  }

  @Test
  fun testApplyToUsesUiModeForDevice() {
    val deviceWear = createDevice(tagId = "android-wear")
    val deviceTv = createDevice(tagId = "android-tv")
    val deviceAutomotive = createDevice(tagId = "android-automotive")
    val deviceDesktop = createDevice(tagId = "android-desktop")
    val deviceXr = createDevice(tagId = "android-xr")
    val deviceThings = createDevice(tagId = "android-things")
    val devicePhone = createDevice(tagId = null)
    val deviceNull: Device? = null

    val configuration =
      Configuration.create(TestConfigurationSettingsImpl(), FolderConfiguration.createDefault())

    // Test with Wear device
    PreviewConfiguration.cleanAndGet(device = deviceWear.id)
      .applyConfigurationForTest(configuration, { null }, { emptyList() }, { deviceWear })
    Assert.assertEquals(UiMode.WATCH, configuration.uiMode)
    // Test with TV device
    PreviewConfiguration.cleanAndGet(device = deviceTv.id)
      .applyConfigurationForTest(configuration, { null }, { emptyList() }, { deviceTv })
    Assert.assertEquals(UiMode.TELEVISION, configuration.uiMode)

    // Test with Automotive device
    PreviewConfiguration.cleanAndGet(device = deviceAutomotive.id)
      .applyConfigurationForTest(configuration, { null }, { emptyList() }, { deviceAutomotive })
    Assert.assertEquals(UiMode.CAR, configuration.uiMode)

    // Test with Desktop device
    PreviewConfiguration.cleanAndGet(device = deviceDesktop.id)
      .applyConfigurationForTest(configuration, { null }, { emptyList() }, { deviceDesktop })
    Assert.assertEquals(UiMode.DESK, configuration.uiMode)

    // Test with XR device
    PreviewConfiguration.cleanAndGet(device = deviceXr.id)
      .applyConfigurationForTest(configuration, { null }, { emptyList() }, { deviceXr })
    Assert.assertEquals(UiMode.VR_HEADSET, configuration.uiMode)

    // Test with Things device
    PreviewConfiguration.cleanAndGet(device = deviceThings.id)
      .applyConfigurationForTest(configuration, { null }, { emptyList() }, { deviceThings })
    Assert.assertEquals(UiMode.APPLIANCE, configuration.uiMode)

    // Test with Phone device
    PreviewConfiguration.cleanAndGet(device = devicePhone.id)
      .applyConfigurationForTest(configuration, { null }, { emptyList() }, { devicePhone })
    Assert.assertEquals(UiMode.NORMAL, configuration.uiMode)

    // Test with null device
    PreviewConfiguration.cleanAndGet(device = null)
      .applyConfigurationForTest(configuration, { null }, { emptyList() }, { deviceNull })
    Assert.assertEquals(UiMode.NORMAL, configuration.uiMode)
  }

  private fun createDevice(tagId: String?): Device {
    val state = State()
    val hardware = Hardware()
    hardware.screen = Screen()
    state.hardware = hardware
    state.isDefaultState = true

    return Device.Builder()
      .apply {
        setName("device")
        setManufacturer("test")
        addSoftware(Software())
        addState(state)
        setTagId(tagId)
      }
      .build()
  }
}

class TestThemeInfoProvider : ThemeInfoProvider {
  override val appThemeName = "AppTheme"
  override val allActivityThemeNames: Set<String> = setOf("ActivityTheme")

  override fun getThemeNameForActivity(activityFqcn: String): String? {
    return "ActivityTheme"
  }

  override fun getDeviceDefaultTheme(
    renderingTarget: IAndroidTarget?,
    screenSize: ScreenSize?,
    device: Device?,
  ): String {
    return "theme"
  }

  override fun getDefaultTheme(configuration: Configuration): String {
    return "theme"
  }
}

class TestConfigurationModelModule : ConfigurationModelModule {
  override val androidPlatform: AndroidPlatform?
    get() = null

  override val resourceRepositoryManager: ResourceRepositoryManager?
    get() = null

  override val themeInfoProvider = TestThemeInfoProvider()
  override val layoutlibContext =
    object : LayoutlibContext {
      override fun hasLayoutlibCrash(): Boolean {
        return false
      }

      override fun register(layoutlib: LayoutLibrary) {}
    }
  override val androidModuleInfo: AndroidModuleInfo?
    get() = null

  override val name: String
    get() = "test"

  override val dependencies: ModuleDependencies
    get() =
      object : ModuleDependencies {
        override fun dependsOn(artifactId: GoogleMavenArtifactId): Boolean {
          return false
        }

        override fun dependsOnAndroidx(): Boolean {
          return false
        }

        override fun getResourcePackageNames(includeExternalLibraries: Boolean): List<String> {
          return emptyList()
        }

        override fun findViewClass(fqcn: String): ViewClass? {
          return null
        }
      }

  override val moduleKey: ModuleKey = ModuleKey()
  override val resourcePackage: String? = null

  override fun getCompatibilityTarget(target: IAndroidTarget): CompatibilityRenderTarget {
    return CompatibilityRenderTarget(target, 30, target)
  }
}

/** Test implementation of [ConfigurationSettings]. */
class TestConfigurationSettingsImpl : ConfigurationSettings {

  override var defaultDevice: Device? = null
  override var locale: Locale = Locale.ANY
  override var target: IAndroidTarget? = null
  override var stateVersion: Int = 1
  override var configModule: ConfigurationModelModule = TestConfigurationModelModule()
  override lateinit var resolverCache: ResourceResolverCache
  override var localesInProject: ImmutableList<Locale> = ImmutableList.of()
  override var devices: ImmutableList<Device> = ImmutableList.of()
  override var projectTarget: IAndroidTarget? = null
  override var highestApiTarget: IAndroidTarget? = null
  override var targets: Array<IAndroidTarget> = arrayOf()
  override var recentDevices: List<Device> = listOf()
  override var avdDevices: List<Device> = listOf()

  var selectDeviceCallCount = 0
  var getTargetCallCount = 0
  var createDeviceForAvdCallCount = 0
  var getDeviceByIdCallCount = 0

  var getTargetReturn: IAndroidTarget? = null
  var getDeviceByIdReturn: Device? = null
  var createDeviceForAvdReturn: Device? = null

  override fun selectDevice(device: Device) {
    defaultDevice = device
    selectDeviceCallCount++
  }

  override fun getTarget(minVersion: Int): IAndroidTarget? {
    getTargetCallCount++
    return getTargetReturn
  }

  override fun createDeviceForAvd(avd: AvdInfo): Device? {
    createDeviceForAvdCallCount++
    return createDeviceForAvdReturn
  }

  override fun getDeviceById(id: String): Device? {
    getDeviceByIdCallCount++
    return getDeviceByIdReturn
  }
}

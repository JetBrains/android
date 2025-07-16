/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.actions

import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.adtui.actions.findActionByText
import com.android.tools.adtui.actions.prettyPrintActions
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.ConfigurationModelModule
import com.android.tools.configurations.ConfigurationSettings
import com.android.tools.configurations.ResourceResolverCache
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.DeviceGroup
import com.android.tools.idea.configurations.StudioConfigurationModelModule
import com.android.tools.idea.configurations.groupDevices
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.testFramework.TestActionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.base.util.module
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever

private fun isAvdAction(action: AnAction): Boolean {
  val text = action.templatePresentation.text
  return text != null && text.startsWith("AVD:")
}

private fun AnAction.flattenActions(): List<AnAction> {
  return if (this is DefaultActionGroup) {
    this.getChildren(null).flatMap { it.flattenActions() }
  } else {
    listOf(this)
  }
}

class DeviceMenuActionTest {

  @JvmField @Rule val projectRule = AndroidProjectRule.withAndroidModel().onEdt()

  private fun getReferenceDevicesExpected(): String {
    return """
    Reference Devices
    Medium Phone (411 × 891 dp, 420dpi)
    Foldable (673 × 841 dp, 420dpi)
    Medium Tablet (1280 × 800 dp, hdpi)
    Desktop (1920 × 1080 dp, mdpi)
    """
  }

  @Test
  fun testDefaultDevicesRendering() = runBlocking {
    val configuration = Mockito.mock(Configuration::class.java)
    val configurationModelModule: ConfigurationModelModule =
      StudioConfigurationModelModule(projectRule.projectRule.module)
    whenever(configuration.settings)
      .thenReturn(ConfigurationManager.getOrCreateInstance(projectRule.projectRule.module))
    whenever(configuration.configModule).thenReturn(configurationModelModule)
    val dataContext = SimpleDataContext.getSimpleContext(CONFIGURATIONS, listOf(configuration))

    val menuAction = DeviceMenuAction()
    menuAction.updateActions(dataContext)
    val actual =
      withContext(Dispatchers.EDT) {
        prettyPrintActions(menuAction, { action: AnAction -> !isAvdAction(action) }, dataContext)
      }

    assertThat(actual.trimEnd())
      .isEqualTo(
        getReferenceDevicesExpected() +
          """
          Phones
                  Pixel 9 (411 × 923 dp, 420dpi)
                  Pixel 9 Pro XL (448 × 997 dp, xxhdpi)
                  Pixel 9 Pro Fold (852 × 883 dp, 390dpi)
                  Pixel 9 Pro (427 × 952 dp, xxhdpi)
                  Pixel 9a (411 × 923 dp, 420dpi)
                  Pixel 8 (411 × 914 dp, 420dpi)
                  Pixel 8 Pro (448 × 997 dp, xxhdpi)
                  Pixel 8a (411 × 914 dp, 420dpi)
                  Pixel 7 (411 × 914 dp, 420dpi)
                  Pixel 7 Pro (411 × 891 dp, 560dpi)
                  Pixel 7a (411 × 914 dp, 420dpi)
                  Pixel 6 (411 × 914 dp, 420dpi)
                  Pixel 6 Pro (411 × 891 dp, 560dpi)
                  Pixel 6a (411 × 914 dp, 420dpi)
                  Pixel 5 (393 × 851 dp, 440dpi)
                  Pixel 4 (393 × 829 dp, 440dpi)
                  Pixel 4 XL (411 × 869 dp, 560dpi)
                  Pixel 4a (393 × 851 dp, 440dpi)
                  Pixel 3 (393 × 785 dp, 440dpi)
                  Pixel 3 XL (411 × 846 dp, 560dpi)
                  Pixel 3a (393 × 807 dp, 440dpi)
                  Pixel 3a XL (432 × 864 dp, 400dpi)
                  Pixel 2 (411 × 731 dp, 420dpi)
                  Pixel 2 XL (411 × 823 dp, 560dpi)
                  Pixel (411 × 731 dp, 420dpi)
                  Pixel XL (411 × 731 dp, 560dpi)
                  Pixel Fold (841 × 701 dp, 420dpi)
                  Nexus 6 (411 × 731 dp, 560dpi)
                  Nexus 6P (411 × 731 dp, 560dpi)
                  Nexus 5X (411 × 731 dp, 420dpi)
              Tablets
                  Pixel Tablet (1280 × 800 dp, xhdpi)
                  Pixel C (1280 × 900 dp, xhdpi)
                  Nexus 10 (1280 × 800 dp, xhdpi)
                  Nexus 9 (1024 × 768 dp, xhdpi)
                  Nexus 7 (600 × 960 dp, xhdpi)
                  Nexus 7 (2012) (601 × 962 dp, tvdpi)
              Desktop
                  Small Desktop (1366 × 768 dp, mdpi)
                  Medium Desktop (1920 × 1080 dp, xhdpi)
                  Large Desktop (1920 × 1080 dp, mdpi)
              ------------------------------------------------------
              Wear
              Wear OS Square (180 × 180 dp, xhdpi)
              Wear OS Small Round (192 × 192 dp, xhdpi)
              Wear OS Rectangular (201 × 238 dp, xhdpi)
              Wear OS Large Round (227 × 227 dp, xhdpi)
              ------------------------------------------------------
              TV
              Television (720p) (962 × 541 dp, tvdpi)
              Television (4K) (960 × 540 dp, xxxhdpi)
              Television (1080p) (960 × 540 dp, xhdpi)
              ------------------------------------------------------
              Auto
              Automotive Ultrawide (2603 × 880 dp, hdpi)
              Automotive Portrait (1067 × 1707 dp, ldpi)
              Automotive Large Portrait (1280 × 1606 dp, mdpi)
              Automotive Distant Display with Google Play (1440 × 800 dp, ldpi)
              Automotive Distant Display (1440 × 800 dp, ldpi)
              Automotive (1408p landscape) with Google Play (1408 × 792 dp, mdpi)
              Automotive (1408p landscape) (1408 × 792 dp, mdpi)
              Automotive (1080p landscape) (1440 × 800 dp, ldpi)
              Automotive (1024p landscape) (1024 × 768 dp, mdpi)
              ------------------------------------------------------
              XR
              XR Headset (1280 × 1279 dp, xhdpi)
              ------------------------------------------------------
              Custom
              ------------------------------------------------------
              Generic Devices
                  Small Phone (360 × 640 dp, xhdpi)
                  Resizable (Experimental) (411 × 914 dp, 420dpi)
                  Medium Tablet (1280 × 800 dp, xhdpi)
                  Medium Phone (411 × 914 dp, 420dpi)
                  8" Fold-out (838 × 945 dp, 420dpi)
                  7.6" Fold-in with outer display (674 × 841 dp, 420dpi)
                  7.4" Rollable (610 × 925 dp, 420dpi)
                  7" WSVGA (Tablet) (1024 × 600 dp, mdpi)
                  6.7" Horizontal Fold-in (360 × 879 dp, xxhdpi)
                  5.4" FWVGA (480 × 854 dp, mdpi)
                  5.1" WVGA (480 × 800 dp, mdpi)
                  4.7" WXGA (640 × 360 dp, xhdpi)
                  4.65" 720p (Galaxy Nexus) (360 × 640 dp, xhdpi)
                  4" WVGA (Nexus S) (320 × 533 dp, hdpi)
                  3.7" WVGA (Nexus One) (320 × 533 dp, hdpi)
                  3.7" FWVGA slider (320 × 569 dp, hdpi)
                  3.4" WQVGA (320 × 576 dp, ldpi)
                  3.3" WQVGA (320 × 533 dp, ldpi)
                  3.2" QVGA (ADP2) (320 × 480 dp, mdpi)
                  3.2" HVGA slider (ADP1) (320 × 480 dp, mdpi)
                  2.7" QVGA slider (320 × 427 dp, ldpi)
                  2.7" QVGA (320 × 427 dp, ldpi)
                  13.5" Freeform (1707 × 960 dp, hdpi)
                  10.1" WXGA (Tablet) (1280 × 800 dp, mdpi)
              Add Device Definition
      """
            .trimIndent()
      )
  }

  @Test
  fun testSetDevice() = runBlocking {
    val layoutFile =
      projectRule.fixture.addFileToProject(
        "res/layout/layout.xml",
        // language=xml
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent"
          android:orientation="vertical">
         </LinearLayout>
      """
          .trimIndent(),
      )

    val configuration = readAction {
      ConfigurationManager.getOrCreateInstance(layoutFile.module!!)
        .getConfiguration(layoutFile.virtualFile)
    }
    val dataContext = SimpleDataContext.getSimpleContext(CONFIGURATIONS, listOf(configuration))

    val menuAction = DeviceMenuAction()
    menuAction.updateActions(dataContext)

    assertThat(configuration.device?.displayName).isEqualTo("Pixel")

    val pixelFoldAction = menuAction.findActionByText("Pixel Fold (841 × 701 dp, 420dpi)")!!
    withContext(Dispatchers.EDT) {
      pixelFoldAction.actionPerformed(TestActionEvent.createTestEvent(dataContext))
    }

    assertThat(configuration.device?.displayName).isEqualTo("Pixel Fold")
  }

  @Test
  fun testSingleDeviceSelection() = runBlocking {
    val menuAction = DeviceMenuAction()
    val configurationManager = ConfigurationManager.getOrCreateInstance(projectRule.fixture.module)
    val groupedDevices = groupDevices(configurationManager.devices)
    val nexusXlDevices = groupedDevices[DeviceGroup.NEXUS_XL]
    assertThat(nexusXlDevices).isNotEmpty()
    val device = nexusXlDevices!!.first()
    val testDevices = listOf(device, device)
    val configurationSettings = TestConfigurationSettings(testDevices)
    val configuration =
      Configuration.create(configurationSettings, FolderConfiguration.createDefault())
    configuration.setEffectiveDevice(device, device.defaultState)

    val dataContext = SimpleDataContext.getSimpleContext(CONFIGURATIONS, listOf(configuration))
    menuAction.updateActions(dataContext)

    val allDeviceActions = menuAction.flattenActions().filterIsInstance<SetDeviceAction>()

    assertThat(allDeviceActions.size).isEqualTo(2) // 2 devices + Custom
    assertThat(allDeviceActions[0].device).isEqualTo(device)
    assertThat(allDeviceActions[1].device).isEqualTo(device)

    // Check that only one device is selected
    val selectedActions =
      allDeviceActions.filter {
        val event = TestActionEvent.createTestEvent()
        it.update(event)
        Toggleable.isSelected(event.presentation)
      }
    assertThat(selectedActions.size).isEqualTo(1)
  }

  @Test
  fun testCustomDeviceIsRecognizedAsReferenceDevice() = runBlocking {
    val layoutFile =
      projectRule.fixture.addFileToProject(
        "res/layout/layout.xml",
        // language=xml
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent"
          android:orientation="vertical">
         </LinearLayout>
      """
          .trimIndent(),
      )

    val configuration = readAction {
      ConfigurationManager.getOrCreateInstance(layoutFile.module!!)
        .getConfiguration(layoutFile.virtualFile)
    }

    val menuAction = DeviceMenuAction()
    val dataContext = SimpleDataContext.getSimpleContext(CONFIGURATIONS, listOf(configuration))
    menuAction.updateActions(dataContext)

    // Set a custom device that matches the Foldable reference device
    val foldableDevice =
      (menuAction.findActionByText("Foldable (673 × 841 dp, 420dpi)") as SetDeviceAction).device
    val builder = Device.Builder(foldableDevice)
    builder.setId(Configuration.CUSTOM_DEVICE_ID)
    val customDevice = builder.build()
    configuration.setEffectiveDevice(customDevice, customDevice.defaultState)

    menuAction.updateActions(dataContext)

    val foldableAction = menuAction.findActionByText("Foldable (673 × 841 dp, 420dpi)")!!
    val customAction = menuAction.findActionByText("Custom")!!

    val event = TestActionEvent.createTestEvent()
    withContext(Dispatchers.EDT) {
      foldableAction.update(event)
      assertThat(Toggleable.isSelected(event.presentation)).isTrue()

      customAction.update(event)
      assertThat(Toggleable.isSelected(event.presentation)).isFalse()
    }
    return@runBlocking
  }
}

private class TestConfigurationSettings(private val testDevices: List<Device>) :
  ConfigurationSettings {
  override val defaultDevice: Device?
    get() = null

  override fun selectDevice(device: Device) {}

  override var locale: Locale
    get() = Locale.ANY
    set(value) {}

  override var target: IAndroidTarget?
    get() = null
    set(value) {}

  override fun getTarget(minVersion: Int): IAndroidTarget? = null

  override val stateVersion: Int
    get() = 0

  override val configModule: ConfigurationModelModule
    get() = TODO("Not yet implemented")

  override val resolverCache: ResourceResolverCache
    get() = TODO("Not yet implemented")

  override val localesInProject: ImmutableList<Locale>
    get() = ImmutableList.of()

  override val devices: ImmutableList<Device>
    get() = ImmutableList.copyOf(testDevices)

  override val projectTarget: IAndroidTarget?
    get() = null

  override fun createDeviceForAvd(avd: AvdInfo): Device? = null

  override val highestApiTarget: IAndroidTarget?
    get() = null

  override val targets: Array<IAndroidTarget>
    get() = arrayOf()

  override fun getDeviceById(id: String): Device? = null

  override val recentDevices: List<Device>
    get() = emptyList()

  override val avdDevices: List<Device>
    get() = emptyList()
}

/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager

import com.android.resources.Density
import com.android.resources.Keyboard
import com.android.resources.KeyboardState
import com.android.resources.Navigation
import com.android.resources.NavigationState
import com.android.resources.ScreenOrientation
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.State
import com.android.sdklib.repository.targets.SystemImage
import com.android.tools.idea.deviceManager.avdmanager.DeviceManagerConnection.Companion.defaultDeviceManagerConnection
import com.android.tools.idea.deviceManager.avdmanager.actions.DeviceUiAction
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.openapi.project.Project

/**
 * [WizardModel] that holds all properties in [Device] to be used in
 * [ConfigureDeviceOptionsStep] for the user to edit.
 */
class ConfigureDeviceModel @JvmOverloads constructor(
  private val provider: DeviceUiAction.DeviceProvider, device: Device? = null, cloneDevice: Boolean = false
) : WizardModel() {
  private val bindings = BindingsManager()
  private val builder = Device.Builder()
  val deviceData: AvdDeviceData = AvdDeviceData(device, null)
  val project: Project? get() = provider.project

  init {
    if (cloneDevice) {
      requireNotNull(device) { "Can't clone a device without specifying a device." }
      deviceData.setUniqueName(String.format("%s (Edited)", device.displayName))
    }

    // Clear device's density. This will cause us to calculate the most accurate setting based on the final screen size.
    deviceData.density().set(Density.NODPI)
    device?.let { initBootProperties(it) }
  }

  private fun initBootProperties(device: Device) {
    for ((key, value) in device.bootProps) {
      builder.addBootProp(key, value)
    }
  }

  override fun handleFinished() {
    val device = buildDevice()
    defaultDeviceManagerConnection.createOrEditDevice(device)
    provider.refreshDevices()
    provider.device = device
  }

  /**
   * Once we finish editing the device, we set it to its final configuration
   */
  private fun buildDevice(): Device {
    val deviceName = deviceData.name().get()
    val tag = deviceData.deviceType().valueOrNull
    return builder.apply {
      setName(deviceName)
      setId(deviceName)
      addSoftware(deviceData.software().value)
      setManufacturer(deviceData.manufacturer().get())
      setTagId(tag?.id.takeUnless { tag == SystemImage.DEFAULT_TAG })
      addAllState(generateStates(AvdHardwareData(deviceData).buildHardware()))
    }.build()
  }

  private fun generateStates(hardware: Hardware): List<State?> {
    val states: MutableList<State?> = mutableListOf()
    if (deviceData.supportsPortrait().get()) {
      states.add(createState(ScreenOrientation.PORTRAIT, hardware, false))
    }
    if (deviceData.supportsLandscape().get()) {
      states.add(createState(ScreenOrientation.LANDSCAPE, hardware, false))
    }
    if (deviceData.hasHardwareKeyboard().get()) {
      if (deviceData.supportsPortrait().get()) {
        states.add(createState(ScreenOrientation.PORTRAIT, hardware, true))
      }
      if (deviceData.supportsLandscape().get()) {
        states.add(createState(ScreenOrientation.LANDSCAPE, hardware, true))
      }
    }

    // We've added states in the order of most common to least common, so let's mark the first one as default
    states[0]!!.isDefaultState = true
    return states
  }

  override fun dispose() {
    bindings.releaseAll()
  }

  companion object {
    private fun createState(orientation: ScreenOrientation, hardware: Hardware, hasHardwareKeyboard: Boolean): State? {
      var state: State? = null
      if (orientation == ScreenOrientation.LANDSCAPE) {
        state = State().apply {
          name = "Landscape"
          description = "The device in landscape orientation"
        }
      }
      else if (orientation == ScreenOrientation.PORTRAIT) {
        state = State().apply {
          name = "Portrait"
          description = "The device in portrait orientation"
        }
      }
      return state?.apply {
        if (hasHardwareKeyboard) {
          name += " with keyboard"
          description += " with a keyboard open"
          keyState = KeyboardState.EXPOSED
        }
        else {
          keyState = if (hardware.keyboard == Keyboard.NOKEY) KeyboardState.SOFT else KeyboardState.HIDDEN
        }
        state.hardware = hardware
        state.orientation = orientation
        navState = if (hardware.nav == Navigation.NONAV) NavigationState.HIDDEN else NavigationState.EXPOSED
      }
    }
  }
}
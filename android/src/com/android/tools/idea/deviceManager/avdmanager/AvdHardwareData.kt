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

import com.android.resources.Keyboard
import com.android.sdklib.devices.Abi
import com.android.sdklib.devices.ButtonType
import com.android.sdklib.devices.Camera
import com.android.sdklib.devices.CameraLocation
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Network
import com.android.sdklib.devices.PowerType
import com.android.sdklib.devices.Sensor
import com.android.sdklib.devices.Storage
import java.util.EnumSet

/**
 * Contains all methods needed to build a [Hardware] instance.
 */
class AvdHardwareData(private val myDeviceData: AvdDeviceData) {

  /**
   * Constructs an instance of [Hardware] based on a reasonable set of defaults and user input.
   */
  fun buildHardware(): Hardware = Hardware().apply {
    addNetwork(Network.BLUETOOTH)
    addNetwork(Network.WIFI)
    addNetwork(Network.NFC)
    addSensor(Sensor.BAROMETER)
    addSensor(Sensor.COMPASS)
    addSensor(Sensor.LIGHT_SENSOR)
    setHasMic(true)
    addInternalStorage(Storage(4, Storage.Unit.GiB))
    cpu = "Generic CPU"
    gpu = "Generic GPU"
    addAllSupportedAbis(EnumSet.allOf(Abi::class.java))
    ram = myDeviceData.ramStorage().get()
    screen = AvdScreenData(myDeviceData).createScreen()
    chargeType = PowerType.BATTERY
    keyboard = if (myDeviceData.hasHardwareKeyboard().get()) Keyboard.QWERTY else Keyboard.NOKEY
    buttonType = if (myDeviceData.hasHardwareButtons().get()) ButtonType.HARD else ButtonType.SOFT
    if (myDeviceData.hasAccelerometer().get()) {
      addSensor(Sensor.ACCELEROMETER)
    }
    if (myDeviceData.hasGyroscope().get()) {
      addSensor(Sensor.GYROSCOPE)
    }
    if (myDeviceData.hasGps().get()) {
      addSensor(Sensor.GPS)
    }
    if (myDeviceData.hasProximitySensor().get()) {
      addSensor(Sensor.PROXIMITY_SENSOR)
    }
    if (myDeviceData.hasBackCamera().get()) {
      addCamera(Camera(CameraLocation.BACK, true, true))
    }
    if (myDeviceData.hasFrontCamera().get()) {
      addCamera(Camera(CameraLocation.FRONT, true, true))
    }
    if (myDeviceData.navigation().valueOrNull != null) {
      nav = myDeviceData.navigation().value
    }
    if (myDeviceData.customSkinFile().valueOrNull != null) {
      skinFile = myDeviceData.customSkinFile().value
    }
  }
}
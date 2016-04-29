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
package com.android.tools.idea.avdmanager;

import com.android.resources.Keyboard;
import com.android.sdklib.devices.*;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

/**
 * Contains all methods needed to build a {@link Hardware} instance.
 */
public final class AvdHardwareData {
  private AvdDeviceData myDeviceData;

  public AvdHardwareData(AvdDeviceData deviceData) {
    myDeviceData = deviceData;
  }

  /**
   * Constructs an instance of {@link Hardware} based on a reasonable set of defaults and user input.
   */
  @NotNull
  public Hardware buildHardware() {
    Hardware hardware = new Hardware();

    hardware.addNetwork(Network.BLUETOOTH);
    hardware.addNetwork(Network.WIFI);
    hardware.addNetwork(Network.NFC);

    hardware.addSensor(Sensor.BAROMETER);
    hardware.addSensor(Sensor.COMPASS);
    hardware.addSensor(Sensor.LIGHT_SENSOR);

    hardware.setHasMic(true);
    hardware.addInternalStorage(new Storage(4, Storage.Unit.GiB));
    hardware.setCpu("Generic CPU");
    hardware.setGpu("Generic GPU");

    hardware.addAllSupportedAbis(EnumSet.allOf(Abi.class));

    hardware.setChargeType(PowerType.BATTERY);

    if (myDeviceData.hasAccelerometer().get()) {
      hardware.addSensor(Sensor.ACCELEROMETER);
    }

    if (myDeviceData.hasGyroscope().get()) {
      hardware.addSensor(Sensor.GYROSCOPE);
    }

    if (myDeviceData.hasGps().get()) {
      hardware.addSensor(Sensor.GPS);
    }

    if (myDeviceData.hasProximitySensor().get()) {
      hardware.addSensor(Sensor.PROXIMITY_SENSOR);
    }

    if (myDeviceData.hasBackCamera().get()) {
      hardware.addCamera(new Camera(CameraLocation.BACK, true, true));
    }

    if (myDeviceData.hasFrontCamera().get()) {
      hardware.addCamera(new Camera(CameraLocation.FRONT, true, true));
    }

    if (myDeviceData.hasHardwareKeyboard().get()) {
      hardware.setKeyboard(Keyboard.QWERTY);
    }
    else {
      hardware.setKeyboard(Keyboard.NOKEY);
    }

    if (myDeviceData.hasHardwareButtons().get()) {
      hardware.setButtonType(ButtonType.HARD);
    }
    else {
      hardware.setButtonType(ButtonType.SOFT);
    }

    if (myDeviceData.navigation().getValueOrNull() != null) {
      hardware.setNav(myDeviceData.navigation().getValue());
    }

    if (myDeviceData.customSkinFile().getValueOrNull() != null) {
      hardware.setSkinFile(myDeviceData.customSkinFile().getValue());
    }

    hardware.setRam(myDeviceData.ramStorage().get());

    hardware.setScreen(new AvdScreenData(myDeviceData).createScreen());

    return hardware;
  }
}

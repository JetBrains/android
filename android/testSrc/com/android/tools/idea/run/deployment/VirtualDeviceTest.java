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
package com.android.tools.idea.run.deployment;

import static org.junit.Assert.assertTrue;

import com.android.tools.idea.run.AndroidDevice;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceTest {
  @Test
  public void matchesDeviceIsInManagerAndKeyIsVirtualDevicePath() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(new VirtualDevicePath("/home/juancnuno/.android/avd/Pixel_4_API_30.avd"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setNameKey(new VirtualDeviceName("Pixel_4_API_30"))
      .build();

    Key key = new VirtualDevicePath("/home/juancnuno/.android/avd/Pixel_4_API_30.avd");

    // Act
    boolean matches = device.matches(key);

    // Assert
    assertTrue(matches);
  }

  @Test
  public void matchesDeviceIsInManagerAndKeyIsNonprefixedKey() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(new VirtualDevicePath("/home/juancnuno/.android/avd/Pixel_4_API_30.avd"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setNameKey(new VirtualDeviceName("Pixel_4_API_30"))
      .build();

    Key key = new NonprefixedKey("Pixel_4_API_30");

    // Act
    boolean matches = device.matches(key);

    // Assert
    assertTrue(matches);
  }

  @Test
  public void matchesDeviceIsntInManagerAndKeyIsSerialNumber() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("emulator-5554")
      .setKey(new SerialNumber("emulator-5554"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Key key = new SerialNumber("emulator-5554");

    // Act
    boolean matches = device.matches(key);

    // Assert
    assertTrue(matches);
  }

  @Test
  public void matchesDeviceIsntInManagerAndKeyIsNonprefixedKey() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("emulator-5554")
      .setKey(new SerialNumber("emulator-5554"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Key key = new NonprefixedKey("emulator-5554");

    // Act
    boolean matches = device.matches(key);

    // Assert
    assertTrue(matches);
  }

  @Test
  public void hasKeyContainedByDeviceIsInManagerAndKeyIsVirtualDevicePath() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(new VirtualDevicePath("/home/juancnuno/.android/avd/Pixel_4_API_30.avd"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setNameKey(new VirtualDeviceName("Pixel_4_API_30"))
      .build();

    Collection<Key> keys = Collections.singleton(new VirtualDevicePath("/home/juancnuno/.android/avd/Pixel_4_API_30.avd"));

    // Act
    boolean contained = device.hasKeyContainedBy(keys);

    // Assert
    assertTrue(contained);
  }

  @Test
  public void hasKeyContainedByDeviceIsInManagerAndKeyIsNonprefixedKey() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(new VirtualDevicePath("/home/juancnuno/.android/avd/Pixel_4_API_30.avd"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setNameKey(new VirtualDeviceName("Pixel_4_API_30"))
      .build();

    Collection<Key> keys = Collections.singleton(new NonprefixedKey("Pixel_4_API_30"));

    // Act
    boolean contained = device.hasKeyContainedBy(keys);

    // Assert
    assertTrue(contained);
  }

  @Test
  public void hasKeyContainedByDeviceIsntInManagerAndKeyIsSerialNumber() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("emulator-5554")
      .setKey(new SerialNumber("emulator-5554"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Collection<Key> keys = Collections.singleton(new SerialNumber("emulator-5554"));

    // Act
    boolean contained = device.hasKeyContainedBy(keys);

    // Assert
    assertTrue(contained);
  }

  @Test
  public void hasKeyContainedByDeviceIsntInManagerAndKeyIsNonprefixedKey() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("emulator-5554")
      .setKey(new SerialNumber("emulator-5554"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Collection<Key> keys = Collections.singleton(new NonprefixedKey("emulator-5554"));

    // Act
    boolean contained = device.hasKeyContainedBy(keys);

    // Assert
    assertTrue(contained);
  }
}

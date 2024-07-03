/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.sdklib.devices.Device;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class NameComparatorTest {
  @Test
  public void comparePhone() {
    // Arrange
    var expectedDevices = List.of(mockDevice("Small Phone"),
                                  mockDevice("Medium Phone"),
                                  mockDevice("Resizable (Experimental)"),
                                  mockDevice("Pixel Fold"),
                                  mockDevice("Pixel 8a"),
                                  mockDevice("Pixel 8 Pro"),
                                  mockDevice("Pixel 8"),
                                  mockDevice("Pixel 7a"),
                                  mockDevice("Pixel 7 Pro"),
                                  mockDevice("Pixel 7"),
                                  mockDevice("Pixel 6a"),
                                  mockDevice("Pixel 6 Pro"),
                                  mockDevice("Pixel 6"),
                                  mockDevice("Pixel 5"),
                                  mockDevice("Pixel 4a"),
                                  mockDevice("Pixel 4 XL"),
                                  mockDevice("Pixel 4"),
                                  mockDevice("Pixel 3a XL"),
                                  mockDevice("Pixel 3a"),
                                  mockDevice("Pixel 3 XL"),
                                  mockDevice("Pixel 3"),
                                  mockDevice("Pixel 2 XL"),
                                  mockDevice("Pixel 2"),
                                  mockDevice("Pixel XL"),
                                  mockDevice("Pixel"));

    var actualDevices = shuffle(expectedDevices);
    var comparator = new NameComparator(() -> true);

    // Act
    actualDevices.sort(comparator);

    // Assert
    assertEquals(expectedDevices, actualDevices);
  }

  @Test
  public void compareTablet() {
    // Arrange
    var expectedDevices = List.of(mockDevice("Medium Tablet"),
                                  mockDevice("Pixel Tablet"),
                                  mockDevice("Pixel C"));

    var actualDevices = shuffle(expectedDevices);
    var comparator = new NameComparator();

    // Act
    actualDevices.sort(comparator);

    // Assert
    assertEquals(expectedDevices, actualDevices);
  }

  @Test
  public void compareWearOs() {
    // Arrange
    var expectedDevices = List.of(mockDevice("Wear OS Square"),
                                  mockDevice("Wear OS Small Round"),
                                  mockDevice("Wear OS Rectangular"),
                                  mockDevice("Wear OS Large Round"));

    var actualDevices = shuffle(expectedDevices);
    var comparator = new NameComparator();

    // Act
    actualDevices.sort(comparator);

    // Assert
    assertEquals(expectedDevices, actualDevices);
  }

  @Test
  public void compareDesktop() {
    // Arrange
    var expectedDevices = List.of(mockDevice("Small Desktop"),
                                  mockDevice("Medium Desktop"),
                                  mockDevice("Large Desktop"));

    var actualDevices = shuffle(expectedDevices);
    var comparator = new NameComparator();

    // Act
    actualDevices.sort(comparator);

    // Assert
    assertEquals(expectedDevices, actualDevices);
  }

  @Test
  public void compareTv() {
    // Arrange
    var expectedDevices = List.of(mockDevice("Television (720p)"),
                                  mockDevice("Television (4K)"),
                                  mockDevice("Television (1080p)"));

    var actualDevices = shuffle(expectedDevices);
    var comparator = new NameComparator();

    // Act
    actualDevices.sort(comparator);

    // Assert
    assertEquals(expectedDevices, actualDevices);
  }

  @Test
  public void compareLegacy() {
    var expectedDevices = List.of(mockDevice("Nexus S"),
                                  mockDevice("Nexus One"),
                                  mockDevice("Nexus 9"),
                                  mockDevice("Nexus 7 (2012)"),
                                  mockDevice("Nexus 7"),
                                  mockDevice("Nexus 6P"),
                                  mockDevice("Nexus 6"),
                                  mockDevice("Nexus 5X"),
                                  mockDevice("Nexus 5"),
                                  mockDevice("Nexus 4"),
                                  mockDevice("Nexus 10"),
                                  mockDevice("Galaxy Nexus"),
                                  mockDevice("8\" Fold-out"),
                                  mockDevice("7\" WSVGA (Tablet)"),
                                  mockDevice("7.4\" Rollable"),
                                  mockDevice("6.7\" Horizontal Fold-in"),
                                  mockDevice("5.4\" FWVGA"),
                                  mockDevice("5.1\" WVGA"),
                                  mockDevice("4\" WVGA (Nexus S)"),
                                  mockDevice("4.7\" WXGA"),
                                  mockDevice("4.65\" 720p (Galaxy Nexus)"),
                                  mockDevice("3.7\" WVGA (Nexus One)"),
                                  mockDevice("3.7\" FWVGA slider"),
                                  mockDevice("3.4\" WQVGA"),
                                  mockDevice("3.3\" WQVGA"),
                                  mockDevice("3.2\" QVGA (ADP2)"),
                                  mockDevice("3.2\" HVGA slider (ADP1)"),
                                  mockDevice("2.7\" QVGA slider"),
                                  mockDevice("2.7\" QVGA"),
                                  mockDevice("13.5\" Freeform"),
                                  mockDevice("10.1\" WXGA (Tablet)"));

    var actualDevices = shuffle(expectedDevices);
    var comparator = new NameComparator();

    // Act
    actualDevices.sort(comparator);

    // Assert
    assertEquals(expectedDevices, actualDevices);
  }

  @NotNull
  private static List<Device> shuffle(@NotNull Collection<Device> expectedDevices) {
    var actualDevices = new ArrayList<>(expectedDevices);
    Collections.shuffle(actualDevices);

    var actualNames = actualDevices.stream()
      .map(Device::getDisplayName)
      .collect(Collectors.joining(", "));

    System.out.println("Shuffled devices: " + actualNames);
    return actualDevices;
  }

  @Test
  public void compareIdsAreDifferent() {
    // Arrange
    var comparator = new NameComparator();

    // system-images;android-21;android-tv;x86
    var device1 = mockDevice("Android TV (1080p)", "Android TV (1080p)");

    // tv.xml at Commit df3a41ec30df6edc79a4e3823698640f114b05a4
    var device2 = mockDevice("Android TV (1080p)", "tv_1080p");

    // Act
    var result = comparator.compare(device1, device2);

    // Assert
    assertTrue(result < 0);
  }

  private static @NotNull Device mockDevice(@NotNull String name) {
    return mockDevice(name, "");
  }

  private static @NotNull Device mockDevice(@NotNull String name, @NotNull String id) {
    var device = Mockito.mock(Device.class);

    Mockito.when(device.getDisplayName()).thenReturn(name);
    Mockito.when(device.getId()).thenReturn(id);
    Mockito.when(device.toString()).thenReturn(name);

    return device;
  }
}

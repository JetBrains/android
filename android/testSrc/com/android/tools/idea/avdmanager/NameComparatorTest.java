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
package com.android.tools.idea.avdmanager;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.devices.Device;
import java.util.ArrayList;
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
  public void compare() {
    // Arrange
    var expectedDevices = List.of(mockDevice("7.6\" Fold-in with outer display"),
                                  mockDevice("8\" Fold-out"),
                                  mockDevice("Pixel XL"),
                                  mockDevice("Resizable (Experimental)"),
                                  mockDevice("Medium Tablet"),
                                  mockDevice("Medium Phone"),
                                  mockDevice("Small Phone"));

    var actualDevices = new ArrayList<>(expectedDevices);
    Collections.shuffle(actualDevices);

    var actualNames = actualDevices.stream()
      .map(Device::getDisplayName)
      .collect(Collectors.joining(", "));

    System.out.println("Shuffled devices: " + actualNames);

    var comparator = new NameComparator();

    // Act
    actualDevices.sort(comparator);

    // Assert
    assertEquals(expectedDevices, actualDevices);
  }

  @NotNull
  private static Device mockDevice(@NotNull String name) {
    var device = Mockito.mock(Device.class);
    Mockito.when(device.getDisplayName()).thenReturn(name);

    return device;
  }
}

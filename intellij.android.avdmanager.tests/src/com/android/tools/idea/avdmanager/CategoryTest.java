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
import com.android.sdklib.devices.Hardware;
import com.android.sdklib.devices.Screen;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class CategoryTest {
  @Test
  public void valueOfDefinitionPhone() {
    // Arrange
    var definition = mockDefinition(null, mockHardware(0));

    // Act
    var category = Category.valueOfDefinition(definition);

    // Assert
    assertEquals(Category.PHONE, category);
  }

  @Test
  public void valueOfDefinitionTablet() {
    // Arrange
    var definition = mockDefinition(null, mockHardware(10.055));

    // Act
    var category = Category.valueOfDefinition(definition);

    // Assert
    assertEquals(Category.TABLET, category);
  }

  @Test
  public void valueOfDefinitionWearOs() {
    // Arrange
    var definition = mockDefinition("android-wear", mockHardware(0));

    // Act
    var category = Category.valueOfDefinition(definition);

    // Assert
    assertEquals(Category.WEAR_OS, category);
  }

  @Test
  public void valueOfDefinitionDesktop() {
    // Arrange
    var definition = mockDefinition("android-desktop", null);

    // Act
    var category = Category.valueOfDefinition(definition);

    // Assert
    assertEquals(Category.DESKTOP, category);
  }

  @Test
  public void valueOfDefinitionTv() {
    // Arrange
    var definition = mockDefinition("android-tv", mockHardware(0));

    // Act
    var category = Category.valueOfDefinition(definition);

    // Assert
    assertEquals(Category.TV, category);
  }

  @Test
  public void valueOfDefinitionAutomotive() {
    // Arrange
    var definition = mockDefinition("android-automotive", mockHardware(0));

    // Act
    var category = Category.valueOfDefinition(definition);

    // Assert
    assertEquals(Category.AUTOMOTIVE, category);
  }

  @Test
  public void valueOfDefinitionLegacy() {
    // Arrange
    var definition = mockDefinition(null, null);
    Mockito.when(definition.getIsDeprecated()).thenReturn(true);

    // Act
    var category = Category.valueOfDefinition(definition);

    // Assert
    assertEquals(Category.LEGACY, category);
  }

  @NotNull
  private static Device mockDefinition(@Nullable String id, @Nullable Hardware hardware) {
    var definition = Mockito.mock(Device.class);

    Mockito.when(definition.getTagId()).thenReturn(id);
    Mockito.when(definition.getDefaultHardware()).thenReturn(hardware);

    return definition;
  }

  @NotNull
  private static Hardware mockHardware(double length) {
    var screen = Mockito.mock(Screen.class);
    Mockito.when(screen.getDiagonalLength()).thenReturn(length);

    var hardware = Mockito.mock(Hardware.class);
    Mockito.when(hardware.getScreen()).thenReturn(screen);

    return hardware;
  }
}

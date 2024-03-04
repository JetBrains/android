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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CategoryTest {
  @Test
  public void valueOfDefinitionPhone() {
    // Arrange
    var definition = Definitions.mockPhone();

    // Act
    var category = Category.valueOfDefinition(definition);

    // Assert
    assertEquals(Category.PHONE, category);
  }

  @Test
  public void valueOfDefinitionTablet() {
    // Arrange
    var definition = Definitions.mockTablet();

    // Act
    var category = Category.valueOfDefinition(definition);

    // Assert
    assertEquals(Category.TABLET, category);
  }

  @Test
  public void valueOfDefinitionWearOs() {
    // Arrange
    var definition = Definitions.mockWearOsDefinition();

    // Act
    var category = Category.valueOfDefinition(definition);

    // Assert
    assertEquals(Category.WEAR_OS, category);
  }

  @Test
  public void valueOfDefinitionDesktop() {
    // Arrange
    var definition = Definitions.mockDesktop();

    // Act
    var category = Category.valueOfDefinition(definition);

    // Assert
    assertEquals(Category.DESKTOP, category);
  }

  @Test
  public void valueOfDefinitionTv() {
    // Arrange
    var definition = Definitions.mockTv();

    // Act
    var category = Category.valueOfDefinition(definition);

    // Assert
    assertEquals(Category.TV, category);
  }

  @Test
  public void valueOfDefinitionAutomotive() {
    // Arrange
    var definition = Definitions.mockAutomotiveDefinition();

    // Act
    var category = Category.valueOfDefinition(definition);

    // Assert
    assertEquals(Category.AUTOMOTIVE, category);
  }

  @Test
  public void valueOfDefinitionLegacy() {
    // Arrange
    var definition = Definitions.mockLegacyDefinition();

    // Act
    var category = Category.valueOfDefinition(definition);

    // Assert
    assertEquals(Category.LEGACY, category);
  }

  @Test
  public void valueOfDefinitionFallsBackToPhone() {
    // Arrange
    var definition = Definitions.mockDefinition("chromeos", Definitions.mockHardware(0), "Pixelbook (beta)", "Pixelbook (beta)");

    // Act
    var category = Category.valueOfDefinition(definition);

    // Assert
    assertEquals(Category.PHONE, category);
  }
}

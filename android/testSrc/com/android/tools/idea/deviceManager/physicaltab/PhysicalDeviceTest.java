/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.physicaltab;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PhysicalDeviceTest {
  @Test
  public void compareToPixel3IsDisconnectedAndPixel5IsDisconnected() {
    // Arrange
    PhysicalDevice pixel3 = new PhysicalDevice("86UX00F4R");
    PhysicalDevice pixel5 = new PhysicalDevice("0A071FDD4003ZG");

    // Act
    int i = pixel3.compareTo(pixel5);

    // Assert
    assertEquals(0, i);
  }

  @Test
  public void compareToPixel3IsDisconnectedAndPixel5IsConnected() {
    // Arrange
    PhysicalDevice pixel3 = new PhysicalDevice("86UX00F4R");
    PhysicalDevice pixel5 = new PhysicalDevice("0A071FDD4003ZG", Instant.parse("2021-03-24T22:38:05.890570Z"));

    // Act
    int i = pixel3.compareTo(pixel5);

    // Assert
    assertTrue(i > 0);
  }

  @Test
  public void compareToPixel3IsConnectedAndPixel5IsDisconnected() {
    // Arrange
    PhysicalDevice pixel3 = new PhysicalDevice("86UX00F4R", Instant.parse("2021-03-24T22:38:05.890570Z"));
    PhysicalDevice pixel5 = new PhysicalDevice("0A071FDD4003ZG");

    // Act
    int i = pixel3.compareTo(pixel5);

    // Assert
    assertTrue(i < 0);
  }

  @Test
  public void compareToPixel3IsConnectedAndPixel5IsConnected() {
    // Arrange
    PhysicalDevice pixel3 = new PhysicalDevice("86UX00F4R", Instant.parse("2021-03-24T22:38:05.890570Z"));
    PhysicalDevice pixel5 = new PhysicalDevice("0A071FDD4003ZG", Instant.parse("2021-03-24T22:38:05.890570Z"));

    // Act
    int i = pixel3.compareTo(pixel5);

    // Assert
    assertEquals(0, i);
  }

  @Test
  public void compareToPixel3WasConnectedBeforePixel5() {
    // Arrange
    PhysicalDevice pixel3 = new PhysicalDevice("86UX00F4R", Instant.parse("2021-03-24T22:38:05.890570Z"));
    PhysicalDevice pixel5 = new PhysicalDevice("0A071FDD4003ZG", Instant.parse("2021-03-24T22:38:05.890571Z"));

    // Act
    int i = pixel3.compareTo(pixel5);

    // Assert
    assertTrue(i > 0);
  }

  @Test
  public void compareToPixel3WasConnectedAfterPixel5() {
    // Arrange
    PhysicalDevice pixel3 = new PhysicalDevice("86UX00F4R", Instant.parse("2021-03-24T22:38:05.890571Z"));
    PhysicalDevice pixel5 = new PhysicalDevice("0A071FDD4003ZG", Instant.parse("2021-03-24T22:38:05.890570Z"));

    // Act
    int i = pixel3.compareTo(pixel5);

    // Assert
    assertTrue(i < 0);
  }
}

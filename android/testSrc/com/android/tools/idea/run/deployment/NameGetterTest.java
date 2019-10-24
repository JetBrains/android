/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.ddms.DeviceNameProperties;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class NameGetterTest {
  @Test
  public void getNameManufacturerAndModelAreNull() {
    // Arrange
    DeviceNameProperties properties = new DeviceNameProperties(null, null, null, null);

    // Act
    Object name = NameGetter.getName(properties);

    // Assert
    assertEquals("Physical Device", name);
  }

  @Test
  public void getNameManufacturerIsNull() {
    // Arrange
    DeviceNameProperties properties = new DeviceNameProperties("Nexus 5X", null, null, null);

    // Act
    Object name = NameGetter.getName(properties);

    // Assert
    assertEquals("Nexus 5X", name);
  }

  @Test
  public void getNameModelIsNull() {
    // Arrange
    DeviceNameProperties properties = new DeviceNameProperties(null, "LGE", null, null);

    // Act
    Object name = NameGetter.getName(properties);

    // Assert
    assertEquals("LGE Device", name);
  }

  @Test
  public void getName() {
    // Arrange
    DeviceNameProperties properties = new DeviceNameProperties("Nexus 5X", "LGE", null, null);

    // Act
    Object name = NameGetter.getName(properties);

    // Assert
    assertEquals("LGE Nexus 5X", name);
  }
}

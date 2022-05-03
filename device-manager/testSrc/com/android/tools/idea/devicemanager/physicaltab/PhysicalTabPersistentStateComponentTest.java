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
package com.android.tools.idea.devicemanager.physicaltab;

import static org.junit.Assert.assertEquals;

import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PhysicalTabPersistentStateComponentTest {
  private final PhysicalTabPersistentStateComponent myComponent = new PhysicalTabPersistentStateComponent();

  @Test
  public void get() {
    // Arrange
    Collection<PhysicalDevice> expectedDevices = List.of(TestPhysicalDevices.GOOGLE_PIXEL_3);
    myComponent.set(expectedDevices);

    // Act
    Object actualDevices = myComponent.get();

    // Assert
    assertEquals(expectedDevices, actualDevices);
  }

  @Test
  public void getFilterKeyIsPersistent() {
    // Arrange
    myComponent.set(List.of(TestPhysicalDevices.ONLINE_COMPAL_FALSTER, TestPhysicalDevices.GOOGLE_PIXEL_3));

    // Act
    Object actualDevices = myComponent.get();

    // Assert
    assertEquals(List.of(TestPhysicalDevices.GOOGLE_PIXEL_3), actualDevices);
  }
}

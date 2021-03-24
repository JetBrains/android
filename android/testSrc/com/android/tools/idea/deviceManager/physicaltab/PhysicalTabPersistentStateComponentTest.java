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

import java.util.Collection;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PhysicalTabPersistentStateComponentTest {
  @Test
  public void get() {
    // Arrange
    Collection<PhysicalDevice> expectedDevices = Collections.singletonList(new PhysicalDevice("86UX00F4R"));

    PhysicalTabPersistentStateComponent component = new PhysicalTabPersistentStateComponent();
    component.set(expectedDevices);

    // Act
    Object actualDevices = component.get();

    // Assert
    assertEquals(expectedDevices, actualDevices);
  }
}

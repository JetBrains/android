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

import javax.swing.plaf.ColorUIResource;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

final class PhysicalDeviceTables {
  private PhysicalDeviceTables() {
  }

  static @NotNull PhysicalDeviceTable mock(@NotNull PhysicalDevice device) {
    PhysicalDeviceTable table = Mockito.mock(PhysicalDeviceTable.class);

    Mockito.when(table.getDeviceAt(0)).thenReturn(device);
    Mockito.when(table.getSelectionBackground()).thenReturn(new ColorUIResource(47, 101, 202));
    Mockito.when(table.getSelectionForeground()).thenReturn(new ColorUIResource(187, 187, 187));

    return table;
  }
}

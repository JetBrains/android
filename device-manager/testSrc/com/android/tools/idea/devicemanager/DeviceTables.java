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
package com.android.tools.idea.devicemanager;

import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevice;
import com.android.tools.idea.devicemanager.physicaltab.PhysicalDeviceTable;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDevice;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceTable;
import javax.swing.plaf.ColorUIResource;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

public final class DeviceTables {
  private DeviceTables() {
  }

  public static @NotNull VirtualDeviceTable mock(@NotNull VirtualDevice device) {
    VirtualDeviceTable table = Mockito.mock(VirtualDeviceTable.class);
    stubCalls(table, device);

    return table;
  }

  public static @NotNull PhysicalDeviceTable mock(@NotNull PhysicalDevice device) {
    PhysicalDeviceTable table = Mockito.mock(PhysicalDeviceTable.class);
    stubCalls(table, device);

    return table;
  }

  private static <D extends Device> void stubCalls(@NotNull DeviceTable<@NotNull D> table, @NotNull D device) {
    Mockito.when(table.getBackground()).thenReturn(new ColorUIResource(60, 63, 65));
    Mockito.when(table.getDeviceAt(0)).thenReturn(device);
    Mockito.when(table.getSelectionBackground()).thenReturn(new ColorUIResource(47, 101, 202));
    Mockito.when(table.getSelectionForeground()).thenReturn(new ColorUIResource(187, 187, 187));
  }
}

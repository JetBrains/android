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
package com.android.tools.idea.devicemanager.virtualtab;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.devicemanager.DeviceTables;
import com.android.tools.idea.devicemanager.DeviceType;
import com.intellij.icons.AllIcons;
import java.util.Optional;
import javax.swing.JTable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceIconButtonTableCellRendererTest {
  @Test
  public void getTableCellRendererComponent() {
    // Arrange
    VirtualDevice device = new VirtualDevice.Builder()
      .setKey(TestVirtualDevices.PIXEL_5_API_31_KEY)
      .setIcon(AllIcons.Actions.Download)
      .setName("Pixel 5 API 31")
      .setTarget("Android 12.0 Google APIs")
      .setCpuArchitecture("x86_64")
      .setAvdInfo(Mockito.mock(AvdInfo.class))
      .build();

    VirtualDeviceIconButtonTableCellRenderer renderer = new VirtualDeviceIconButtonTableCellRenderer();
    JTable table = DeviceTables.mock(device);

    // Act
    renderer.getTableCellRendererComponent(table, DeviceType.PHONE, false, false, 0, 0);

    // Assert
    assertEquals(Optional.of(AllIcons.Actions.Download), renderer.getButton().getDefaultIcon());
  }
}

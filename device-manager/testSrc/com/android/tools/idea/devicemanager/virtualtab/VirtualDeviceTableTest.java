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

import static org.junit.Assert.assertFalse;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.targets.SystemImage;
import java.nio.file.Paths;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceTableTest {
  private final @NotNull VirtualDevicePanel myPanel = Mockito.mock(VirtualDevicePanel.class);

  @Test
  public void emptyTable() {
    VirtualDeviceTableModel model = new VirtualDeviceTableModel(Collections.emptyList());
    VirtualDeviceTable table = new VirtualDeviceTable(myPanel, model);

    assertFalse(table.getSelectedDevice().isPresent());
  }

  @Test
  public void unselectedDevice() {
    AvdInfo device = new AvdInfo("Pixel 5",
                                 Paths.get("ini", "file"),
                                 Paths.get("data", "folder", "path"),
                                 Mockito.mock(SystemImage.class),
                                 null);

    VirtualDeviceTableModel model = new VirtualDeviceTableModel(Collections.singletonList(TestVirtualDevices.pixel5Api31(device)));
    VirtualDeviceTable table = new VirtualDeviceTable(myPanel, model);

    assertFalse(table.getSelectedDevice().isPresent());
  }
}

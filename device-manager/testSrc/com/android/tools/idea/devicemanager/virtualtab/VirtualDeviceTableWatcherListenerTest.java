/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.sdklib.internal.avd.AvdInfo;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceTableWatcherListenerTest {
  private final @NotNull VirtualDeviceTable myTable;
  private final @NotNull VirtualDeviceWatcherListener myListener;
  private final @NotNull VirtualDeviceWatcher myWatcher;
  private final @NotNull AvdInfo myAvd;
  private final @NotNull VirtualDevice myDevice;

  public VirtualDeviceTableWatcherListenerTest() {
    myTable = Mockito.mock(VirtualDeviceTable.class);
    Mockito.when(myTable.getModel()).thenReturn(Mockito.mock(VirtualDeviceTableModel.class));

    myListener = new VirtualDeviceTableWatcherListener(myTable);
    myWatcher = Mockito.mock(VirtualDeviceWatcher.class);

    myAvd = Mockito.mock(AvdInfo.class);
    Mockito.when(myAvd.getId()).thenReturn(TestVirtualDevices.PIXEL_5_API_31_KEY.toString());

    myDevice = TestVirtualDevices.pixel5Api31(myAvd);
  }

  @Test
  public void addAvd() {
    Mockito.when(myTable.getModel().getDevices()).thenReturn(List.of());

    myListener.virtualDevicesChanged(new VirtualDeviceWatcherEvent(myWatcher, List.of(myAvd)));

    Mockito.verify(myTable).addDevice(TestVirtualDevices.PIXEL_5_API_31_KEY);
  }

  @Test
  public void removeAvd() {
    Mockito.when(myTable.getModel().getDevices()).thenReturn(List.of(myDevice));

    myListener.virtualDevicesChanged(new VirtualDeviceWatcherEvent(myWatcher, List.of()));

    Mockito.verify(myTable.getModel()).remove(TestVirtualDevices.PIXEL_5_API_31_KEY);
  }

  @Test
  public void changeAvd() {
    Mockito.when(myTable.getModel().getDevices()).thenReturn(List.of(myDevice));

    AvdInfo changedAvd = Mockito.mock(AvdInfo.class);
    Mockito.when(changedAvd.getId()).thenReturn(TestVirtualDevices.PIXEL_5_API_31_KEY.toString());

    myListener.virtualDevicesChanged(new VirtualDeviceWatcherEvent(myWatcher, List.of(changedAvd)));

    Mockito.verify(myTable).reloadDevice(TestVirtualDevices.PIXEL_5_API_31_KEY);
  }
}

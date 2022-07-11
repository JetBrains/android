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

import static org.junit.Assert.assertEquals;

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.CountDownLatchAssert;
import com.android.tools.idea.devicemanager.CountDownLatchFutureCallback;
import com.android.tools.idea.devicemanager.Key;
import com.android.tools.idea.testing.swing.TableModelEventArgumentMatcher;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceTableModelTest {
  private final @NotNull AvdManagerConnection myConnection = Mockito.mock(AvdManagerConnection.class);
  private final @NotNull AvdInfo myAvd = Mockito.mock(AvdInfo.class);
  private final @NotNull Collection<@NotNull VirtualDevice> myDevices = List.of(TestVirtualDevices.pixel5Api31(myAvd));
  private final @NotNull TableModelListener myListener = Mockito.mock(TableModelListener.class);

  @Test
  public void setAllOnline() throws InterruptedException {
    // Arrange
    Mockito.when(myConnection.isAvdRunning(myAvd)).thenReturn(true);
    CountDownLatch latch = new CountDownLatch(1);

    VirtualDeviceTableModel model = new VirtualDeviceTableModel(null,
                                                                myDevices,
                                                                () -> myConnection,
                                                                (m, key) -> newSetOnline(m, key, latch));

    model.addTableModelListener(myListener);

    // Act
    model.setAllOnline();

    // Assert
    CountDownLatchAssert.await(latch);

    assertEquals(List.of(TestVirtualDevices.onlinePixel5Api31(myAvd)), model.getDevices());
    Mockito.verify(myListener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(new TableModelEvent(model, 0))));
  }

  private static @NotNull FutureCallback<@NotNull Boolean> newSetOnline(@NotNull VirtualDeviceTableModel model,
                                                                        @NotNull Key key,
                                                                        @NotNull CountDownLatch latch) {
    return new CountDownLatchFutureCallback<>(VirtualDeviceTableModel.newSetOnline(model, key), latch);
  }

  @Test
  public void setValueAtStartAvdSucceeded() {
    // Arrange
    Mockito.when(myConnection.startAvd(null, myAvd)).thenReturn(Futures.immediateFuture(Mockito.mock(IDevice.class)));

    TableModel model = new VirtualDeviceTableModel(null, myDevices, () -> myConnection, VirtualDeviceTableModel::newSetOnline);
    model.addTableModelListener(myListener);

    // Act
    model.setValueAt(VirtualDevice.State.LAUNCHING, 0, VirtualDeviceTableModel.LAUNCH_OR_STOP_MODEL_COLUMN_INDEX);

    // Assert
    Object device = new VirtualDevice.Builder()
      .setKey(TestVirtualDevices.newKey("Pixel_5_API_31"))
      .setName("Pixel 5 API 31")
      .setTarget("Android 12.0 Google APIs")
      .setCpuArchitecture("x86_64")
      .setAndroidVersion(new AndroidVersion(31))
      .setState(VirtualDevice.State.LAUNCHING)
      .setAvdInfo(myAvd)
      .build();

    assertEquals(device, model.getValueAt(0, VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX));

    TableModelEvent event = new TableModelEvent(model, 0, 0, VirtualDeviceTableModel.LAUNCH_OR_STOP_MODEL_COLUMN_INDEX);
    Mockito.verify(myListener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(event)));
  }

  @Test
  public void setValueAtStartAvdFailed() throws Exception {
    // Arrange
    Mockito.when(myConnection.startAvd(null, myAvd)).thenReturn(Futures.immediateFailedFuture(new RuntimeException()));
    CountDownLatch latch = new CountDownLatch(1);

    TableModel model = new VirtualDeviceTableModel(null, myDevices, () -> myConnection, (m, key) -> newSetOnline(m, key, latch));
    model.addTableModelListener(myListener);

    // Act
    model.setValueAt(VirtualDevice.State.LAUNCHING, 0, VirtualDeviceTableModel.LAUNCH_OR_STOP_MODEL_COLUMN_INDEX);

    // Assert
    CountDownLatchAssert.await(latch);

    assertEquals(TestVirtualDevices.pixel5Api31(myAvd), model.getValueAt(0, VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX));

    TableModelEvent event = new TableModelEvent(model, 0, 0, VirtualDeviceTableModel.LAUNCH_OR_STOP_MODEL_COLUMN_INDEX);
    Mockito.verify(myListener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(event)));

    Mockito.verify(myListener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(new TableModelEvent(model, 0))));
  }
}

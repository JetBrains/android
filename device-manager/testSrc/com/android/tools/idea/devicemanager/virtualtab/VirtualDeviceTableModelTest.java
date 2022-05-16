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

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.CountDownLatchAssert;
import com.android.tools.idea.devicemanager.CountDownLatchFutureCallback;
import com.android.tools.idea.devicemanager.Key;
import com.android.tools.idea.testing.swing.TableModelEventArgumentMatcher;
import com.google.common.util.concurrent.FutureCallback;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceTableModelTest {
  @Test
  public void setAllOnline() throws InterruptedException {
    // Arrange
    AvdInfo avd = Mockito.mock(AvdInfo.class);
    Collection<VirtualDevice> devices = List.of(TestVirtualDevices.pixel5Api31(avd));

    AvdManagerConnection connection = Mockito.mock(AvdManagerConnection.class);
    Mockito.when(connection.isAvdRunning(avd)).thenReturn(true);

    CountDownLatch latch = new CountDownLatch(1);
    TableModelListener listener = Mockito.mock(TableModelListener.class);

    VirtualDeviceTableModel model = new VirtualDeviceTableModel(devices, () -> connection, (m, key) -> newSetOnline(m, key, latch));
    model.addTableModelListener(listener);

    // Act
    model.setAllOnline();

    // Assert
    CountDownLatchAssert.await(latch);
    assertEquals(List.of(TestVirtualDevices.onlinePixel5Api31(avd)), model.getDevices());

    TableModelEvent event = new TableModelEvent(model, 0, 0, VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX);
    Mockito.verify(listener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(event)));
  }

  private static @NotNull FutureCallback<@NotNull Boolean> newSetOnline(@NotNull VirtualDeviceTableModel model,
                                                                        @NotNull Key key,
                                                                        @NotNull CountDownLatch latch) {
    return new CountDownLatchFutureCallback<>(VirtualDeviceTableModel.newSetOnline(model, key), latch);
  }
}

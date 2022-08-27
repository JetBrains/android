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
import com.android.ddmlib.IDevice.DeviceState;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.devicemanager.CountDownLatchAssert;
import com.android.tools.idea.devicemanager.CountDownLatchFutureCallback;
import com.android.tools.idea.devicemanager.Key;
import com.android.tools.idea.devicemanager.virtualtab.ProcessManager.State;
import com.google.common.util.concurrent.FutureCallback;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class ProcessManagerTest {
  private static final @NotNull Object KEY = TestVirtualDevices.newKey("Pixel_6_API_33");

  private final @NotNull AvdInfo myAvd;
  private final @NotNull AvdManagerConnection myConnection;
  private final @NotNull CountDownLatch myLatch;
  private final @NotNull ProcessManagerListener myListener;
  private final @NotNull ProcessManager myManager;

  public ProcessManagerTest() {
    myAvd = Mockito.mock(AvdInfo.class);
    Mockito.when(myAvd.getId()).thenReturn(KEY.toString());

    myConnection = Mockito.mock(AvdManagerConnection.class);
    Mockito.when(myConnection.getAvds(true)).thenReturn(List.of(myAvd));

    myLatch = new CountDownLatch(1);
    myListener = Mockito.mock(ProcessManagerListener.class);

    myManager = new ProcessManager(() -> myConnection, this::newSetKeyToStateMapFutureCallback);
    myManager.addProcessManagerListener(myListener);
  }

  @Test
  public void deviceChanged() throws Exception {
    // Arrange
    IDevice device = Mockito.mock(IDevice.class);
    Mockito.when(device.isEmulator()).thenReturn(true);
    Mockito.when(device.getState()).thenReturn(DeviceState.ONLINE);

    Mockito.when(myConnection.isAvdRunning(myAvd)).thenReturn(true);

    // Act
    myManager.deviceChanged(device, IDevice.CHANGE_STATE);

    // Assert
    CountDownLatchAssert.await(myLatch);

    Mockito.verify(myListener).allStatesChanged(new ProcessManagerEvent(myManager));
    assertEquals(Map.of(KEY, State.LAUNCHED), myManager.getKeyToStateMap());
  }

  @Test
  public void initOnline() throws Exception {
    // Arrange
    Mockito.when(myConnection.isAvdRunning(myAvd)).thenReturn(true);

    // Act
    myManager.init();

    // Assert
    CountDownLatchAssert.await(myLatch);

    Mockito.verify(myListener).allStatesChanged(new ProcessManagerEvent(myManager));
    assertEquals(Map.of(KEY, State.LAUNCHED), myManager.getKeyToStateMap());
  }

  @Test
  public void init() throws Exception {
    // Act
    myManager.init();

    // Assert
    CountDownLatchAssert.await(myLatch);

    Mockito.verify(myListener).allStatesChanged(new ProcessManagerEvent(myManager));
    assertEquals(Map.of(KEY, State.STOPPED), myManager.getKeyToStateMap());
  }

  private @NotNull FutureCallback<@NotNull Map<@NotNull Key, @NotNull State>> newSetKeyToStateMapFutureCallback(@NotNull ProcessManager manager) {
    return new CountDownLatchFutureCallback<>(ProcessManager.newSetKeyToStateMapFutureCallback(manager), myLatch);
  }
}

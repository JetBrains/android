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

import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.DeviceState;
import com.intellij.openapi.application.Application;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

@RunWith(JUnit4.class)
public final class VirtualDeviceChangeListenerTest {
  private final @NotNull VirtualDeviceTableModel myModel = Mockito.mock(VirtualDeviceTableModel.class);
  private final @NotNull IDevice myDevice = Mockito.mock(IDevice.class);

  @Test
  public void deviceChangedDeviceIsntVirtualDevice() {
    // Arrange
    IDeviceChangeListener listener = new VirtualDeviceChangeListener(myModel);

    // Act
    listener.deviceChanged(myDevice, 0);

    // Assert
    Mockito.verify(myModel, Mockito.never()).setAllOnline();
  }

  @Test
  public void deviceChangedChangeStateIsntSet() {
    // Arrange
    IDeviceChangeListener listener = new VirtualDeviceChangeListener(myModel);
    Mockito.when(myDevice.isEmulator()).thenReturn(true);

    // Act
    listener.deviceChanged(myDevice, 0);

    // Assert
    Mockito.verify(myModel, Mockito.never()).setAllOnline();
  }

  @Test
  public void deviceChangedStateIsNull() {
    // Arrange
    IDeviceChangeListener listener = new VirtualDeviceChangeListener(myModel);
    Mockito.when(myDevice.isEmulator()).thenReturn(true);

    // Act
    listener.deviceChanged(myDevice, IDevice.CHANGE_STATE);

    // Assert
    Mockito.verify(myModel, Mockito.never()).setAllOnline();
  }

  @Test
  public void deviceChangedCaseOffline() {
    // Arrange
    Runnable setAllOnline = myModel::setAllOnline;

    Application application = Mockito.mock(Application.class);
    Mockito.doAnswer(VirtualDeviceChangeListenerTest::run).when(application).invokeLater(setAllOnline);

    IDeviceChangeListener listener = new VirtualDeviceChangeListener(() -> application, setAllOnline);

    Mockito.when(myDevice.isEmulator()).thenReturn(true);
    Mockito.when(myDevice.getState()).thenReturn(DeviceState.OFFLINE);

    // Act
    listener.deviceChanged(myDevice, IDevice.CHANGE_STATE);

    // Assert
    Mockito.verify(myModel).setAllOnline();
  }

  @Test
  public void deviceChangedCaseOnline() {
    // Arrange
    Runnable setAllOnline = myModel::setAllOnline;

    Application application = Mockito.mock(Application.class);
    Mockito.doAnswer(VirtualDeviceChangeListenerTest::run).when(application).invokeLater(setAllOnline);

    IDeviceChangeListener listener = new VirtualDeviceChangeListener(() -> application, setAllOnline);

    Mockito.when(myDevice.isEmulator()).thenReturn(true);
    Mockito.when(myDevice.getState()).thenReturn(DeviceState.ONLINE);

    // Act
    listener.deviceChanged(myDevice, IDevice.CHANGE_STATE);

    // Assert
    Mockito.verify(myModel).setAllOnline();
  }

  private static @Nullable Void run(@NotNull InvocationOnMock invocation) {
    ((Runnable)invocation.getArgument(0)).run();
    return null;
  }
}

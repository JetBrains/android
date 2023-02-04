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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;
import com.android.sdklib.deviceprovisioner.DeviceHandle;
import com.android.sdklib.deviceprovisioner.DeviceProperties;
import com.android.sdklib.deviceprovisioner.DeviceProvisioner;
import com.android.sdklib.deviceprovisioner.DeviceState;
import com.android.tools.idea.devicemanager.CountDownLatchAssert;
import com.android.tools.idea.devicemanager.CountDownLatchFutureCallback;
import com.android.tools.idea.devicemanager.DeviceManagerAndroidDebugBridge;
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import kotlinx.coroutines.flow.StateFlow;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class PhysicalDeviceChangeListenerTest {
  private final @NotNull PhysicalDeviceTableModel myModel = Mockito.mock(PhysicalDeviceTableModel.class);
  private final @NotNull DeviceManagerAndroidDebugBridge myBridge = Mockito.mock(DeviceManagerAndroidDebugBridge.class);
  private final @NotNull IDevice myDevice = Mockito.mock(IDevice.class);
  private final @NotNull BuilderService myService = Mockito.mock(BuilderService.class);
  private final @NotNull Project myProject = Mockito.mock(Project.class);

  @Test
  public void deviceChangedMaskEqualsChangeProfileableClientList() {
    // Arrange
    FutureCallback<PhysicalDevice> callback = PhysicalDeviceChangeListener.newAddOrSet(myModel);
    IDeviceChangeListener listener = new PhysicalDeviceChangeListener(myBridge, () -> myService, callback, myProject);

    // Act
    listener.deviceChanged(myDevice, IDevice.CHANGE_PROFILEABLE_CLIENT_LIST);

    // Assert
    Mockito.verify(myModel, Mockito.never()).addOrSet(ArgumentMatchers.any());
  }

  @Test
  public void deviceChanged() throws InterruptedException {
    // Arrange
    Mockito.when(myService.build(myDevice)).thenReturn(Futures.immediateFuture(TestPhysicalDevices.GOOGLE_PIXEL_3));

    CountDownLatch latch = new CountDownLatch(1);

    FutureCallback<PhysicalDevice> callback = new CountDownLatchFutureCallback<>(PhysicalDeviceChangeListener.newAddOrSet(myModel), latch);
    IDeviceChangeListener listener = new PhysicalDeviceChangeListener(myBridge, () -> myService, callback, myProject);

    // Act
    listener.deviceChanged(myDevice, IDevice.CHANGE_STATE);

    // Assert
    CountDownLatchAssert.await(latch);
    Mockito.verify(myModel).addOrSet(TestPhysicalDevices.GOOGLE_PIXEL_3);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void firebaseDeviceConnected() throws InterruptedException {
    // Arrange
    CountDownLatch latch = new CountDownLatch(1);
    FutureCallback<PhysicalDevice> callback =
      new CountDownLatchFutureCallback<>(PhysicalDeviceChangeListener.newAddOrSet(myModel), latch);
    DeviceProvisionerService deviceProvisionerService = Mockito.mock(DeviceProvisionerService.class);
    DeviceProvisioner deviceProvisioner = Mockito.mock(DeviceProvisioner.class);
    StateFlow<List<DeviceHandle>> stateFlow = (StateFlow<List<DeviceHandle>>)Mockito.mock(StateFlow.class);
    DeviceHandle deviceHandle = Mockito.mock(DeviceHandle.class);
    DeviceState deviceState = Mockito.mock(DeviceState.class);
    DeviceProperties deviceProperties = Mockito.mock(DeviceProperties.class);

    Mockito.when(myProject.getService(DeviceProvisionerService.class)).thenReturn(deviceProvisionerService);
    Mockito.when(deviceProvisionerService.getDeviceProvisioner()).thenReturn(deviceProvisioner);
    Mockito.when(deviceProvisioner.getDevices()).thenReturn(stateFlow);
    Mockito.when(stateFlow.getValue()).thenReturn(List.of(deviceHandle));
    Mockito.when(deviceHandle.getState()).thenReturn(deviceState);
    Mockito.when(deviceState.getProperties()).thenReturn(deviceProperties);
    Mockito.when(deviceProperties.getDisambiguator()).thenReturn("12345");
    Mockito.when(myDevice.getSerialNumber()).thenReturn("localhost:12345");

    IDeviceChangeListener listener = new PhysicalDeviceChangeListener(myBridge, () -> myService, callback, myProject);

    // Act - firebase device
    listener.deviceConnected(myDevice);

    // Assert - firebase device
    Mockito.verify(myModel, never()).addOrSet(any());
    Mockito.verify(myService, never()).build(any());

    // Arrange - real device
    IDevice myRealDevice = Mockito.mock(IDevice.class);
    Mockito.when(myRealDevice.getSerialNumber()).thenReturn("localhost:54321");
    Mockito.when(myService.build(myRealDevice)).thenReturn(Futures.immediateFuture(TestPhysicalDevices.GOOGLE_PIXEL_3));

    // Act - real device
    listener.deviceConnected(myRealDevice);

    // Assert - real device
    latch.await();
    Mockito.verify(myModel).addOrSet(TestPhysicalDevices.GOOGLE_PIXEL_3);

    // Assert Model::addOrSet was called only once for real device.
    Mockito.verify(myModel, times(1)).addOrSet(any());
  }
}

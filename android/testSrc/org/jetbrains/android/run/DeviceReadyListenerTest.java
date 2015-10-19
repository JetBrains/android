/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.run;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformLiteFixture;
import org.jetbrains.android.run.DeviceReadyListener.Callback;
import org.jetbrains.annotations.NotNull;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link org.jetbrains.android.run.DeviceReadyListener}.
 */
public class DeviceReadyListenerTest extends PlatformLiteFixture {
  private static IDevice createDeviceMock(@NotNull String serial, boolean isOnline) {
    IDevice device = mock(IDevice.class);
    when(device.getSerialNumber()).thenReturn(serial);
    when(device.isOnline()).thenReturn(isOnline);
    return device;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // We need to set up a test application to support the merged update queue.
    initApplication();
  }

  public void testDeviceChangedReadyAndMatchingFiresCallback() {
    SimpleLogger logger = mock(SimpleLogger.class);
    Predicate<IDevice> devicePredicate = Predicates.alwaysTrue();
    Callback callback = mock(Callback.class);
    DeviceReadyListener listener = createListener(logger, devicePredicate, callback);

    IDevice device = createDeviceMock("device1", true /* isOnline */);
    // When there are more than 5 clients, the device listener believes the device is ready.
    when(device.getClients()).thenReturn(new Client[5]);
    listener.deviceChanged(device, 0);
    verify(callback).onDeviceReady(device);
  }

  @NotNull
  protected DeviceReadyListener createListener(SimpleLogger logger,
                                               Predicate<IDevice> devicePredicate,
                                               Callback callback) {
    DeviceReadyListener listener = new DeviceReadyListener(logger, devicePredicate, callback);
    Disposer.register(getTestRootDisposable(), listener);
    return listener;
  }

  public void testDeviceChangedFiresCallbackOnlyOnce() {
    SimpleLogger logger = mock(SimpleLogger.class);
    Predicate<IDevice> devicePredicate = Predicates.alwaysTrue();
    Callback callback = mock(Callback.class);
    DeviceReadyListener listener = createListener(logger, devicePredicate, callback);

    IDevice device = createDeviceMock("device1", true /* isOnline */);
    // When there are more than 5 clients, the device listener believes the device is ready.
    when(device.getClients()).thenReturn(new Client[5]);
    listener.deviceChanged(device, 0);
    listener.deviceChanged(device, 0);
    verify(callback).onDeviceReady(device);
  }

  public void testDeviceChangedNotMatchingPredicateDoesNothing() {
    SimpleLogger logger = mock(SimpleLogger.class);
    Predicate<IDevice> devicePredicate = Predicates.alwaysFalse();
    Callback callback = mock(Callback.class);
    DeviceReadyListener listener = createListener(logger, devicePredicate, callback);

    IDevice device = createDeviceMock("device1", true /* isOnline */);
    // When there are more than 5 clients, the device listener believes the device is ready.
    when(device.getClients()).thenReturn(new Client[5]);
    listener.deviceChanged(device, 0);
    verifyZeroInteractions(callback);
  }

  public void testDeviceChangedNotOnlineDoesNothing() {
    SimpleLogger logger = mock(SimpleLogger.class);
    Predicate<IDevice> devicePredicate = Predicates.alwaysTrue();
    Callback callback = mock(Callback.class);
    DeviceReadyListener listener = createListener(logger, devicePredicate, callback);

    IDevice device = createDeviceMock("device1", false /* isOnline */);
    // When there are more than 5 clients, the device listener believes the device is ready.
    when(device.getClients()).thenReturn(new Client[5]);
    listener.deviceChanged(device, 0);
    verifyZeroInteractions(callback);
  }

  public void testDeviceChangedNotReadyDoesNothing() {
    SimpleLogger logger = mock(SimpleLogger.class);
    Predicate<IDevice> devicePredicate = Predicates.alwaysTrue();
    Callback callback = mock(Callback.class);
    DeviceReadyListener listener = createListener(logger, devicePredicate, callback);

    IDevice device = createDeviceMock("device1", true /* isOnline */);
    // When there are fewer than 5 clients, and the listener can't find certain clients, device is not ready.
    when(device.getClients()).thenReturn(new Client[0]);
    listener.deviceChanged(device, 0);
    verifyZeroInteractions(callback);
  }

  public void testDeviceDisconnectedDoesNothing() {
    SimpleLogger logger = mock(SimpleLogger.class);
    Predicate<IDevice> devicePredicate = Predicates.alwaysTrue();
    Callback callback = mock(Callback.class);
    DeviceReadyListener listener = createListener(logger, devicePredicate, callback);

    IDevice device = createDeviceMock("device1", true /* isOnline */);

    // Disconnecting just prints output, doesn't surface anything.
    listener.deviceDisconnected(device);

    verifyZeroInteractions(callback);
  }

  public void testDeviceConnectedRepeatedlyChecksIfReady() {
    SimpleLogger logger = mock(SimpleLogger.class);
    Predicate<IDevice> devicePredicate = Predicates.alwaysTrue();
    Callback callback = mock(Callback.class);
    DeviceReadyListener listener = createListener(logger, devicePredicate, callback);

    IDevice device = createDeviceMock("device1", true /* isOnline */);

    // After device connection, the listener will periodically check whether the device is ready.
    // Watch out! In unit test mode the periodic-queueing mechanism reduces to recursion,
    // so if the device doesn't eventually become ready in tests you will get a stack overflow error.
    when(device.getClients())
      .thenReturn(new Client[1])
      .thenReturn(new Client[2])
      .thenReturn(new Client[3])
      .thenReturn(new Client[4])
      .thenReturn(new Client[5]); // On the 5th call, ready.

    listener.deviceConnected(device);

    verify(device, times(5)).getClients();
    verify(callback).onDeviceReady(device);
  }
}

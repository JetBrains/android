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
package com.android.tools.idea.run;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.google.common.base.Predicate;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

/**
 * A device change listener which calls a callback when a matching device is ready for use
 * (is online and ready to install APKs).
 */
public final class DeviceReadyListener implements AndroidDebugBridge.IDeviceChangeListener, Disposable {

  interface Callback {
    void onDeviceReady(@NotNull IDevice device);
  }

  private final MergingUpdateQueue myQueue =
    new MergingUpdateQueue("ANDROID_DEVICE_STATE_UPDATE_QUEUE", 1000, true, null, this, null, false);

  @NotNull private final ConsolePrinter myPrinter;
  @NotNull private final Predicate<IDevice> myDeviceFilter;
  @NotNull private final Callback myCallback;
  @NotNull private final Object myFinishedLock = new Object();
  @GuardedBy("finishedLock") private boolean myFinished;

  public DeviceReadyListener(
    @NotNull ConsolePrinter printer,
    @NotNull Predicate<IDevice> deviceFilter,
    @NotNull Callback callback
  ) {
    myPrinter = printer;
    myDeviceFilter = deviceFilter;
    myCallback = callback;
  }

  @Override
  public void dispose() {
    // Do nothing; we are only Disposable so that we can register as the parent of the MergingUpdateQueue,
    // so clients can dispose us and thus shut down the queue (which otherwise would continue with periodic MyDeviceStateUpdates).
  }

  @Override
  public void deviceConnected(@NotNull final IDevice device) {
    // avd may be null if usb device is used, or if it didn't set by ddmlib yet
    if (device.getAvdName() == null || myDeviceFilter.apply(device)) {
      myPrinter.stdout("Device connected: " + device.getSerialNumber());

      // we need this, because deviceChanged is not triggered if avd is set to the emulator
      myQueue.queue(new MyDeviceStateUpdate(device));
    }
  }

  @Override
  public void deviceDisconnected(@NotNull IDevice device) {
    if (myDeviceFilter.apply(device)) {
      myPrinter.stdout("Device disconnected: " + device.getSerialNumber());
    }
  }

  @Override
  public void deviceChanged(@NotNull final IDevice device, int changeMask) {
    myQueue.queue(new Update(device.getSerialNumber()) {
      @Override
      public void run() {
        onDeviceChanged(device);
      }
    });
  }

  private void onDeviceChanged(@NotNull IDevice device) {
    synchronized (myFinishedLock) {
      if (myFinished || !myDeviceFilter.apply(device) || !device.isOnline()) {
        return;
      }

      if (!isReady(device)) {
        return;
      }

      myPrinter.stdout("Device is ready: " + device.getName());
      myFinished = true;
    }
    // The client is generally responsible for disposing us when they remove us as a listener from the debug bridge,
    // but we know we no longer want the queue to be active anymore at this point.
    // This also helps with testing, because otherwise our periodic MyDeviceStatusUpdates lead to stack overflow.
    Disposer.dispose(myQueue);
    myCallback.onDeviceReady(device);
  }

  private static boolean isReady(@NotNull IDevice device) {
    if (!device.isOnline()) {
      return false;
    }
    // Devices (esp. emulators) may be reported as online, but may not have services running yet. Attempting to
    // install at this time would result in an error like "Could not access the Package Manager".
    // We use the following heuristic to check that the system is in a reasonable state to install apps.
    return !(device.getClients().length < 5 &&
             device.getClient("android.process.acore") == null &&
             device.getClient("com.google.android.wearable.app") == null);
  }

  /** An update which checks the device and registers another update, leading to periodic checks. */
  private class MyDeviceStateUpdate extends Update {
    @NotNull private final IDevice myDevice;

    public MyDeviceStateUpdate(@NotNull IDevice device) {
      super(device.getSerialNumber());
      myDevice = device;
    }

    @Override
    public void run() {
      onDeviceChanged(myDevice);
      myQueue.queue(new MyDeviceStateUpdate(myDevice));
    }
  }

  /** Gets a listenable future which resolves to the first device matching the given filter which is ready to use. */
  public static ListenableFuture<IDevice> getReadyDevice(@NotNull Predicate<IDevice> deviceFilter, @NotNull ConsolePrinter logger) {
    final SettableFuture<IDevice> future = SettableFuture.create();
    Callback callback = new Callback() {
      @Override
      public void onDeviceReady(@NotNull IDevice device) {
        future.set(device);
      }
    };
    final DeviceReadyListener deviceReadyListener = new DeviceReadyListener(logger, deviceFilter, callback);

    // Whether resolved or canceled, be sure to shut down.
    Futures.addCallback(future, new FutureCallback<IDevice>() {
      @Override
      public void onSuccess(IDevice result) {
        shutdown(deviceReadyListener);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        // Handle cancellation of the future.
        shutdown(deviceReadyListener);
      }
    });

    AndroidDebugBridge.addDeviceChangeListener(deviceReadyListener);

    return future;
  }

  private static void shutdown(@NotNull DeviceReadyListener listener) {
    Disposer.dispose(listener);
    AndroidDebugBridge.removeDeviceChangeListener(listener);
  }
}

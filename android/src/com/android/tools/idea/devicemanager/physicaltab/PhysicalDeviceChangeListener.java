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

import com.android.annotations.concurrency.UiThread;
import com.android.annotations.concurrency.WorkerThread;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.internal.DeviceMonitor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.EdtExecutorService;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Notifies the physical device table when physical devices are connected, disconnected, and when they become online. The
 * {@link DeviceMonitor#start device list monitor} thread passes IDevices to the IDeviceChangeListener methods of this class. Those methods
 * convert them to PhysicalDevices and notify the table model on the event dispatch thread.
 */
final class PhysicalDeviceChangeListener implements Disposable, IDeviceChangeListener {
  private final @NotNull PhysicalDeviceTableModel myModel;
  private final @NotNull AndroidDebugBridge myBridge;
  private final @NotNull ListeningExecutorService myExecutorService;
  private final @NotNull Supplier<@NotNull BuilderService> myBuilderServiceGetInstance;
  private final @NotNull PhysicalDeviceFutureCallbackSupplier myNewFutureCallback;

  @VisibleForTesting
  static class PhysicalDeviceFutureCallback implements FutureCallback<PhysicalDevice> {
    private final @NotNull Consumer<@NotNull PhysicalDevice> myOnSuccess;

    /**
     * Called by the device list monitor thread
     */
    @WorkerThread
    @VisibleForTesting
    PhysicalDeviceFutureCallback(@NotNull Consumer<@NotNull PhysicalDevice> onSuccess) {
      myOnSuccess = onSuccess;
    }

    @UiThread
    @Override
    public void onSuccess(@Nullable PhysicalDevice device) {
      assert device != null;
      myOnSuccess.accept(device);
    }

    @UiThread
    @Override
    public final void onFailure(@NotNull Throwable throwable) {
      // TODO Handle the exception in a user visible way
      Logger.getInstance(PhysicalDeviceChangeListener.class).warn(throwable);
    }
  }

  @VisibleForTesting
  @FunctionalInterface
  interface PhysicalDeviceFutureCallbackSupplier {
    @NotNull FutureCallback<@NotNull PhysicalDevice> get(@NotNull Consumer<@NotNull PhysicalDevice> onSuccess);
  }

  @UiThread
  PhysicalDeviceChangeListener(@NotNull PhysicalDeviceTableModel model) {
    this(model, new AndroidDebugBridge(), BuilderService::getInstance, PhysicalDeviceFutureCallback::new);
  }

  @UiThread
  @VisibleForTesting
  PhysicalDeviceChangeListener(@NotNull PhysicalDeviceTableModel model,
                               @NotNull AndroidDebugBridge bridge,
                               @NotNull Supplier<@NotNull BuilderService> builderServiceGetInstance,
                               @NotNull PhysicalDeviceFutureCallbackSupplier newFutureCallback) {
    myModel = model;
    myBridge = bridge;
    myExecutorService = MoreExecutors.listeningDecorator(EdtExecutorService.getInstance());
    myBuilderServiceGetInstance = builderServiceGetInstance;
    myNewFutureCallback = newFutureCallback;

    bridge.addDeviceChangeListener(this);
  }

  @UiThread
  @Override
  public void dispose() {
    myBridge.removeDeviceChangeListener(this);
  }

  // TODO Throttle the notifications from the device list monitor thread

  /**
   * Called by the device list monitor thread
   */
  @WorkerThread
  @Override
  public void deviceConnected(@NotNull IDevice device) {
    buildPhysicalDevice(device, myModel::deviceConnected);
  }

  /**
   * Called by the device list monitor thread
   */
  @WorkerThread
  @Override
  public void deviceDisconnected(@NotNull IDevice device) {
    buildPhysicalDevice(device, myModel::deviceDisconnected);
  }

  /**
   * Called by the device list monitor and the device client monitor threads
   */
  @WorkerThread
  @Override
  public void deviceChanged(@NotNull IDevice device, int mask) {
    if ((mask & IDevice.CHANGE_STATE) == 0) {
      return;
    }

    buildPhysicalDevice(device, myModel::deviceChanged);
  }

  /**
   * Called by the device list monitor thread
   */
  @WorkerThread
  private void buildPhysicalDevice(@NotNull IDevice device, @NotNull Consumer<@NotNull PhysicalDevice> onSuccess) {
    Executor executor = EdtExecutorService.getInstance();

    // noinspection UnstableApiUsage
    FluentFuture.from(myExecutorService.submit(myBuilderServiceGetInstance::get))
      .transformAsync(builderService -> Objects.requireNonNull(builderService).build(device), executor)
      .addCallback(myNewFutureCallback.get(onSuccess), executor);
  }
}

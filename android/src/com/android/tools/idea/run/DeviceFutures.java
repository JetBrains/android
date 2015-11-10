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

import com.android.ddmlib.IDevice;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.ExecutionException;

/** A collection of devices (some of them may still be starting up) for use in a device-oriented run configuration launch. */
public final class DeviceFutures {
  @NotNull
  private final Collection<ListenableFuture<IDevice>> myDeviceFutures;

  private DeviceFutures(@NotNull Collection<ListenableFuture<IDevice>> deviceFutures) {
    myDeviceFutures = deviceFutures;
  }

  @NotNull
  public static DeviceFutures forFuture(@NotNull ListenableFuture<IDevice> deviceFuture) {
    return forFutures(ImmutableList.of(deviceFuture));
  }

  @NotNull
  public static DeviceFutures forFutures(@NotNull Collection<ListenableFuture<IDevice>> deviceFutures) {
    return new DeviceFutures(deviceFutures);
  }

  @NotNull
  public static DeviceFutures forDevices(Iterable<IDevice> devices) {
    ImmutableList.Builder<ListenableFuture<IDevice>> futures = ImmutableList.builder();
    for (IDevice device : devices) {
      futures.add(Futures.immediateFuture(device));
    }
    return new DeviceFutures(futures.build());
  }

  /** @return the device futures, which resolve when each device is ready. */
  @NotNull
  public Collection<ListenableFuture<IDevice>> get() {
    return myDeviceFutures;
  }

  /** @return the target devices, if all are now ready. Otherwise, null. */
  @Nullable
  public Collection<IDevice> getIfReady() {
    for (ListenableFuture<IDevice> deviceFuture : myDeviceFutures) {
      if (!deviceFuture.isDone() || deviceFuture.isCancelled()) {
        return null;
      }
    }

    try {
      return Futures.get(Futures.allAsList(myDeviceFutures), ExecutionException.class);
    } catch (ExecutionException e) {
      // This can happen if the process behind the future threw an exception.
      return null;
    }
  }
}

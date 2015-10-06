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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link DeviceStateCache} is a simple cache of package and device specific data.
 * Entries corresponding to a device are automatically cleared if the device is disconnected.
 */
public class DeviceStateCache<T> implements AndroidDebugBridge.IDeviceChangeListener, Disposable {
  /** Maps from device serial -> package name -> cached data */
  private final Table<String, String, T> myCache = HashBasedTable.create();

  public DeviceStateCache(@NotNull Disposable parent) {
    Disposer.register(parent, this);
    AndroidDebugBridge.addDeviceChangeListener(this);
  }

  @Override
  public void dispose() {
    AndroidDebugBridge.removeDeviceChangeListener(this);
  }

  @Nullable
  public T get(@NotNull IDevice device, @NotNull String pkgName) {
    return myCache.get(device.getSerialNumber(), pkgName);
  }

  @Nullable
  public T put(@NotNull IDevice device, @NotNull String pkgName, @NotNull T data) {
    return myCache.put(device.getSerialNumber(), pkgName, data);
  }

  @Override
  public void deviceConnected(IDevice device) {
  }

  @Override
  public void deviceDisconnected(IDevice device) {
    myCache.row(device.getSerialNumber()).clear();
  }

  @Override
  public void deviceChanged(IDevice device, int changeMask) {
  }
}

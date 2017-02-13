/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.explorer.fs;

import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Abstraction over ADB devices and their corresponding file system.
 *
 * The service is meant to be called on the EDT thread, where long pending operations return a future,
 * while state changes (e.g. new device discovered, existing device disconnected, etc.) fire events on
 * the registered {@link DeviceFileSystemServiceListener} instances. Events are always fired on the EDT
 * thread.
 */
public interface DeviceFileSystemService extends Disposable {
  void addListener(@NotNull DeviceFileSystemServiceListener listener);

  void removeListener(@NotNull DeviceFileSystemServiceListener listener);

  /**
   * Starts the service, usually after registering one or more {@link DeviceFileSystemServiceListener}.
   */
  @NotNull
  ListenableFuture<Void> start();

  /**
   * Restarts the service, usually as the result of a user action when/if the service has become
   * unreliable.
   */
  @NotNull
  ListenableFuture<Void> restart();

  /**
   * Returns the list of currently known devices.
   */
  @NotNull
  ListenableFuture<List<DeviceFileSystem>> getDevices();
}

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
package com.android.tools.idea.deviceManager.physicaltab;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.IDevice;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

final class PhysicalDeviceChangeListener implements Disposable, IDeviceChangeListener {
  /**
   * Called by the event dispatch thread
   */
  PhysicalDeviceChangeListener() {
    AndroidDebugBridge.addDeviceChangeListener(this);
  }

  /**
   * Called by the event dispatch thread
   */
  @Override
  public void dispose() {
    AndroidDebugBridge.removeDeviceChangeListener(this);
  }

  /**
   * Called by the device list monitor thread
   */
  @Override
  public void deviceConnected(@NotNull IDevice device) {
  }

  /**
   * Called by the device list monitor thread
   */
  @Override
  public void deviceDisconnected(@NotNull IDevice device) {
  }

  /**
   * Called by the device list monitor and the device client monitor threads
   */
  @Override
  public void deviceChanged(@NotNull IDevice device, int mask) {
  }
}

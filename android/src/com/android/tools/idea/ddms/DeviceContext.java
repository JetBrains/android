/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.ddms;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.intellij.openapi.Disposable;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

public class DeviceContext {
  private final EventDispatcher<DeviceSelectionListener> myEventDispatcher =
    EventDispatcher.create(DeviceSelectionListener.class);

  private IDevice mySelectedDevice;
  private Client mySelectedClient;

  public void addListener(DeviceSelectionListener l, @NotNull Disposable parentDisposable) {
    myEventDispatcher.addListener(l, parentDisposable);
  }

  public void fireDeviceSelected(@Nullable IDevice d) {
    mySelectedDevice = d;
    myEventDispatcher.getMulticaster().deviceSelected(d);
  }

  public void fireClientSelected(@Nullable Client c) {
    mySelectedClient = c;
    myEventDispatcher.getMulticaster().clientSelected(c);
  }

  public void fireDeviceChanged(@NotNull IDevice device, int changeMask) {
    myEventDispatcher.getMulticaster().deviceChanged(device, changeMask);
  }

  public IDevice getSelectedDevice() {
    return mySelectedDevice;
  }

  public Client getSelectedClient() {
    return mySelectedClient;
  }

  public interface DeviceSelectionListener extends EventListener {
    /** Callback invoked when the selected device changes in the current device context. */
    void deviceSelected(@Nullable IDevice device);

    /** Callback invoked when some property of a device changes.
     * See {@link com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener#deviceChanged(com.android.ddmlib.IDevice, int)}
     * for details regarding the change mask.
     */
    void deviceChanged(@NotNull IDevice device, int changeMask);

    /** Callback invoked when current selected client changes in the current device context. */
    void clientSelected(@Nullable Client c);
  }
}

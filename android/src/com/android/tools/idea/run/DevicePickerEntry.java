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

import com.android.annotations.Nullable;
import com.android.tools.idea.run.editor.DevicePicker;
import org.jetbrains.annotations.NotNull;

/** Model object for an entry in the {@link DevicePicker}. This is a simple wrapper around either a String, or a {@link AndroidDevice}. */
public class DevicePickerEntry {
  public static final DevicePickerEntry CONNECTED_DEVICES_MARKER = new DevicePickerEntry(null, "Connected Devices");
  public static final DevicePickerEntry LAUNCHABLE_DEVICES_MARKER = new DevicePickerEntry(null, "Available Emulators");
  public static final DevicePickerEntry NONE = new DevicePickerEntry(null, "   <none>");

  private final AndroidDevice myAndroidDevice;
  private final String myMarker;

  private DevicePickerEntry(@Nullable AndroidDevice androidDevice, @Nullable String marker) {
    myAndroidDevice = androidDevice;
    myMarker = marker;
  }

  public boolean isMarker() {
    return myMarker != null;
  }

  @Nullable
  public String getMarker() {
    return myMarker;
  }

  @Nullable
  public AndroidDevice getAndroidDevice() {
    return myAndroidDevice;
  }

  public static DevicePickerEntry create(@NotNull AndroidDevice device) {
    return new DevicePickerEntry(device, null);
  }
}

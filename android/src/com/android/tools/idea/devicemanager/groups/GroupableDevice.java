/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.groups;

import com.android.annotations.Nullable;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.adtui.common.ColoredIconGenerator;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import icons.StudioIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/**
 * A wrapper around AvdInfo or physical device (TODO) that represents a device that can be part of a device group
 */
public final class GroupableDevice {
  private final @Nullable AvdInfo myAvdInfo;
  // TODO: add physical device

  public GroupableDevice(@NotNull AvdInfo info) {
    myAvdInfo = info;
  }

  public @NotNull String getType() {
    return myAvdInfo != null ? "AVD" : "USB"; // TODO: differentiate for Wi-Fi vs USB
  }

  public @NotNull String getName() {
    if (myAvdInfo != null) {
      return myAvdInfo.getDisplayName();
    }
    else {
      return "TODO";
    }
  }

  public @Nullable Icon getIcon() {
    if (myAvdInfo != null) {
      return StudioIcons.Avd.DEVICE_MOBILE_LARGE; // TODO: should there be different icons?
    }
    else {
      return null;
    }
  }

  public @Nullable Icon getHighlightedIcon() {
    if (myAvdInfo != null) {
      return ColoredIconGenerator.generateWhiteIcon(StudioIcons.Avd.DEVICE_MOBILE_LARGE); // TODO: should there be different icons?
    }
    else {
      return null;
    }
  }

  public boolean isOnline() {
    if (myAvdInfo != null) {
      // TODO(b/174518417): call this on background thread
      return AvdManagerConnection.getDefaultAvdManagerConnection().isAvdRunning(myAvdInfo);
    }
    else {
      return false;
    }
  }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.android.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.runners.ExecutionUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

final class VirtualDevice extends Device {
  static final ImmutableCollection<String> DEFAULT_SNAPSHOT_LIST = ImmutableList.of("default_boot");
  private static final Icon ourConnectedIcon = ExecutionUtil.getLiveIndicator(AndroidIcons.Ddms.EmulatorDevice);

  private final boolean myConnected;

  /**
   * Snapshot directory names displayed to the developer.
   */
  private final ImmutableCollection<String> mySnapshots;

  @VisibleForTesting
  VirtualDevice(boolean connected, @NotNull String name) {
    this(connected, name, ImmutableList.of());
  }

  VirtualDevice(boolean connected, @NotNull String name, @NotNull ImmutableCollection<String> snapshots) {
    super(name);

    myConnected = connected;
    mySnapshots = snapshots;
  }

  boolean isConnected() {
    return myConnected;
  }

  @NotNull
  ImmutableCollection<String> getSnapshots() {
    return mySnapshots;
  }

  @NotNull
  @Override
  Icon getIcon() {
    return myConnected ? ourConnectedIcon : AndroidIcons.Ddms.EmulatorDevice;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof VirtualDevice)) {
      return false;
    }

    VirtualDevice device = (VirtualDevice)object;
    return myConnected == device.myConnected && myName.equals(device.myName) && mySnapshots.equals(device.mySnapshots);
  }

  @Override
  public int hashCode() {
    int hashCode = Boolean.hashCode(myConnected);

    hashCode = 31 * hashCode + myName.hashCode();
    hashCode = 31 * hashCode + mySnapshots.hashCode();

    return hashCode;
  }
}

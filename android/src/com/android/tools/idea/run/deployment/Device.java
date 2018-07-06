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

import com.intellij.execution.runners.ExecutionUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

final class Device {
  private static final Icon ourConnectedIcon = ExecutionUtil.getLiveIndicator(AndroidIcons.Ddms.EmulatorDevice);

  private final boolean myConnected;
  private final String myName;

  Device(boolean connected, @NotNull String name) {
    myConnected = connected;
    myName = name;
  }

  @NotNull
  Icon getIcon() {
    return myConnected ? ourConnectedIcon : AndroidIcons.Ddms.EmulatorDevice;
  }

  @NotNull
  String getName() {
    return myName;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof Device)) {
      return false;
    }

    Device device = (Device)object;
    return myConnected == device.myConnected && myName.equals(device.myName);
  }

  @Override
  public int hashCode() {
    return 31 * Boolean.hashCode(myConnected) + myName.hashCode();
  }

  @Override
  public String toString() {
    String format = "myConnected = %s%n" +
                    "myName = %s";

    return String.format(format, myConnected, myName);
  }
}

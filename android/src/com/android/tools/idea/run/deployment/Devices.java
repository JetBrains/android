/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.LaunchCompatibility.State;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.SimpleTextAttributes;
import java.util.Collection;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Devices {
  private Devices() {
  }

  static boolean containsAnotherDeviceWithSameName(@NotNull Collection<Device> devices, @NotNull Device device) {
    Object key = device.getKey();
    Object name = device.getName();

    return devices.stream()
      .filter(d -> !d.getKey().equals(key))
      .map(Device::getName)
      .anyMatch(name::equals);
  }

  static @NotNull Optional<@NotNull String> getBootOption(@NotNull Device device,
                                                          @NotNull Target target,
                                                          @NotNull BooleanSupplier selectDeviceSnapshotComboBoxSnapshotsEnabledGet) {
    if (!selectDeviceSnapshotComboBoxSnapshotsEnabledGet.getAsBoolean()) {
      return Optional.empty();
    }

    if (device.isConnected()) {
      return Optional.empty();
    }

    if (device.getSnapshots().isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(target.getText(device));
  }

  static @NotNull String getText(@NotNull Device device) {
    return getText(device, null);
  }

  static @NotNull String getText(@NotNull Device device, @Nullable Key key) {
    return getText(device, key, null);
  }

  static @NotNull String getErrorFormattedText(@NotNull LaunchCompatibility launchCompatibility) {
    State state = launchCompatibility.getState();
    if (state.equals(State.OK)) {
      return "";
    }
    String greyColor = ColorUtil.toHtmlColor(SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor());
    String title = state.equals(State.ERROR) ?
                   AndroidBundle.message("error.level.title") :
                   AndroidBundle.message("warning.level.title");
    return String
      .format("<html><font size=+1>%s</font><br><font color=%s>%s</font></html>", title, greyColor, launchCompatibility.getReason());
  }

  static @NotNull String getText(@NotNull Device device, @Nullable Key key, @Nullable String bootOption) {
    return getText(device.getName(), key == null ? null : key.toString(), bootOption);
  }

  private static @NotNull String getText(@NotNull String device, @Nullable String key, @Nullable String bootOption) {
    StringBuilder builder = new StringBuilder(device);

    if (key != null) {
      builder
        .append(" [")
        .append(key)
        .append(']');
    }

    if (bootOption != null) {
      builder
        .append(" - ")
        .append(bootOption);
    }

    return builder.toString();
  }
}

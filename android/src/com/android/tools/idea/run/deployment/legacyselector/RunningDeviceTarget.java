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
package com.android.tools.idea.run.deployment.legacyselector;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A physical device or virtual device booted in an unknown way. Such targets show up in the "Running devices" section of the drop down,
 * hence the name. The other Target subclasses are used for devices that are not running (the ones in the "Available devices" section). When
 * we can identify how a running virtual device was actually booted, the other subclasses will be used for those devices too.
 */
final class RunningDeviceTarget extends Target {
  RunningDeviceTarget(@NotNull Key deviceKey) {
    super(deviceKey);
  }

  /**
   * @return throws UnsupportedOperationException. The text indicates the way a device will be booted but running devices are not booted by
   * the drop down.
   */
  @Override
  @NotNull String getText(@NotNull Device device) {
    throw new UnsupportedOperationException();
  }

  @Override
  void boot(@NotNull VirtualDevice device, @NotNull Project project) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int hashCode() {
    return getDeviceKey().hashCode();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    return object instanceof RunningDeviceTarget && getDeviceKey().equals(((Target)object).getDeviceKey());
  }
}

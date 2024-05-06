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

final class QuickBootTarget extends Target {
  QuickBootTarget(@NotNull Key deviceKey) {
    super(deviceKey);
  }

  @Override
  @NotNull String getText(@NotNull Device device) {
    return "Quick Boot";
  }

  @Override
  void boot(@NotNull VirtualDevice device, @NotNull Project project) {
    device.quickBoot(project);
  }

  @Override
  public int hashCode() {
    return getDeviceKey().hashCode();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    return object instanceof QuickBootTarget && getDeviceKey().equals(((Target)object).getDeviceKey());
  }
}

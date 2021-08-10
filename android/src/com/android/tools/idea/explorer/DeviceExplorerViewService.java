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
package com.android.tools.idea.explorer;

import com.android.sdklib.internal.avd.AvdInfo;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Service for the Device File Explorer tool window.
 */
public interface DeviceExplorerViewService {
  static @NotNull DeviceExplorerViewService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, DeviceExplorerViewService.class);
  }

  /**
   * Opens the Device File Explorer tool window and selects the device matching the given avdInfo.
   */
  void openAndShowDevice(@NotNull AvdInfo avdInfo);

  /**
   * Opens the Device File Explorer tool window and selects the device matching the given serialNumber.
   */
  void openAndShowDevice(@NotNull String serialNumber);
}

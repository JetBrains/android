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
package com.android.tools.idea.devicemanager;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.explorer.DeviceExplorerViewService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class DeviceExplorerViewServiceInvokeLater implements DeviceExplorerViewService {
  private final @NotNull Project myProject;

  public DeviceExplorerViewServiceInvokeLater(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void openAndShowDevice(@NotNull AvdInfo device) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!myProject.isDisposed()) {
        DeviceExplorerViewService.getInstance(myProject).openAndShowDevice(device);
      }
    });
  }

  @Override
  public void openAndShowDevice(@NotNull String serialNumber) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (!myProject.isDisposed()) {
        DeviceExplorerViewService.getInstance(myProject).openAndShowDevice(serialNumber);
      }
    });
  }
}

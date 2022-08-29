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
package com.android.tools.idea.devicemanager;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.avdmanager.HardwareAccelerationCheck;
import com.android.tools.idea.sdk.AndroidSdkPathStore;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import icons.StudioIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DeviceManagerAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setVisible(true);

    switch (event.getPlace()) {
      case ActionPlaces.TOOLBAR:
        // Layout editor device menu
        presentation.setIcon(null);
        presentation.setText("Add Device Definition...");

        break;
      case ActionPlaces.UNKNOWN:
        // run target menu
        presentation.setIcon(StudioIcons.Shell.Toolbar.DEVICE_MANAGER);
        presentation.setText("Open Device Manager");

        break;
      default:
        presentation.setIcon(StudioIcons.Shell.Toolbar.DEVICE_MANAGER);
        presentation.setText("Device Manager");

        break;
    }

    presentation.setDescription("Opens the device manager which manages virtual and physical devices");

    if (HardwareAccelerationCheck.isChromeOSAndIsNotHWAccelerated()) {
      presentation.setVisible(false);
      return;
    }

    if (ActionPlaces.MAIN_TOOLBAR.equals(event.getPlace()) && !IdeInfo.getInstance().isAndroidStudio()) {
      @Nullable Project project = event.getProject();
      boolean hasAndroidFacets = project != null && ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID);
      presentation.setVisible(hasAndroidFacets);
    }

    presentation.setEnabled(AndroidSdkPathStore.getInstance().getAndroidSdkPath() != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();

    if (project == null) {
      return;
    }

    ToolWindow deviceManager = ToolWindowManager.getInstance(project).getToolWindow(DeviceManagerToolWindowFactory.ID);

    if (deviceManager != null) {
      deviceManager.show(null);
    }
  }
}

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
package org.jetbrains.android.actions;

import static com.android.tools.idea.avdmanager.HardwareAccelerationCheck.isChromeOSAndIsNotHWAccelerated;
import static com.android.tools.idea.devicemanager.DeviceManagerFactoryKt.DEVICE_MANAGER_ID;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.avdmanager.AvdListDialog;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RunAndroidAvdManagerAction extends DumbAwareAction {
  public static final String ID = "Android.RunAndroidAvdManager";

  @Nullable private AvdListDialog myDialog;

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    switch (event.getPlace()) {
      case ActionPlaces.TOOLBAR:
        // Layout editor device menu
        presentation.setText("Add Device Definition...");
        presentation.setIcon(null);
        break;
      case ActionPlaces.UNKNOWN:
        // run target menu
        presentation.setText(redirectToDeviceManager() ? "Open Device Manager" : "Open AVD Manager");
        break;
      default:
        presentation.setText(redirectToDeviceManager() ? "Device Manager" : "AVD Manager");
        break;
    }

    if (isChromeOSAndIsNotHWAccelerated()) {
      presentation.setVisible(false);
      return;
    }

    presentation.setEnabled(AndroidSdkUtils.isAndroidSdkAvailable());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    openAvdManager(e.getProject());
  }

  public void openAvdManager(@Nullable Project project) {
    if (redirectToDeviceManager()) {
      openDeviceManager(project);
    }
    else {
      openOldAvdManager(project);
    }
  }
  private void openOldAvdManager(@Nullable Project project) {
    if (isChromeOSAndIsNotHWAccelerated()) {
      return;
    }

    if (myDialog == null) {
      myDialog = new AvdListDialog(project);
      myDialog.init();
      myDialog.show();
      // Remove the dialog reference when the dialog is disposed (closed).
      Disposer.register(myDialog, () -> myDialog = null);
    }
    else {
      myDialog.getFrame().toFront();
    }
  }

  private static void openDeviceManager(@Nullable Project project) {
    if (project == null) {
      // TODO(qumeric): investigate if it is possible and let the user know if it is.
      return;
    }
    ToolWindow deviceManager = ToolWindowManager.getInstance(project).getToolWindow(DEVICE_MANAGER_ID);
    if (deviceManager != null) {
      deviceManager.show(null);
    }
  }

  private static boolean redirectToDeviceManager() {
    return StudioFlags.ENABLE_NEW_DEVICE_MANAGER_PANEL.get() && StudioFlags.POINT_AVD_MANAGER_TO_DEVICE_MANAGER.get();
  }

  @Nullable
  public AvdInfo getSelected() {
    return myDialog == null ? null : myDialog.getSelected();
  }
}

/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer;

import static com.android.tools.idea.device.explorer.DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import java.util.Arrays;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * Utility methods providing an entry point to Device Explorer, for use by other modules.
 *
 * Note that DeviceExplorerToolWindowFactory is used by the platform directly; these methods are
 * essentially convenience wrappers around it.
 */
public class DeviceExplorerService {
  private DeviceExplorerService() {}

  /** Shows Device Explorer and selects the given AVD. */
  public static void openAndShowDevice(Project project, @NotNull AvdInfo avdInfo) {
    if (!showToolWindow(project)) {
      return;
    }

    DeviceExplorerController controller = DeviceExplorerController.getProjectController(project);
    assert controller != null;
    assert AndroidDebugBridge.getBridge() != null;

    String avdName = avdInfo.getName();
    Optional<IDevice> optionalIDevice =
        Arrays.stream(AndroidDebugBridge.getBridge().getDevices())
            .filter(device -> avdName.equals(device.getAvdName()))
            .findAny();
    if (!optionalIDevice.isPresent()) {
      controller.reportErrorFindingDevice("Unable to find AVD " + avdName + " by name. Please retry.");
      return;
    }

    controller.selectActiveDevice(optionalIDevice.get().getSerialNumber());
  }

  /** Shows Device Explorer and selects the device with the given serial number. */
  public static void openAndShowDevice(Project project, @NotNull String serialNumber) {
    if (!showToolWindow(project)) {
      return;
    }

    DeviceExplorerController controller = DeviceExplorerController.getProjectController(project);
    assert controller != null;

    controller.selectActiveDevice(serialNumber);
  }

  /** Shows Device Explorer, creating it if necessary. */
  public static boolean showToolWindow(Project project) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(project)
        .getToolWindow(TOOL_WINDOW_ID);

    if (toolWindow != null) {
      toolWindow.show();
      return true;
    }

    return false;
  }
}

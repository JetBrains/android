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
package com.android.tools.idea.device.explorer.files;

import static com.android.tools.idea.AndroidEnvironmentUtils.isAndroidEnvironment;

import com.android.tools.idea.file.explorer.toolwindow.DeviceExplorerController;
import com.android.tools.idea.file.explorer.toolwindow.DeviceExplorerFileManager;
import com.android.tools.idea.file.explorer.toolwindow.adbimpl.AdbDeviceFileSystemRenderer;
import com.android.tools.idea.file.explorer.toolwindow.adbimpl.AdbDeviceFileSystemService;
import com.android.tools.idea.file.explorer.toolwindow.ui.DeviceExplorerViewImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.SystemProperties;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;

/**
 * [ToolWindowFactory] for the Device File Explorer ToolWindow
 */
public class DeviceExplorerToolWindowFactory implements DumbAware, ToolWindowFactory {
  private static final String DEVICE_EXPLORER_ENABLED = "android.device.explorer.enabled";
  public static final String TOOL_WINDOW_ID = "Device File Explorer";

  @Override
  public boolean isApplicable(@NotNull Project project) {
    return SystemProperties.getBooleanProperty(DEVICE_EXPLORER_ENABLED, true) && isAndroidEnvironment(project);
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    toolWindow.setIcon(StudioIcons.Shell.ToolWindows.DEVICE_EXPLORER);
    toolWindow.setAvailable(true);
    toolWindow.setToHideOnEmptyContent(true);
    toolWindow.setTitle(TOOL_WINDOW_ID);

    AdbDeviceFileSystemService adbService = project.getService(AdbDeviceFileSystemService.class);
    DeviceExplorerFileManager fileManager = project.getService(DeviceExplorerFileManager.class);

    DeviceExplorerModel model = new DeviceExplorerModel();

    DeviceExplorerViewImpl view = new DeviceExplorerViewImpl(project, new AdbDeviceFileSystemRenderer(), model);
    DeviceExplorerController controller =
      new DeviceExplorerController(project, model, view, adbService, fileManager, fileManager::openFile);

    controller.setup();

    ContentManager contentManager = toolWindow.getContentManager();
    Content toolWindowContent = contentManager.getFactory().createContent(view.getComponent(), "", true);
    contentManager.addContent(toolWindowContent);
  }
}

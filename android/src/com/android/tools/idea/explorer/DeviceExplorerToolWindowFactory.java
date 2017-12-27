/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemRenderer;
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemService;
import com.android.tools.idea.explorer.fs.DeviceFileSystemRenderer;
import com.android.tools.idea.explorer.ui.DeviceExplorerViewImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import java.util.concurrent.Executor;

public class DeviceExplorerToolWindowFactory implements DumbAware, ToolWindowFactory {
  public static final String TOOL_WINDOW_ID = "Device File Explorer";

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    Executor edtExecutor = EdtExecutor.INSTANCE;
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;

    AdbDeviceFileSystemService service = new AdbDeviceFileSystemService(aVoid -> AndroidSdkUtils.getAdb(project),
                                                                        edtExecutor,
                                                                        taskExecutor);
    DeviceFileSystemRenderer renderer = new AdbDeviceFileSystemRenderer(service);
    DeviceExplorerFileManager fileManager = new DeviceExplorerFileManagerImpl(project, edtExecutor);

    DeviceExplorerModel model = new DeviceExplorerModel();
    DeviceExplorerView view = new DeviceExplorerViewImpl(project, toolWindow, renderer, model);
    DeviceExplorerController controller =
      new DeviceExplorerController(project, model, view, service, fileManager, edtExecutor, taskExecutor);

    controller.setup();
  }
}

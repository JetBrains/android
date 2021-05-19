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

import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemRendererFactory;
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemService;
import com.android.tools.idea.explorer.ui.DeviceExplorerViewImpl;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.concurrency.EdtExecutorService;
import icons.StudioIcons;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

public final class DeviceExplorerToolWindowFactory implements DumbAware, ToolWindowFactory {
  public static final String TOOL_WINDOW_ID = "Device File Explorer";

  @Override
  public boolean isApplicable(@NotNull Project project) {
    return DeviceExplorer.isFeatureEnabled();
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    toolWindow.setIcon(StudioIcons.Shell.ToolWindows.DEVICE_EXPLORER);
    toolWindow.setAvailable(true);
    toolWindow.setToHideOnEmptyContent(true);
    toolWindow.setTitle(TOOL_WINDOW_ID);

    Executor edtExecutor = EdtExecutorService.getInstance();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;

    AdbDeviceFileSystemService adbService = AdbDeviceFileSystemService.getInstance(project);
    DeviceExplorerFileManager fileManager = DeviceExplorerFileManager.getInstance(project);

    DeviceFileSystemRendererFactory deviceFileSystemRendererFactory = new AdbDeviceFileSystemRendererFactory(adbService);

    DeviceExplorerModel model = new DeviceExplorerModel();

    DeviceExplorerViewImpl view = new DeviceExplorerViewImpl(project, deviceFileSystemRendererFactory, model);
    DeviceExplorerController.FileOpener fileOpener = new DeviceExplorerController.FileOpener() {
      @Override
      public void openFile(@NotNull Path localPath) {
        // OpenFileAction.openFile triggers a write action, which needs to be executed from a write-safe context.
        ApplicationManager.getApplication().invokeLater(() -> {
          // We need this assertion because in tests OpenFileAction.openFile doesn't trigger it. But it does in production.
          ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
          OpenFileAction.openFile(localPath.toString(), project);
        }, project.getDisposed());
      }

      @Override
      public void openFile(@NotNull VirtualFile virtualFile) {
        // OpenFileAction.openFile triggers a write action, which needs to be executed from a write-safe context.
        ApplicationManager.getApplication().invokeLater(() -> {
          // We need this assertion because in tests OpenFileAction.openFile doesn't trigger it. But it does in production.
          ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
          OpenFileAction.openFile(virtualFile, project);
        }, project.getDisposed());
      }
    };

    DeviceExplorerController controller =
      new DeviceExplorerController(project, model, view, adbService, fileManager, fileOpener, edtExecutor, taskExecutor);

    controller.setup();

    ContentManager contentManager = toolWindow.getContentManager();
    Content toolWindowContent = contentManager.getFactory().createContent(view.getComponent(), "", true);
    contentManager.addContent(toolWindowContent);
  }
}

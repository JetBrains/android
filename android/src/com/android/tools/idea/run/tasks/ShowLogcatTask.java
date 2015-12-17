/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.ddms.DevicePanel;
import com.android.tools.idea.monitor.AndroidToolWindowFactory;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShowLogcatTask implements LaunchTask {
  @NotNull private final Project myProject;
  @Nullable private final String myApplicationId;

  public ShowLogcatTask(@NotNull Project project, @Nullable String applicationId) {
    myProject = project;
    myApplicationId = applicationId;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Activating Logcat Tool window";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.ASYNC_TASK;
  }

  @Override
  public boolean perform(@NotNull IDevice device, @NotNull LaunchStatus launchStatus, @NotNull ConsolePrinter printer) {
    Client client = myApplicationId == null ? null : device.getClient(myApplicationId);
    showLogcatConsole(myProject, device, client);
    return true;
  }

  private static void showLogcatConsole(@NotNull final Project project, @NotNull final IDevice device, @Nullable final Client client) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        final ToolWindow androidToolWindow = ToolWindowManager.getInstance(project).
          getToolWindow(AndroidToolWindowFactory.TOOL_WINDOW_ID);

        // Activate the tool window, and once activated, make sure the right device is selected
        androidToolWindow.activate(new Runnable() {
          @Override
          public void run() {
            int count = androidToolWindow.getContentManager().getContentCount();
            for (int i = 0; i < count; i++) {
              Content content = androidToolWindow.getContentManager().getContent(i);
              DevicePanel devicePanel = content == null ? null : content.getUserData(AndroidToolWindowFactory.DEVICES_PANEL_KEY);
              if (devicePanel != null) {
                devicePanel.selectDevice(device);
                devicePanel.selectClient(client);
                break;
              }
            }
          }
        }, false);
      }
    });
  }
}

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
package com.android.tools.idea.ddms.actions;

import com.android.ddmlib.Client;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.editors.hierarchyview.HierarchyViewCaptureTask;
import com.android.tools.idea.editors.hierarchyview.WindowPickerDialog;
import com.android.tools.idea.editors.hierarchyview.model.ClientWindow;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class HierarchyViewAction extends AbstractClientAction {
  private static final boolean ENABLED = Boolean.getBoolean("enable.hv") || Boolean.parseBoolean(System.getenv("enable.hv"));

  private final Project myProject;

  public HierarchyViewAction(@NotNull Project project, @NotNull DeviceContext deviceContext) {
    super(deviceContext,
          AndroidBundle.message("android.ddms.actions.hierarchyview"),
          AndroidBundle.message("android.ddms.actions.hierarchyview.description"),
          AndroidIcons.Ddms.HierarchyView);
    myProject = project;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(isEnabled() && ENABLED);
    super.update(e);
  }

  @Override
  protected void performAction(@NotNull final Client client) {
    new GetClientWindowsTask(myProject, client).queue();
  }

  private static final class GetClientWindowsTask extends Task.Backgroundable {
    private final Client myClient;
    private List<ClientWindow> myWindows;

    public GetClientWindowsTask(@Nullable Project project, @NotNull Client client) {
      super(project, "Obtaining Windows");
      myClient = client;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      myWindows = ClientWindow.getAll(myClient, 5, TimeUnit.SECONDS);
    }

    @Override
    public void onSuccess() {
      String title = "Capture View Hierarchy";

      if (myWindows == null) {
        Messages.showErrorDialog("Unable to obtain list of windows used by " + myClient.getClientData().getPackageName(), title);
        return;
      }

      if (myWindows.isEmpty()) {
        Messages.showErrorDialog("No active windows displayed by " + myClient.getClientData().getPackageName(), title);
        return;
      }

      ClientWindow window;
      if (myWindows.size() == 1) {
        window = myWindows.get(0);
      } else { // prompt user if there are more than 1 windows displayed by this application
        WindowPickerDialog pickerDialog = new WindowPickerDialog(myProject, myClient, myWindows);
        if (!pickerDialog.showAndGet()) {
          return;
        }

        window = pickerDialog.getSelectedWindow();
        if (window == null) {
          return;
        }
      }

      HierarchyViewCaptureTask captureTask = new HierarchyViewCaptureTask(myProject, myClient, window);
      captureTask.queue();
    }
  }
}

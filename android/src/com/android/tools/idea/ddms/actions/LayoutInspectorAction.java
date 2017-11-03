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
import com.android.layoutinspector.model.ClientWindow;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.editors.layoutInspector.LayoutInspectorCaptureTask;
import com.android.tools.idea.editors.layoutInspector.WindowPickerDialog;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LayoutInspectorAction extends AbstractClientAction {
  private final Project myProject;

  public LayoutInspectorAction(@NotNull Project project, @NotNull DeviceContext deviceContext) {
    super(deviceContext,
          AndroidBundle.message("android.ddms.actions.layoutinspector.title"),
          AndroidBundle.message("android.ddms.actions.layoutinspector.description"),
          AndroidIcons.Ddms.LayoutInspector);
    myProject = project;
  }

  @Override
  protected void performAction(@NotNull final Client client) {
    new GetClientWindowsTask(myProject, client).queue();
  }

  public static final class GetClientWindowsTask extends Task.Backgroundable {
    private final Client myClient;
    private List<ClientWindow> myWindows;
    private String myError;

    public GetClientWindowsTask(@Nullable Project project, @NotNull Client client) {
      super(project, "Obtaining Windows");
      myClient = client;
      myError = null;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);

      try {
        myWindows = ClientWindow.getAll(myClient, 5, TimeUnit.SECONDS);

        if (myWindows == null) {
          myError = "Unable to obtain list of windows used by " +
                    myClient.getClientData().getPackageName() +
                    "\nLayout Inspector requires device API version to be 18 or greater.";
        }
        else if (myWindows.isEmpty()) {
          myError = "No active windows displayed by " + myClient.getClientData().getPackageName();
        }
      }
      catch (IOException e) {
        myError = "Unable to obtain list of windows used by " + myClient.getClientData().getPackageName() + "\nError: " + e.getMessage();
      }
    }

    @Override
    public void onSuccess() {
      if (myError != null) {
        Messages.showErrorDialog(myError, "Capture View Hierarchy");
        return;
      }

      ClientWindow window;
      if (myWindows.size() == 1) {
        window = myWindows.get(0);
      }
      else { // prompt user if there are more than 1 windows displayed by this application
        WindowPickerDialog pickerDialog = new WindowPickerDialog(myProject, myClient, myWindows);
        if (!pickerDialog.showAndGet()) {
          return;
        }

        window = pickerDialog.getSelectedWindow();
        if (window == null) {
          return;
        }
      }

      LayoutInspectorCaptureTask captureTask = new LayoutInspectorCaptureTask(myProject, myClient, window);
      captureTask.queue();
    }
  }
}

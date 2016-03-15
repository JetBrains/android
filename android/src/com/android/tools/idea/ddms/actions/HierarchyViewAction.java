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
import com.android.tools.idea.editors.hierarchyview.HierarchyViewCaptureType;
import com.android.tools.idea.editors.hierarchyview.WindowPickerDialog;
import com.android.tools.idea.editors.hierarchyview.model.ClientWindow;
import com.android.tools.idea.profiling.capture.CaptureHandle;
import com.android.tools.idea.profiling.capture.CaptureService;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import icons.AndroidIcons;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

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
    WindowPickerDialog pickerDialog = new WindowPickerDialog(myProject, client);
    if (!pickerDialog.showAndGet()) {
      return;
    }
    final ClientWindow window = pickerDialog.getSelectedWindow();
    if (window == null) {
      return;
    }

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        try {
          final CaptureService service = CaptureService.getInstance(myProject);
          String name = service.getSuggestedName(client);
          CaptureHandle handle = service.startCaptureFile(HierarchyViewCaptureType.class, name);

          HierarchyViewCaptureTask captureTask = new HierarchyViewCaptureTask(myProject, window, service, handle);
          ProgressManager.getInstance().run(captureTask);
        } catch (IOException e) {
          Messages.showErrorDialog("Error create hierarchy view file", "Capture Hierarchy View");
        }
      }
    });
  }
}

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
package com.android.tools.idea.editors.hierarchyview;

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.hierarchyview.model.ClientWindow;
import com.android.tools.idea.editors.hierarchyview.model.ViewNode;
import com.android.tools.idea.profiling.capture.Capture;
import com.android.tools.idea.profiling.capture.CaptureHandle;
import com.android.tools.idea.profiling.capture.CaptureService;
import com.google.common.util.concurrent.FutureCallback;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.concurrent.TimeUnit;

public class HierarchyViewCaptureTask extends Task.Backgroundable {

  @NotNull private CaptureService myService;
  @NotNull private CaptureHandle myHandle;
  @NotNull private ClientWindow myWindow;

  public HierarchyViewCaptureTask(
    @NotNull Project project, @NotNull ClientWindow window,
    @NotNull CaptureService service, @NotNull CaptureHandle handle) {
    super(project, "Capturing view hierarchy");

    myWindow = window;
    myService = service;
    myHandle = handle;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      ObjectOutputStream output = new ObjectOutputStream(bytes);

      // Write meta data
      HierarchyViewCaptureOptions options = new HierarchyViewCaptureOptions();
      options.setTitle(myWindow.getDisplayName());
      output.writeUTF(options.toString());

      // Capture view hierarchy
      indicator.setText("Capturing hierarchy");
      byte[] hierarchy = verifyNotNull(myWindow.loadWindowData(20, TimeUnit.SECONDS));
      output.writeInt(hierarchy.length);
      output.write(hierarchy);

      // Parse the root node and get the preview of the root node
      indicator.setText("Capturing preview");
      indicator.setIndeterminate(false);
      indicator.setFraction(0.5);
      ViewNode root = verifyNotNull(ViewNode.parseFlatString(hierarchy));
      byte[] preview = verifyNotNull(myWindow.loadViewImage(root, 10, TimeUnit.SECONDS));
      output.writeInt(preview.length);
      output.write(preview);

      // Write data
      indicator.setText("Writing data");
      indicator.setFraction(1);

      output.flush();
      myService.appendData(myHandle, bytes.toByteArray());
      ApplicationManager.getApplication().invokeLater(new Runnable() {

        @Override
        public void run() {
          myService.finalizeCaptureFileAsynchronous(myHandle, new FutureCallback<Capture>() {
            @Override
            public void onSuccess(Capture result) {
              myService.notifyCaptureReady(result);
            }

            @Override
            public void onFailure(Throwable t) {
              showErrorDialog();
            }
          }, EdtExecutor.INSTANCE);
        }
      });
    } catch (IOException e) {
      indicator.cancel();
      myService.cancelCaptureFile(myHandle);
      showErrorDialog();
    }
  }

  @NotNull
  private static <T> T verifyNotNull(@Nullable T object) throws IOException {
    if (object == null) {
      throw new IOException();
    }
    return object;
  }

  private void showErrorDialog() {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        Messages.showErrorDialog("Error create hierarchy view file", "Capture Hierarchy View");
      }
    });
  }
}

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

import com.android.ddmlib.Client;
import com.android.tools.idea.editors.hierarchyview.model.ClientWindow;
import com.android.tools.idea.editors.hierarchyview.model.ViewNode;
import com.android.tools.idea.profiling.capture.Capture;
import com.android.tools.idea.profiling.capture.CaptureService;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.concurrent.TimeUnit;

public class HierarchyViewCaptureTask extends Task.Backgroundable {
  private static final String TITLE = "Capture View Hierarchy";

  @NotNull private final Client myClient;
  @NotNull private final ClientWindow myWindow;

  private String myError;
  private byte[] myData;

  public HierarchyViewCaptureTask(@NotNull Project project, @NotNull Client client, @NotNull ClientWindow window) {
    super(project, "Capturing View Hierarchy");
    myClient = client;
    myWindow = window;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    HierarchyViewCaptureOptions options = new HierarchyViewCaptureOptions();
    options.setTitle(myWindow.getDisplayName());

    // Capture view hierarchy
    indicator.setText("Capturing View Hierarchy");
    indicator.setIndeterminate(false);
    byte[] hierarchy = myWindow.loadWindowData(20, TimeUnit.SECONDS);
    if (hierarchy == null) {
      myError = "Unexpected error: empty view hierarchy";
      return;
    }

    // Parse the root node
    indicator.setText("Capturing preview");
    indicator.setFraction(0.5);
    ViewNode root = ViewNode.parseFlatString(hierarchy);
    if (root == null) {
      myError = "Unable to parse view hierarchy";
      return;
    }

    //  Get the preview of the root node
    byte[] preview = myWindow.loadViewImage(root, 10, TimeUnit.SECONDS);
    if (preview == null) {
      myError = "Unable to obtain preview image";
      return;
    }

    ByteArrayOutputStream bytes = new ByteArrayOutputStream(4096);
    ObjectOutputStream output = null;

    try {
      output = new ObjectOutputStream(bytes);
      output.writeUTF(options.toString());

      output.writeInt(hierarchy.length);
      output.write(hierarchy);

      output.writeInt(preview.length);
      output.write(preview);
    } catch (IOException e) {
      myError = "Unexpected error while saving hierarchy snapshot: " + e;
      return;
    } finally {
      try {
        if (output != null) {
          output.close();
        }
      }
      catch (IOException e) {
        myError = "Unexpected error while closing hierarchy snapshot: " + e;
      }
    }

    myData = bytes.toByteArray();
  }

  @Override
  public void onSuccess() {
    if (myError != null) {
      Messages.showErrorDialog("Error obtaining view hierarchy: " + StringUtil.notNullize(myError), TITLE);
      return;
    }

    CaptureService service = CaptureService.getInstance(myProject);
    try {
      Capture capture = service.createCapture(HierarchyViewCaptureType.class, myData, service.getSuggestedName(myClient));
      final VirtualFile file = capture.getFile();
      file.refresh(true, false, new Runnable() {
        @Override
        public void run() {
          UIUtil.invokeLaterIfNeeded(new Runnable() {
            @Override
            public void run() {
              OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, file);
              FileEditorManager.getInstance(myProject).openEditor(descriptor, true);
            }
          });
        }
      });
    }
    catch (IOException e) {
      Messages.showErrorDialog("Error creating hierarchy view capture: " + e, TITLE);
    }
  }
}

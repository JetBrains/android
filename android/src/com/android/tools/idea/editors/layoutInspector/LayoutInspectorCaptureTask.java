/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.editors.layoutInspector;

import com.android.ddmlib.Client;
import com.android.layoutinspector.model.ClientWindow;
import com.android.layoutinspector.LayoutInspectorBridge;
import com.android.layoutinspector.LayoutInspectorCaptureOptions;
import com.android.layoutinspector.LayoutInspectorResult;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.profiling.capture.Capture;
import com.android.tools.idea.profiling.capture.CaptureService;
import com.android.tools.idea.stats.AndroidStudioUsageTracker;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.google.wireless.android.sdk.stats.LayoutInspectorEvent;
import com.intellij.openapi.fileEditor.FileEditor;
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

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class LayoutInspectorCaptureTask extends Task.Backgroundable {
  private static final String TITLE = "Capture View Hierarchy";

  @NotNull private final Client myClient;
  @NotNull private final ClientWindow myWindow;

  private String myError;
  private byte[] myData;

  public LayoutInspectorCaptureTask(@NotNull Project project, @NotNull Client client, @NotNull ClientWindow window) {
    super(project, "Capturing View Hierarchy");
    myClient = client;
    myWindow = window;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    LayoutInspectorCaptureOptions options = new LayoutInspectorCaptureOptions();
    options.setTitle(myWindow.getDisplayName());

    // Capture view hierarchy
    indicator.setText("Capturing View Hierarchy");
    indicator.setIndeterminate(false);

    long startTimeMs = System.currentTimeMillis();
    LayoutInspectorResult result = LayoutInspectorBridge.captureView(myWindow, options);
    if (!result.getError().isEmpty()) {
      myError = result.getError();
      return;
    }

    long captureDurationMs = System.currentTimeMillis() - startTimeMs;
    UsageTracker.getInstance()
      .log(AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.LAYOUT_INSPECTOR_EVENT)
             .setDeviceInfo(AndroidStudioUsageTracker.deviceToDeviceInfo(myClient.getDevice()))
             .setLayoutInspectorEvent(LayoutInspectorEvent.newBuilder()
                                        .setType(LayoutInspectorEvent.LayoutInspectorEventType.CAPTURE)
                                        .setDurationInMs(captureDurationMs)));

    myData = result.getData();
  }

  @Override
  public void onSuccess() {
    if (myError != null) {
      Messages.showErrorDialog("Error obtaining view hierarchy: " + StringUtil.notNullize(myError), TITLE);
      return;
    }

    CaptureService service = CaptureService.getInstance(myProject);
    try {
      Capture capture = service.createCapture(LayoutInspectorCaptureType.class, myData, service.getSuggestedName(myClient));
      final VirtualFile file = capture.getFile();
      file.refresh(true, false, () -> UIUtil.invokeLaterIfNeeded(() -> {
        OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, file);
        List<FileEditor> editors = FileEditorManager.getInstance(myProject).openEditor(descriptor, true);

        if (LayoutInspectorContext.isDumpDisplayListEnabled()) {
          Optional<FileEditor> optionalEditor = editors.stream().filter(e -> e instanceof LayoutInspectorEditor).findFirst();
          if (optionalEditor.isPresent()) {
            ((LayoutInspectorEditor)optionalEditor.get()).setSources(myClient, myWindow);
          }
        }
      }));
    }
    catch (IOException e) {
      Messages.showErrorDialog("Error creating hierarchy view capture: " + e, TITLE);
    }
  }
}

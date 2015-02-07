/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.ddms;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.tools.idea.editors.allocations.AllocationCaptureType;
import com.android.tools.idea.profiling.capture.Capture;
import com.android.tools.idea.profiling.capture.CaptureService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class ShowAllocationsHandler implements ClientData.IAllocationTrackingHandler {
  private static final Logger LOG = Logger.getInstance(ShowAllocationsHandler.class);

  private final Project myProject;

  public ShowAllocationsHandler(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void onSuccess(@NotNull final byte[] data, @NotNull Client client) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              CaptureService service = CaptureService.getInstance(myProject);
              service.createCapture(AllocationCaptureType.class, data);
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        });
      }
    });
  }
}
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
package com.android.tools.idea.ddms.hprof;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.tools.idea.editors.hprof.HprofCaptureType;
import com.android.tools.idea.profiling.capture.CaptureService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class SaveHprofHandler implements ClientData.IHprofDumpHandler {
  private final Project myProject;

  public SaveHprofHandler(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void onSuccess(String remoteFilePath, Client client) {
    // TODO: older devices don't stream back the heap data. Instead they save results on the sdcard.
    // We don't support this yet.
    showError(AndroidBundle.message("android.ddms.actions.dump.hprof.error.unsupported", remoteFilePath));
  }

  @Override
  public void onSuccess(final byte[] data, Client client) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              CaptureService service = CaptureService.getInstance(myProject);
              service.createCapture(HprofCaptureType.class, data);
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        });
      }
    });
  }

  @Override
  public void onEndFailure(Client client, final String message) {
    showError(message);
  }

  private void showError(final String message) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        Messages.showErrorDialog(message, AndroidBundle.message("android.ddms.actions.dump.hprof"));
      }
    });
  }
}

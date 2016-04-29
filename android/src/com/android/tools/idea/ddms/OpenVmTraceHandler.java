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
import com.android.tools.idea.editors.vmtrace.VmTraceCaptureType;
import com.android.tools.idea.profiling.capture.Capture;
import com.android.tools.idea.profiling.capture.CaptureHandle;
import com.android.tools.idea.profiling.capture.CaptureService;
import com.google.common.util.concurrent.FutureCallback;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class OpenVmTraceHandler implements ClientData.IMethodProfilingHandler {
  private static final Logger LOG = Logger.getInstance(OpenVmTraceHandler.class);

  private final Project myProject;

  public OpenVmTraceHandler(Project project) {
    myProject = project;
  }

  @Override
  public void onSuccess(String remoteFilePath, Client client) {
    // TODO: Devices older than API 10 don't return profile results via JDWP. Instead they save the results on the sdcard.
    // We don't support this yet.
    showError("Method profiling: Older devices (API level < 10) are not supported yet. Please manually retrieve the file " +
              remoteFilePath +
              " from the device and open the file to view the results.");
  }

  @Override
  public void onSuccess(@NotNull final byte[] data, @NotNull final Client client) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        try {
          final CaptureService service = CaptureService.getInstance(myProject);
          String name = service.getSuggestedName(client);
          CaptureHandle handle = service.startCaptureFile(VmTraceCaptureType.class, name);
          service.appendData(handle, data);
          service.finalizeCaptureFileAsynchronous(handle, new FutureCallback<Capture>() {
            @Override
            public void onSuccess(Capture result) {
              service.notifyCaptureReady(result);
            }

            @Override
            public void onFailure(Throwable t) {
              LOG.error("Method Profiling - error when writing trace: " + t);
            }
          }, EdtExecutor.INSTANCE);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @Override
  public void onStartFailure(Client client, String message) {
    showError(message);
  }

  @Override
  public void onEndFailure(Client client, String message) {
    showError(message);
  }

  private void showError(final String message) {
    LOG.error("Method Profiling: " + message);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        Messages.showErrorDialog(myProject, message, "Method Trace");
      }
    });
  }
}

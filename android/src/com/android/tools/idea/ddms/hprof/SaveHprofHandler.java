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

import com.android.SdkConstants;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.google.common.io.Files;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class SaveHprofHandler implements ClientData.IHprofDumpHandler {
  private final Project myProject;

  public SaveHprofHandler(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void onSuccess(String remoteFilePath, Client client) {
    // TODO: older devices don't stream back the heap dtaa. Instead they save results on the sdcard.
    // We don't support this yet.
    showError(AndroidBundle.message("android.ddms.actions.dump.hprof.error.unsupported", remoteFilePath));
  }

  @Override
  public void onSuccess(final byte[] data, Client client) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        SaveHprofDialog dialog = new SaveHprofDialog(myProject);
        if (!dialog.showAndGet()) {
          return;
        }

        new SaveAndRunHprofConvTask(myProject, dialog.getHprofFile(), dialog.shouldConvertToHprof(), data).queue();
      }
    }, ModalityState.defaultModalityState());
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

  private static class SaveAndRunHprofConvTask extends Task.Backgroundable {
    private final File myDestination;
    private final boolean myRunHprofConv;
    private final byte[] myData;

    private Exception myException;

    public SaveAndRunHprofConvTask(@Nullable Project project, @NotNull File destination, boolean runHprofConv, @NotNull byte[] data) {
      super(project, AndroidBundle.message("android.ddms.actions.dump.hprof"), false);

      myDestination = destination;
      myRunHprofConv = runHprofConv;
      myData = data;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      try {
        saveAndConvert();
      }
      catch (Exception e) {
        myException = e;
      }
    }

    private void saveAndConvert() throws IOException, ExecutionException {
      File androidHprof = myRunHprofConv ? FileUtil.createTempFile("android", SdkConstants.EXT_HPROF) : myDestination;
      Files.write(myData, androidHprof);
      if (myRunHprofConv) {
        // run hprof-conv, transforming androidHprof -> destination
        AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
        if (sdkData == null) {
          throw new ExecutionException("Unable to find path to SDK.");
        }

        String hprofConvPath = new File(sdkData.getLocation(), AndroidCommonUtils.platformToolPath(SdkConstants.FN_HPROF_CONV)).getPath();
        ProcessBuilder pb = new ProcessBuilder(hprofConvPath, androidHprof.getAbsolutePath(), myDestination.getAbsolutePath());
        BaseOSProcessHandler handler;
        handler = new BaseOSProcessHandler(pb.start(), "", null);
        final StringBuilder builder = new StringBuilder();
        handler.addProcessListener(new ProcessAdapter() {
          @Override
          public void onTextAvailable(ProcessEvent event, Key outputType) {
            builder.append(event.getText());
          }
        });
        handler.startNotify();
        handler.waitFor();
        int exitCode = handler.getProcess().exitValue();
        if (exitCode != 0) {
          throw new ExecutionException(builder.toString().trim());
        }

        // remove intermediate file
        //noinspection ResultOfMethodCallIgnored
        androidHprof.delete();
      }
    }

    @Override
    public void onSuccess() {
      if (myException != null) {
        Messages.showErrorDialog("Unexpected error while saving heap dump: " + myException.getMessage(),
                                 AndroidBundle.message("android.ddms.actions.dump.hprof"));
      }
      else {
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myDestination);
        Notifications.Bus.notify(new Notification("Android", AndroidBundle.message("android.ddms.actions.dump.hprof"),
                                                  AndroidBundle.message("android.ddms.actions.dump.hprof.saved", myDestination),
                                                  NotificationType.INFORMATION));
      }
    }
  }
}

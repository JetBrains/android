/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tools.idea.editors.hprof.HprofCaptureType;
import com.android.tools.idea.profiling.capture.Capture;
import com.android.tools.idea.profiling.capture.CaptureTypeService;
import com.android.tools.idea.profiling.view.CapturesToolWindow;
import com.android.tools.idea.stats.UsageTracker;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class RunHprofConvAndSaveAsAction extends DumbAwareAction {
  public RunHprofConvAndSaveAsAction() {
    getTemplatePresentation().setText(getActionName());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setText(getActionName());
    presentation.setVisible(isValidCaptureSelection(CapturesToolWindow.CAPTURE_ARRAY.getData(e.getDataContext())));
  }

  @NotNull
  public static String getActionName() {
    return AndroidBundle.message("android.profiler.hprof.actions.conv.contextmenu");
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (e.getProject() == null) {
          return;
        }
        ConvertHprofDialog dialog = new ConvertHprofDialog(e.getProject());
        if (!dialog.showAndGet()) {
          return;
        }

        Capture[] captures = CapturesToolWindow.CAPTURE_ARRAY.getData(e.getDataContext());
        if (isValidCaptureSelection(captures)) {
          UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_PROFILING, UsageTracker.ACTION_PROFILING_CONVERT_HPROF, null, null);
          new RunHprofConvAndSaveTask(e.getProject(), captures[0].getFile(), dialog.getHprofFile()).queue();
        }
      }
    }, ModalityState.defaultModalityState());
  }

  private static boolean isValidCaptureSelection(@Nullable Capture[] captures) {
    return captures != null &&
           captures.length == 1 &&
           captures[0].getType() == CaptureTypeService.getInstance().getType(HprofCaptureType.class);
  }

  private static class RunHprofConvAndSaveTask extends Task.Backgroundable {
    private final VirtualFile mySource;
    private final File myDestination;

    private Exception myException;

    public RunHprofConvAndSaveTask(@Nullable Project project, @NotNull VirtualFile source, @NotNull File destination) {
      super(project, AndroidBundle.message("android.profiler.hprof.actions.conv"), false);

      mySource = source;
      myDestination = destination;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      try {
        convertAndSave();
      }
      catch (Exception e) {
        myException = e;
      }
    }

    private void convertAndSave() throws IOException, ExecutionException {
      // run hprof-conv, transforming androidHprof -> destination
      AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
      if (sdkData == null) {
        throw new ExecutionException("Unable to find path to SDK.");
      }

      String hprofConvPath = new File(sdkData.getLocation(), AndroidCommonUtils.platformToolPath(SdkConstants.FN_HPROF_CONV)).getPath();
      List<String> commandLine = Arrays.asList(hprofConvPath, VfsUtilCore.virtualToIoFile(mySource).getAbsolutePath(), myDestination.getAbsolutePath());
      ProcessBuilder pb = new ProcessBuilder(commandLine);
      BaseOSProcessHandler handler = new BaseOSProcessHandler(pb.start(), StringUtil.join(commandLine, " "), null);
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
    }

    @Override
    public void onSuccess() {
      if (myException != null) {
        Messages.showErrorDialog("Unexpected error while converting heap dump: " + myException.getMessage(),
                                 AndroidBundle.message("android.profiler.hprof.actions.conv"));
      }
      else {
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myDestination);
        Notifications.Bus.notify(new Notification("Android", AndroidBundle.message("android.profiler.hprof.actions.conv"), AndroidBundle
          .message("android.profiler.hprof.actions.conv.saved", myDestination.getAbsolutePath()), NotificationType.INFORMATION));
      }
    }
  }
}

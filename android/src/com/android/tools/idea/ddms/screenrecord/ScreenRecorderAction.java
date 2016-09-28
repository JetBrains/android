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

package com.android.tools.idea.ddms.screenrecord;

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ScreenRecorderOptions;
import com.intellij.CommonBundle;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileTypes.NativeFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Calendar;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ScreenRecorderAction {
  private static final String TITLE = "Screen Recorder";
  @NonNls private static final String REMOTE_PATH = "/sdcard/ddmsrec.mp4";

  private static VirtualFile ourLastSavedFolder;

  private final Project myProject;
  private final IDevice myDevice;

  public ScreenRecorderAction(@NotNull Project p, @NotNull IDevice device) {
    myProject = p;
    myDevice = device;
  }

  public void performAction() {
    final ScreenRecorderOptionsDialog dialog = new ScreenRecorderOptionsDialog(myProject);
    if (!dialog.showAndGet()) {
      return;
    }

    final ScreenRecorderOptions options = dialog.getOptions();

    final CountDownLatch latch = new CountDownLatch(1);
    final CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          myDevice.startScreenRecorder(REMOTE_PATH, options, receiver);
        }
        catch (Exception e) {
          showError(myProject, "Unexpected error while launching screen recorder", e);
          latch.countDown();
        }
      }
    });

    Task.Modal screenRecorderShellTask = new ScreenRecorderTask(myProject, myDevice, latch, receiver);
    screenRecorderShellTask.setCancelText("Stop Recording");
    screenRecorderShellTask.queue();
  }

  private static class ScreenRecorderTask extends Task.Modal {
    private final IDevice myDevice;
    private final CountDownLatch myCompletionLatch;
    private final CollectingOutputReceiver myReceiver;

    public ScreenRecorderTask(@NotNull Project project,
                              @NotNull IDevice device,
                              @NotNull CountDownLatch completionLatch,
                              @NotNull CollectingOutputReceiver receiver) {
      super(project, TITLE, true);

      myDevice = device;
      myCompletionLatch = completionLatch;
      myReceiver = receiver;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      int elapsedTime = 0; // elapsed time in seconds
      indicator.setIndeterminate(true);
      while (true) {
        try {
          if (myCompletionLatch.await(1, TimeUnit.SECONDS)) {
            break;
          }

          // update elapsed time in seconds
          elapsedTime++;
          indicator.setText(String.format("Recording...%1$d %2$s elapsed", elapsedTime, StringUtil.pluralize("second", elapsedTime)));

          if (indicator.isCanceled()) {
            // explicitly cancel the running task
            myReceiver.cancel();

            indicator.setText("Stopping...");

            // Wait for an additional second to make sure that the command
            // completed and screen recorder finishes writing the output
            myCompletionLatch.await(1, TimeUnit.SECONDS);
            break;
          }
        }
        catch (InterruptedException ignored) {
        }
      }
    }

    @Override
    public void onFinished() {
      pullRecording();
    }

    private void pullRecording() {
      FileSaverDescriptor descriptor = new FileSaverDescriptor("Save As", "", "mp4");
      FileSaverDialog saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, myProject);
      VirtualFile baseDir = ourLastSavedFolder != null ? ourLastSavedFolder : VfsUtil.getUserHomeDir();
      VirtualFileWrapper fileWrapper = saveFileDialog.save(baseDir, getDefaultFileName());
      if (fileWrapper == null) {
        return;
      }

      File f = fileWrapper.getFile();
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourLastSavedFolder = VfsUtil.findFileByIoFile(f.getParentFile(), false);

      new PullRecordingTask(myProject, myDevice, f.getAbsolutePath()).queue();
    }

    private static String getDefaultFileName() {
      Calendar now = Calendar.getInstance();
      return String.format("device-%tF-%tH%tM%tS", now, now, now, now);
    }
  }

  private static class PullRecordingTask extends Task.Modal {
    private final String myLocalPath;
    private final IDevice myDevice;

    public PullRecordingTask(@Nullable Project project, @NotNull IDevice device, @NotNull String localFilePath) {
      super(project, TITLE, false);
      myDevice = device;
      myLocalPath = localFilePath;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      try {
        myDevice.pullFile(REMOTE_PATH, myLocalPath);
      }
      catch (Exception e) {
        showError(myProject, "Unexpected error while copying video recording from device", e);
      }
    }

    // Tries to open the file at myLocalPath
    private void openSavedFile() {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(myLocalPath);
      if (file != null) {
        NativeFileType.openAssociatedApplication(file);
      }
    }

    @Override
    public void onSuccess() {
      assert myProject != null;

      if (ShowFilePathAction.isSupported()) {
        int exitCode = Messages.showYesNoCancelDialog(myProject, "Video Recording saved as " + myLocalPath, TITLE, "Open" /* Yes text */,
                                                      "Show in " + ShowFilePathAction.getFileManagerName() /* No text */,
                                                      CommonBundle.getOkButtonText() /* Cancel text */, Messages.getInformationIcon());

        if (exitCode == Messages.YES) {
          openSavedFile();
        }
        else if (exitCode == Messages.NO) {
          ShowFilePathAction.openFile(new File(myLocalPath));
        }
      }
      else if (Messages.showOkCancelDialog(myProject, "Video Recording saved as " + myLocalPath, TITLE, "Open File" /* Ok text */,
                                           CommonBundle.getOkButtonText() /* cancel text */, Messages.getInformationIcon()) ==
               Messages.OK) {
        openSavedFile();
      }
    }
  }

  private static void showError(@Nullable final Project project, @NotNull final String message, @Nullable final Throwable throwable) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        String msg = message;
        if (throwable != null) {
          msg += throwable.getLocalizedMessage() != null ? ": " + throwable.getLocalizedMessage() : "";
        }

        Messages.showErrorDialog(project, msg, TITLE);
      }
    });
  }
}

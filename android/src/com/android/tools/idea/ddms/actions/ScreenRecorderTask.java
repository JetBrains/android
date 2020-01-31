/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.EmulatorConsole;
import com.android.ddmlib.IDevice;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ScreenRecorderTask extends Task.Modal {
  private static final CharSequence MEDIA_UNSUPPORTED_ERROR = "-1010";

  private final IDevice myDevice;
  private final CountDownLatch myCompletionLatch;
  private final CollectingOutputReceiver myReceiver;
  private final Path mHostTmpFileName;

  public ScreenRecorderTask(@NotNull Project project,
                            @NotNull IDevice device,
                            @NotNull CountDownLatch completionLatch,
                            @NotNull CollectingOutputReceiver receiver,
                            @Nullable Path hostTmpFileName) {
    super(project, ScreenRecorderAction.TITLE, true);

    myDevice = device;
    myCompletionLatch = completionLatch;
    myReceiver = receiver;
    mHostTmpFileName = hostTmpFileName;
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
        indicator.setText(
          String.format(Locale.US, "Recording...%1$d %2$s elapsed", elapsedTime, StringUtil.pluralize("second", elapsedTime)));

        // If using emulator screen recording feature, stop the recording if the emulator dies
        EmulatorConsole console = null;

        if (mHostTmpFileName != null) { // Using emulator screen recording
          // getConsole() will check if the emulator is alive
          console = EmulatorConsole.getConsole(myDevice);
          if (console == null) {
            indicator.cancel();
          }
        }
        // Emulator recording has a max recording time of 3 min, so explicitly stop the recording when
        // the time limit is reached.
        if (indicator.isCanceled() || elapsedTime >= 180) {
          // explicitly cancel the running task
          if (mHostTmpFileName != null) { // Using emulator screen recording
            if (console != null) {
              console.stopScreenRecording();
            }
          }
          else {
            myReceiver.cancel();
          }

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
    if (mHostTmpFileName != null) {
      pullEmulatorRecording();
    }
    else {
      pullRecording();
    }
  }

  private void pullEmulatorRecording() {
    FileSaverDescriptor descriptor = new FileSaverDescriptor("Save As", "", "webm");
    FileSaverDialog saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, myProject);
    VirtualFile baseDir = VfsUtil.getUserHomeDir();
    VirtualFileWrapper fileWrapper = saveFileDialog.save(baseDir, getDefaultFileName(".webm"));
    if (fileWrapper == null) {
      return;
    }

    try {
      assert mHostTmpFileName != null;
      Files.copy(mHostTmpFileName, fileWrapper.getFile().toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
    }
    catch (IOException e) {
      ScreenRecorderAction.showError(myProject, "Unable to copy file to destination", e);
    }
  }

  private void pullRecording() {
    // If the receiver failed to record due to unsupported screen resolution
    if (myReceiver.getOutput().contains(MEDIA_UNSUPPORTED_ERROR)) {
      Messages.showErrorDialog(myReceiver.getOutput(), "Screen Recorder Error");
      return;
    }
    FileSaverDescriptor descriptor = new FileSaverDescriptor("Save As", "", "mp4");
    FileSaverDialog saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, myProject);
    VirtualFile baseDir = VfsUtil.getUserHomeDir();
    VirtualFileWrapper fileWrapper = saveFileDialog.save(baseDir, getDefaultFileName(".mp4"));
    if (fileWrapper == null) {
      return;
    }

    File f = fileWrapper.getFile();

    new PullRecordingTask(myProject, myDevice, f.getAbsolutePath()).queue();
  }

  private static String getDefaultFileName(String extension) {
    Calendar now = Calendar.getInstance();
    String fileName = "device-%tF-%tH%tM%tS";
    // add extension to filename on Mac only see: b/38447816
    return String.format(Locale.US, SystemInfo.isMac ? fileName + extension : fileName, now, now, now, now);
  }
}

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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.android.annotations.concurrency.Slow;
import com.android.annotations.concurrency.UiThread;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.EmulatorConsole;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ScreenRecorderOptions;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ScreenRecorderTask implements Runnable {
  private static final String SAVE_PATH_KEY = "ScreenRecorderTask.SavePath";
  private static final CharSequence MEDIA_UNSUPPORTED_ERROR = "-1010";
  private static final long MAX_RECORDING_TIME_MILLIS = TimeUnit.MINUTES.toMillis(3);

  private final Project myProject;
  private final IDevice myDevice;
  private final Path myHostRecordingFile;
  private final ScreenRecorderOptions myOptions;
  private DialogWrapper myDialogWrapper;

  public ScreenRecorderTask(@NotNull Project project,
                            @NotNull IDevice device,
                            @Nullable Path hostRecordingFile,
                            @NotNull ScreenRecorderOptions options) {
    myProject = project;
    myDevice = device;
    myHostRecordingFile = hostRecordingFile;
    myOptions = options;
  }

  @Slow
  @Override
  public void run() {
    CollectingOutputReceiver receiver = null;

    EmulatorConsole console = null;
    if (myHostRecordingFile != null) { // Using emulator screen recording.
      console = EmulatorConsole.getConsole(myDevice);
      if (console == null) {
        return; // Emulator was terminated.
      }
      try {
        console.startEmulatorScreenRecording(getEmulatorScreenRecorderOptions(myHostRecordingFile, myOptions));
      }
      catch (Exception e) {
        EventQueue.invokeLater(() -> showError("Unexpected error while launching screen recording", e));
        return;
      }
    }
    else {
      receiver = new CollectingOutputReceiver();
      startDeviceRecording(receiver);
    }

    long start = System.currentTimeMillis();

    CountDownLatch stoppingLatch = new CountDownLatch(1);
    ScreenRecorderDialog dialog = new ScreenRecorderDialog("Screen Recorder", stoppingLatch::countDown);

    EventQueue.invokeLater(() -> {
      myDialogWrapper = dialog.createWrapper(myProject);
      myDialogWrapper.show();
    });

    try {
      while (!stoppingLatch.await(millisUntilNextSecondTick(start), TimeUnit.MILLISECONDS) &&
             System.currentTimeMillis() - start < MAX_RECORDING_TIME_MILLIS &&
             (receiver == null || !receiver.isComplete())) {
        EventQueue.invokeLater(() -> dialog.setRecordingTimeMillis(System.currentTimeMillis() - start));

        // If using emulator screen recording feature, stop the recording if the emulator dies.
        if (console != null) { // Using emulator screen recording.
          // Check if the emulator is still alive.
          console = EmulatorConsole.getConsole(myDevice);
          if (console == null) {
            break; // Emulator has been terminated.
          }
        }
      }

      EventQueue.invokeLater(() -> dialog.setRecordingLabelText("Stopping..."));

      stopRecording(receiver, console);
    }
    catch (InterruptedException e) {
      stopRecording(receiver, console);
      throw new ProcessCanceledException();
    }
    finally {
      EventQueue.invokeLater(() -> myDialogWrapper.close(DialogWrapper.CLOSE_EXIT_CODE));
    }

    if (receiver != null) {
      pullRecording(receiver);
    }
    else {
      pullEmulatorRecording();
    }
  }

  private long millisUntilNextSecondTick(long start) {
    return 1000 - (System.currentTimeMillis() - start) % 1000;
  }

  private void startDeviceRecording(@NotNull CollectingOutputReceiver receiver) {
    // The IDevice.startScreenRecorder method is blocking, so execute it asynchronously.
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      // Store the temp media file in the respective AVD folder.
      try {
        myDevice.startScreenRecorder(ScreenRecorderAction.REMOTE_PATH, myOptions, receiver);
      }
      catch (Exception e) {
        receiver.flush();
        EventQueue.invokeLater(() -> showError("Unexpected error while launching screen recording", e));
      }
    });
  }

  private void stopRecording(@Nullable CollectingOutputReceiver receiver, @Nullable EmulatorConsole console) {
    if (receiver != null) {
      receiver.cancel();
      try {
        // Wait for an additional second to make sure that the command
        // completed and screen recorder finishes writing the output.
        receiver.awaitCompletion(1, TimeUnit.SECONDS);
      }
      catch (InterruptedException e) {
        throw new ProcessCanceledException();
      }
    }
    else if (console != null) {
      console.stopScreenRecording();
    }
  }

  private void pullEmulatorRecording() {
    assert myHostRecordingFile != null;
    if (!Files.exists(myHostRecordingFile)) {
      return;
    }

    EventQueue.invokeLater(() -> {
      VirtualFileWrapper fileWrapper = getTargetFile("webm");
      if (fileWrapper == null) {
        return;
      }

      try {
        Files.move(myHostRecordingFile, fileWrapper.getFile().toPath(), REPLACE_EXISTING);
      }
      catch (IOException e) {
        showError("Unable to copy file to destination", e);
      }
    });
  }

  private void pullRecording(@NotNull CollectingOutputReceiver receiver) {
    EventQueue.invokeLater(() -> {
      // If the receiver failed to record due to unsupported screen resolution.
      if (receiver.getOutput().contains(MEDIA_UNSUPPORTED_ERROR)) {
        Messages.showErrorDialog(receiver.getOutput(), "Screen Recorder Error");
        return;
      }
      VirtualFileWrapper fileWrapper = getTargetFile("mp4");
      if (fileWrapper == null) {
        return;
      }

      File file = fileWrapper.getFile();
      new PullRecordingTask(myProject, myDevice, file.getAbsolutePath()).queue();
    });
  }

  private @Nullable VirtualFileWrapper getTargetFile(@NotNull String extension) {
    PropertiesComponent properties = PropertiesComponent.getInstance(myProject);
    FileSaverDescriptor descriptor = new FileSaverDescriptor("Save As", "", extension);
    FileSaverDialog saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, myProject);
    String lastPath = properties.getValue(SAVE_PATH_KEY);
    VirtualFile baseDir = lastPath != null ? LocalFileSystem.getInstance().findFileByPath(lastPath) : VfsUtil.getUserHomeDir();
    VirtualFileWrapper saveFileWrapper = saveFileDialog.save(baseDir, getDefaultFileName(extension));
    if (saveFileWrapper != null) {
      File saveFile = saveFileWrapper.getFile();
      properties.setValue(SAVE_PATH_KEY, saveFile.getPath());
    }
    return saveFileWrapper;
  }

  @UiThread
  private void showError(@NotNull String message, @Nullable Throwable throwable) {
    ScreenRecorderAction.showError(myProject, message, throwable);
  }

  private static @NotNull String getDefaultFileName(@NotNull String extension) {
    Calendar now = Calendar.getInstance();
    String fileName = "device-%tF-%tH%tM%tS";
    // Add extension to filename on Mac only see: b/38447816.
    return String.format(Locale.US, SystemInfo.isMac ? fileName + '.' + extension : fileName, now, now, now, now);
  }

  @VisibleForTesting
  static @NotNull String getEmulatorScreenRecorderOptions(@NotNull Path filePath, @NotNull ScreenRecorderOptions options) {
    StringBuilder sb = new StringBuilder();

    if (options.width > 0 && options.height > 0) {
      sb.append("--size ");
      sb.append(options.width);
      sb.append('x');
      sb.append(options.height);
      sb.append(' ');
    }

    if (options.bitrateMbps > 0) {
      sb.append("--bit-rate ");
      sb.append(options.bitrateMbps * 1000000);
      sb.append(' ');
    }

    if (options.timeLimit > 0) {
      sb.append("--time-limit ");
      long seconds = TimeUnit.SECONDS.convert(options.timeLimit, options.timeLimitUnits);
      if (seconds > 180) {
        seconds = 180;
      }
      sb.append(seconds);
      sb.append(' ');
    }

    sb.append(filePath);

    return sb.toString();
  }
}

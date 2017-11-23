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

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.*;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.utils.FileUtils;
import com.intellij.CommonBundle;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.fileTypes.NativeFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ScreenRecorderAction {
  private static final String TITLE = "Screen Recorder";
  private static final String MEDIA_UNSUPPORTED_ERROR = "-1010";
  @NonNls private static final String REMOTE_PATH = "/sdcard/ddmsrec.mp4";
  private static final String EMU_TMP_FILENAME = "tmp.webm";

  private static VirtualFile ourLastSavedFolder;

  private final Project myProject;
  private final IDevice myDevice;

  private boolean mUseEmuRecording = false;
  private String mHostRecordingFileName = null;

  public ScreenRecorderAction(@NotNull Project p, @NotNull IDevice device, boolean useEmuRecording) {
    myProject = p;
    myDevice = device;
    mUseEmuRecording = useEmuRecording;
  }

  public void performAction() {
    final ScreenRecorderOptionsDialog dialog = new ScreenRecorderOptionsDialog(myProject);
    if (!dialog.showAndGet()) {
      return;
    }

    final ScreenRecorderOptions options = dialog.getOptions();

    final CountDownLatch latch = new CountDownLatch(1);
    final CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);

    if (mUseEmuRecording) {
      // TODO (joshuaduong): Needs to handle two cases:
      // 1) When emulator shuts down, need to stop recording dialog
      // 2) When recording hits time limit of 3 min
      try {
        // Store the temp media file in the respective avd folder
        AndroidSdkHandler handler = AndroidSdks.getInstance().tryToChooseSdkHandler();
        AvdManager avdManager = AvdManager.getInstance(handler, new LogWrapper(Logger.getInstance(ScreenRecorderAction.class)));
        AvdInfo avdInfo = avdManager.getAvd(myDevice.getAvdName(), true);
        mHostRecordingFileName = avdInfo.getDataFolderPath() + File.separator + EMU_TMP_FILENAME;
      }
      catch (Exception e) {
        showError(myProject, "Unexpected error while launching screen recorder", e);
      }
    }

    boolean showTouchEnabled = isShowTouchEnabled(myDevice);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      if (options.showTouches != showTouchEnabled) {
        setShowTouch(myDevice, options.showTouches);
      }
      try {
        if (mHostRecordingFileName != null) { // Use emulator screen recording
          EmulatorConsole console = EmulatorConsole.getConsole(myDevice);
          if (console != null) {
            console.startEmulatorScreenRecording(getEmulatorScreenRecorderOptions(mHostRecordingFileName, options));
          }
        } else {
          // Store the temp media file in the respective avd folder
          myDevice.startScreenRecorder(REMOTE_PATH, options, receiver);
        }
      }
      catch (Exception e) {
        showError(myProject, "Unexpected error while launching screen recorder", e);
        latch.countDown();
      }
      finally {
        if (options.showTouches != showTouchEnabled) {
          setShowTouch(myDevice, showTouchEnabled);
        }
      }
    });

    Task.Modal screenRecorderShellTask = new ScreenRecorderTask(myProject, myDevice, latch, receiver, mHostRecordingFileName);
    screenRecorderShellTask.setCancelText("Stop Recording");
    screenRecorderShellTask.queue();
  }

  private void setShowTouch(@NotNull IDevice device, boolean isEnabled) {
    int value = isEnabled ? 1 : 0;
    try {
      device.executeShellCommand("settings put system show_touches " + value, new NullOutputReceiver());
    }
    catch (AdbCommandRejectedException|ShellCommandUnresponsiveException|IOException|TimeoutException e) {
      Logger.getInstance(ScreenRecorderAction.class).warn("Failed to set show taps to " + isEnabled, e);
    }
  }

  private boolean isShowTouchEnabled(@NotNull IDevice device) {
    CollectingOutputReceiver receiver = new CollectingOutputReceiver();
    try {
      device.executeShellCommand("settings get system show_touches", receiver);
      String output = receiver.getOutput();
      return output.equals("1");
    }
    catch (AdbCommandRejectedException|ShellCommandUnresponsiveException|IOException|TimeoutException e) {
      Logger.getInstance(ScreenRecorderAction.class).warn("Failed to retrieve setting", e);
    }
    return false;
  }

  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
  static String getEmulatorScreenRecorderOptions(
    @NotNull String filePath,
    @NotNull ScreenRecorderOptions options) {
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

  private static class ScreenRecorderTask extends Task.Modal {
    private final IDevice myDevice;
    private final CountDownLatch myCompletionLatch;
    private final CollectingOutputReceiver myReceiver;
    private String mHostTmpFileName = null;

    public ScreenRecorderTask(@NotNull Project project,
                              @NotNull IDevice device,
                              @NotNull CountDownLatch completionLatch,
                              @NotNull CollectingOutputReceiver receiver,
                              @Nullable String hostTmpFileName) {
      super(project, TITLE, true);

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
          indicator.setText(String.format("Recording...%1$d %2$s elapsed", elapsedTime, StringUtil.pluralize("second", elapsedTime)));

          if (indicator.isCanceled()) {
            // explicitly cancel the running task
            if (mHostTmpFileName != null) { // Using emulator screen recording
              EmulatorConsole console = EmulatorConsole.getConsole(myDevice);
              if (console != null) {
                console.stopScreenRecording();
              }
            } else {
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
      } else {
        pullRecording();
      }
    }

    private void pullEmulatorRecording() {
      FileSaverDescriptor descriptor = new FileSaverDescriptor("Save As", "", "webm");
      FileSaverDialog saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, myProject);
      VirtualFile baseDir = ourLastSavedFolder != null ? ourLastSavedFolder : VfsUtil.getUserHomeDir();
      VirtualFileWrapper fileWrapper = saveFileDialog.save(baseDir, getDefaultFileName(".webm"));
      if (fileWrapper == null) {
        return;
      }

      File f = fileWrapper.getFile();
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourLastSavedFolder = VfsUtil.findFileByIoFile(f.getParentFile(), false);

      try {
        FileUtils.copyFile(new File(mHostTmpFileName), f);
      } catch (IOException e) {
        showError(myProject, "Unable to copy file to destination", e);
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
      VirtualFile baseDir = ourLastSavedFolder != null ? ourLastSavedFolder : VfsUtil.getUserHomeDir();
      VirtualFileWrapper fileWrapper = saveFileDialog.save(baseDir, getDefaultFileName(".mp4"));
      if (fileWrapper == null) {
        return;
      }

      File f = fileWrapper.getFile();
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourLastSavedFolder = VfsUtil.findFileByIoFile(f.getParentFile(), false);

      new PullRecordingTask(myProject, myDevice, f.getAbsolutePath()).queue();
    }

    private static String getDefaultFileName(String extension) {
      Calendar now = Calendar.getInstance();
      String fileName = "device-%tF-%tH%tM%tS";
      // add extension to filename on Mac only see: b/38447816
      return String.format(SystemInfo.isMac ? fileName + extension : fileName, now, now, now, now);
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
        myDevice.removeRemotePackage(REMOTE_PATH);
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
    ApplicationManager.getApplication().invokeLater(() -> {
      String msg = message;
      if (throwable != null) {
        msg += throwable.getLocalizedMessage() != null ? ": " + throwable.getLocalizedMessage() : "";
      }

      Messages.showErrorDialog(project, msg, TITLE);
    });
  }
}

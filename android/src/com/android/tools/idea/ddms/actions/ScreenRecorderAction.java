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
package com.android.tools.idea.ddms.actions;

import com.google.common.annotations.VisibleForTesting;
import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.EmulatorConsole;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.ScreenRecorderOptions;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.screenrecord.ScreenRecorderOptionsDialog;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.sdk.AndroidSdks;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import icons.StudioIcons;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ScreenRecorderAction extends AbstractDeviceAction {
  static final String REMOTE_PATH = "/sdcard/ddmsrec.mp4";
  static final String TITLE = "Screen Recorder";

  private static final String EMU_TMP_FILENAME = "tmp.webm";

  private final Features myFeatures;
  private final Project myProject;

  public ScreenRecorderAction(@NotNull Project project, @NotNull DeviceContext context) {
    this(project, context, new CachedFeatures(project));
  }

  @VisibleForTesting
  ScreenRecorderAction(@NotNull Project project, @NotNull DeviceContext context, @NotNull Features features) {
    super(context, AndroidBundle.message("android.ddms.actions.screenrecord"),
          AndroidBundle.message("android.ddms.actions.screenrecord.description"), StudioIcons.Logcat.VIDEO_CAPTURE);

    myFeatures = features;
    myProject = project;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();

    if (!isEnabled()) {
      presentation.setEnabled(false);
      presentation.setText(AndroidBundle.message("android.ddms.actions.screenrecord"));

      return;
    }

    IDevice device = myDeviceContext.getSelectedDevice();

    if (myFeatures.watch(device)) {
      presentation.setEnabled(false);
      presentation.setText("Screen Record Is Unavailable for Wear OS");

      return;
    }

    presentation.setEnabled(myFeatures.screenRecord(device));
    presentation.setText(AndroidBundle.message("android.ddms.actions.screenrecord"));
  }

  @Override
  protected void performAction(@NotNull IDevice device) {
    final ScreenRecorderOptionsDialog dialog = new ScreenRecorderOptionsDialog(myProject);
    if (!dialog.showAndGet()) {
      return;
    }

    // Get the show_touches option in another thread, backed by a ProgressIndicator, before start recording. We do that because running the
    // "settings put system show_touches" shell command on the device might take a while to run, freezing the UI for a huge amount of time.
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Starting Screen Recording...") {
      private boolean myShowTouchEnabled;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        myShowTouchEnabled = isShowTouchEnabled(device);
      }

      @Override
      public void onSuccess() {
        startRecordingAsync(dialog, device, myShowTouchEnabled);
      }
    });
  }

  private void startRecordingAsync(@NotNull ScreenRecorderOptionsDialog dialog, @NotNull IDevice device, boolean showTouchEnabled) {
    final ScreenRecorderOptions options = dialog.getOptions();

    final CountDownLatch latch = new CountDownLatch(1);
    final CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);

    AvdManager manager = getVirtualDeviceManager();
    final Path hostRecordingFileName = manager == null ? null : getTemporaryVideoPathForVirtualDevice(device, manager);

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      if (options.showTouches != showTouchEnabled) {
        setShowTouch(device, options.showTouches);
      }
      try {
        if (hostRecordingFileName != null) { // Use emulator screen recording
          EmulatorConsole console = EmulatorConsole.getConsole(device);
          if (console != null) {
            console.startEmulatorScreenRecording(getEmulatorScreenRecorderOptions(hostRecordingFileName, options));
          }
        }
        else {
          // Store the temp media file in the respective avd folder
          device.startScreenRecorder(REMOTE_PATH, options, receiver);
        }
      }
      catch (Exception e) {
        showError(myProject, "Unexpected error while launching screen recorder", e);
        latch.countDown();
      }
      finally {
        if (options.showTouches != showTouchEnabled) {
          setShowTouch(device, showTouchEnabled);
        }
      }
    });

    Task.Modal screenRecorderShellTask = new ScreenRecorderTask(myProject, device, latch, receiver, hostRecordingFileName);
    screenRecorderShellTask.setCancelText("Stop Recording");
    screenRecorderShellTask.queue();
  }

  @Nullable
  private static AvdManager getVirtualDeviceManager() {
    Logger logger = Logger.getInstance(ScreenRecorderAction.class);

    try {
      return AvdManager.getInstance(AndroidSdks.getInstance().tryToChooseSdkHandler(), new LogWrapper(logger));
    }
    catch (AndroidLocationException exception) {
      logger.warn(exception);
      return null;
    }
  }

  @Nullable
  @VisibleForTesting
  Path getTemporaryVideoPathForVirtualDevice(@NotNull IDevice device, @NotNull AvdManager manager) {
    if (!myFeatures.screenRecord(device)) {
      return null;
    }

    AvdInfo virtualDevice = manager.getAvd(device.getAvdName(), true);

    if (virtualDevice == null) {
      return null;
    }

    return Paths.get(virtualDevice.getDataFolderPath(), EMU_TMP_FILENAME);
  }

  private static void setShowTouch(@NotNull IDevice device, boolean isEnabled) {
    int value = isEnabled ? 1 : 0;
    try {
      device.executeShellCommand("settings put system show_touches " + value, new NullOutputReceiver());
    }
    catch (AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException | TimeoutException e) {
      Logger.getInstance(ScreenRecorderAction.class).warn("Failed to set show taps to " + isEnabled, e);
    }
  }

  private static boolean isShowTouchEnabled(@NotNull IDevice device) {
    CollectingOutputReceiver receiver = new CollectingOutputReceiver();
    try {
      device.executeShellCommand("settings get system show_touches", receiver);
      String output = receiver.getOutput();
      return output.equals("1");
    }
    catch (AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException | TimeoutException e) {
      Logger.getInstance(ScreenRecorderAction.class).warn("Failed to retrieve setting", e);
    }
    return false;
  }

  @VisibleForTesting
  static String getEmulatorScreenRecorderOptions(@NotNull Path filePath, @NotNull ScreenRecorderOptions options) {
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

  static void showError(@Nullable final Project project, @NotNull final String message, @Nullable final Throwable throwable) {
    ApplicationManager.getApplication().invokeLater(() -> {
      String msg = message;
      if (throwable != null) {
        msg += throwable.getLocalizedMessage() != null ? ": " + throwable.getLocalizedMessage() : "";
      }

      Messages.showErrorDialog(project, msg, TITLE);
    });
  }
}

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

import static java.util.concurrent.TimeUnit.SECONDS;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.NullOutputReceiver;
import com.android.ddmlib.ScreenRecorderOptions;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.prefs.AndroidLocationsException;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.screenrecord.ScreenRecorderOptionsDialog;
import com.android.tools.idea.ddms.screenrecord.ScreenRecorderPersistentOptions;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeAvdManagers;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import icons.StudioIcons;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ScreenRecorderAction extends AbstractDeviceAction implements DumbAware {
  private static final String REMOTE_PATH = "/sdcard/screen-recording-%d.mp4";
  static final String TITLE = "Screen Recorder";
  private static final Pattern WM_SIZE_OUTPUT_REGEX = Pattern.compile("(?<width>\\d+)x(?<height>\\d+)");

  private static final String EMU_TMP_FILENAME = "tmp.webm";
  /**
   * Devices that are currently recording.
   */
  private static final Set<IDevice> myRecordingInProgress = new HashSet<>();

  private final Features myFeatures;
  private final Project myProject;

  public ScreenRecorderAction(@NotNull Project project, @NotNull DeviceContext context) {
    this(project, context, new CachedFeatures(project));
  }

  @VisibleForTesting
  ScreenRecorderAction(@NotNull Project project, @NotNull DeviceContext context, @NotNull Features features) {
    super(context, AndroidBundle.message("android.ddms.actions.screenrecord"),
          AndroidBundle.message("android.ddms.actions.screenrecord.description"), StudioIcons.Common.VIDEO_CAPTURE);

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

    presentation.setEnabled(myFeatures.screenRecord(device));
    presentation.setText(AndroidBundle.message("android.ddms.actions.screenrecord"));
  }

  protected boolean isEnabled() {
    return super.isEnabled() && !myRecordingInProgress.contains(myDeviceContext.getSelectedDevice());
  }

  @Override
  protected void performAction(@NotNull AnActionEvent e, @NotNull IDevice device) {
    final ScreenRecorderOptionsDialog dialog = new ScreenRecorderOptionsDialog(myProject, device.isEmulator());
    if (!dialog.showAndGet()) {
      return;
    }

    // Get the show_touches option in another thread, backed by a ProgressIndicator, before start recording. We do that because running the
    // "settings put system show_touches" shell command on the device might take a while to run, freezing the UI for a huge amount of time.
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Starting screen recording...") {
      private boolean myShowTouchEnabled;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        myShowTouchEnabled = isShowTouchEnabled(device);
      }

      @Override
      public void onSuccess() {
        startRecordingAsync(dialog.getUseEmulatorRecording(), device, myShowTouchEnabled);
      }
    });
  }

  private void startRecordingAsync(boolean useEmulatorRecording, @NotNull IDevice device, boolean showTouchEnabled) {
    AvdManager manager = getVirtualDeviceManager();
    Path emulatorRecordingFile =
      (manager != null && useEmulatorRecording) ? getTemporaryVideoPathForVirtualDevice(device, manager) : null;
    myRecordingInProgress.add(device);

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Dimension size = getDeviceScreenSize(device);
      ScreenRecorderOptions options = ScreenRecorderPersistentOptions.getInstance().toScreenRecorderOptions(size);
      if (options.showTouches != showTouchEnabled) {
        setShowTouch(device, options.showTouches);
      }
      try {
        ScreenRecorderTask task =
          new ScreenRecorderTask(
            myProject, device, String.format(Locale.US, REMOTE_PATH, System.currentTimeMillis()), emulatorRecordingFile, options);
        task.run();
      }
      finally {
        if (options.showTouches != showTouchEnabled) {
          setShowTouch(device, showTouchEnabled);
        }
        EventQueue.invokeLater(() -> myRecordingInProgress.remove(device));
      }
    });
  }

  @Nullable
  private static AvdManager getVirtualDeviceManager() {
    Logger logger = Logger.getInstance(ScreenRecorderAction.class);
    try {
      return IdeAvdManagers.INSTANCE.getAvdManager(AndroidSdks.getInstance().tryToChooseSdkHandler());
    }
    catch (AndroidLocationsException exception) {
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

    return virtualDevice.getDataFolderPath().resolve(EMU_TMP_FILENAME);
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

  static void showError(@Nullable final Project project, @NotNull final String message, @Nullable final Throwable throwable) {
    ApplicationManager.getApplication().invokeLater(() -> {
      String msg = message;
      if (throwable != null) {
        msg += throwable.getLocalizedMessage() != null ? ": " + throwable.getLocalizedMessage() : "";
      }

      Messages.showErrorDialog(project, msg, TITLE);
    });
  }

  private @Nullable Dimension getDeviceScreenSize(@NotNull IDevice device) {
    CollectingOutputReceiver receiver = new CollectingOutputReceiver();
    try {
      device.executeShellCommand("wm size", receiver);
      receiver.awaitCompletion(5, SECONDS);
      String output = receiver.getOutput();
      Matcher matcher = WM_SIZE_OUTPUT_REGEX.matcher(output);
      if (!matcher.find()) {
        Logger.getInstance(getClass()).warn("Unexpected output from 'wm size': " + output);
        return null;
      }
      return new Dimension(Integer.parseInt(matcher.group("width")), Integer.parseInt(matcher.group("height")));
    }
    catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException | NumberFormatException e) {
      Logger.getInstance(getClass()).warn("Failed to get device screen size.", e);
      return null;
    }
    catch (InterruptedException e) {
      return null;
    }
  }
}

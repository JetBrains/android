/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import static com.android.SdkConstants.ANDROID_HOME_ENV;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_DISPLAY_NAME;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_DISPLAY_SETTINGS_FILE;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_SKIN_PATH;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_TAG_ID;
import static com.android.sdklib.repository.targets.SystemImage.DEFAULT_TAG;
import static com.android.sdklib.repository.targets.SystemImage.GOOGLE_APIS_TAG;

import com.android.SdkConstants;
import com.android.ddmlib.IDevice;
import com.android.prefs.AndroidLocation;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoPackage;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode;
import com.android.tools.idea.emulator.EmulatorSettings;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.net.HttpConfigurable;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

/**
 * A wrapper class for communicating with {@link AvdManager} and exposing helper functions
 * for dealing with {@link AvdInfo} objects inside Android Studio.
 */
public class AvdManagerConnection {
  private static final Logger IJ_LOG = Logger.getInstance(AvdManagerConnection.class);
  private static final ILogger SDK_LOG = new LogWrapper(IJ_LOG);
  private static final ProgressIndicator REPO_LOG = new StudioLoggerProgressIndicator(AvdManagerConnection.class);
  private static final AvdManagerConnection NULL_CONNECTION = new AvdManagerConnection(null);
  private static final int MNC_API_LEVEL_23 = 23;
  private static final int LMP_MR1_API_LEVEL_22 = 22;

  public static final String AVD_INI_HW_LCD_DENSITY = "hw.lcd.density";
  public static final Revision TOOLS_REVISION_WITH_FIRST_QEMU2 = Revision.parseRevision("25.0.0 rc1");
  public static final Revision TOOLS_REVISION_25_0_2_RC3 = Revision.parseRevision("25.0.2 rc3");
  public static final Revision PLATFORM_TOOLS_REVISION_WITH_FIRST_QEMU2 = Revision.parseRevision("23.1.0");
  protected static Revision EMULATOR_REVISION_SUPPORTS_STUDIO_PARAMS = Revision.parseRevision("26.1.0");

  private static final SystemImageUpdateDependency[] SYSTEM_IMAGE_DEPENCENCY_WITH_FIRST_QEMU2 = {
    new SystemImageUpdateDependency(LMP_MR1_API_LEVEL_22, DEFAULT_TAG, 2),
    new SystemImageUpdateDependency(LMP_MR1_API_LEVEL_22, GOOGLE_APIS_TAG, 2),
    new SystemImageUpdateDependency(MNC_API_LEVEL_23, DEFAULT_TAG, 6),
    new SystemImageUpdateDependency(MNC_API_LEVEL_23, GOOGLE_APIS_TAG, 10),
  };
  private static final SystemImageUpdateDependency[] SYSTEM_IMAGE_DEPENCENCY_WITH_25_0_2_RC3 = {
    new SystemImageUpdateDependency(LMP_MR1_API_LEVEL_22, DEFAULT_TAG, 4),
    new SystemImageUpdateDependency(LMP_MR1_API_LEVEL_22, GOOGLE_APIS_TAG, 4),
    new SystemImageUpdateDependency(MNC_API_LEVEL_23, DEFAULT_TAG, 8),
    new SystemImageUpdateDependency(MNC_API_LEVEL_23, GOOGLE_APIS_TAG, 12),
  };

  private static Map<File, AvdManagerConnection> ourCache = ContainerUtil.createWeakMap();
  private static long ourMemorySize = -1;

  private static Function<AndroidSdkHandler, AvdManagerConnection> ourConnectionFactory = AvdManagerConnection::new;

  @Nullable
  private final AndroidSdkHandler mySdkHandler;

  @NotNull
  private final FileOp myFileOp;

  @NotNull
  private final ListeningExecutorService myEdtListeningExecutorService;

  @Nullable
  private AvdManager myAvdManager;

  @NotNull
  public static AvdManagerConnection getDefaultAvdManagerConnection() {
    AndroidSdkHandler handler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    if (handler.getLocation() == null) {
      return NULL_CONNECTION;
    }
    else {
      return getAvdManagerConnection(handler);
    }
  }

  @NotNull
  public synchronized static AvdManagerConnection getAvdManagerConnection(@NotNull AndroidSdkHandler handler) {
    File sdkPath = handler.getLocation();
    if (!ourCache.containsKey(sdkPath)) {
      ourCache.put(sdkPath, ourConnectionFactory.apply(handler));
    }
    return ourCache.get(sdkPath);
  }

  private AvdManagerConnection(@Nullable AndroidSdkHandler sdkHandler) {
    this(sdkHandler, MoreExecutors.listeningDecorator(EdtExecutorService.getInstance()));
  }

  @VisibleForTesting
  public AvdManagerConnection(@Nullable AndroidSdkHandler sdkHandler, @NotNull ListeningExecutorService edtListeningExecutorService) {
    mySdkHandler = sdkHandler;
    myFileOp = sdkHandler == null ? FileOpUtils.create() : sdkHandler.getFileOp();
    myEdtListeningExecutorService = edtListeningExecutorService;
  }

  /**
   * Sets a factory to be used for creating connections, so subclasses can be injected for testing.
   */
  @VisibleForTesting
  protected synchronized static void setConnectionFactory(Function<AndroidSdkHandler, AvdManagerConnection> factory) {
    ourCache.clear();
    ourConnectionFactory = factory;
  }


  /**
   * Setup our static instances if required. If the instance already exists, then this is a no-op.
   */
  private boolean initIfNecessary() {
    if (myAvdManager == null) {
      if (mySdkHandler == null) {
        IJ_LOG.warn("No Android SDK Found");
        return false;
      }
      try {
        myAvdManager = AvdManager.getInstance(mySdkHandler, new File(AndroidLocation.getAvdFolder()), SDK_LOG);
      }
      catch (AndroidLocation.AndroidLocationException e) {
        IJ_LOG.error("Could not instantiate AVD Manager from SDK", e);
        return false;
      }
      if (myAvdManager == null) {
        return false;
      }
    }
    return true;
  }

  public String getSdCardSizeFromHardwareProperties() {
    assert mySdkHandler != null;
    return AvdWizardUtils.getHardwarePropertyDefaultValue(AvdWizardUtils.SD_CARD_STORAGE_KEY, mySdkHandler);
  }

  public String getInternalStorageSizeFromHardwareProperties() {
    assert mySdkHandler != null;
    return AvdWizardUtils.getHardwarePropertyDefaultValue(AvdWizardUtils.INTERNAL_STORAGE_KEY, mySdkHandler);
  }

  @Nullable
  private File getBinaryLocation(String filename) {
    assert mySdkHandler != null;
    LocalPackage sdkPackage = mySdkHandler.getLocalPackage(SdkConstants.FD_EMULATOR, REPO_LOG);
    if (sdkPackage == null) {
      return null;
    }
    File binaryFile = new File(sdkPackage.getLocation(), filename);
    if (!myFileOp.exists(binaryFile)) {
      return null;
    }
    return binaryFile;
  }

  @Nullable
  public File getEmulatorBinary() {
    return getBinaryLocation(SdkConstants.FN_EMULATOR);
  }

  @Nullable
  public File getEmulatorCheckBinary() {
    return getBinaryLocation(SdkConstants.FN_EMULATOR_CHECK);
  }

  /**
   * Return the SystemImageUpdateDependencies for the current emulator
   * or null if no emulator is installed or if the emulator is not an qemu2 emulator.
   */
  @Nullable
  private SystemImageUpdateDependency[] getSystemImageUpdateDependencies() {
    assert mySdkHandler != null;
    LocalPackage info = mySdkHandler.getSdkManager(REPO_LOG).getPackages().getLocalPackages().get(SdkConstants.FD_EMULATOR);
    if (info == null) {
      return null;
    }
    if (info.getVersion().compareTo(TOOLS_REVISION_25_0_2_RC3) >= 0) {
      return SYSTEM_IMAGE_DEPENCENCY_WITH_25_0_2_RC3;
    }
    if (info.getVersion().compareTo(TOOLS_REVISION_WITH_FIRST_QEMU2) >= 0) {
      return SYSTEM_IMAGE_DEPENCENCY_WITH_FIRST_QEMU2;
    }
    return null;
  }

  private boolean hasQEMU2Installed() {
    return getSystemImageUpdateDependencies() != null;
  }

  private boolean hasPlatformToolsForQEMU2Installed() {
    assert mySdkHandler != null;
    LocalPackage info = mySdkHandler.getSdkManager(REPO_LOG).getPackages().getLocalPackages().get(SdkConstants.FD_PLATFORM_TOOLS);
    if (info == null) {
      return false;
    }
    if (info.getVersion().compareTo(PLATFORM_TOOLS_REVISION_WITH_FIRST_QEMU2) >= 0) {
      return true;
    }
    return false;
  }

  private boolean hasSystemImagesForQEMU2Installed() {
    return getSystemImageUpdates().isEmpty();
  }

  /**
   * The qemu2 emulator has changes in the system images for platform 22 and 23 (Intel CPU architecture only).
   * This method will generate package updates if we detect that we have outdated system images for platform
   * 22 and 23. We also check the addon system images which includes the Google API.
   * @return a list of package paths that need to be updated.
   */
  @NotNull
  public List<String> getSystemImageUpdates() {
    List<String> requested = new ArrayList<>();
    SystemImageUpdateDependency[] dependencies = getSystemImageUpdateDependencies();
    if (dependencies == null) {
      return requested;
    }

    assert mySdkHandler != null;
    for (SystemImage systemImage : mySdkHandler.getSystemImageManager(REPO_LOG).getImages()) {
      for (SystemImageUpdateDependency dependency : dependencies) {
        if (dependency.updateRequired(systemImage)) {
          requested.add(systemImage.getPackage().getPath());
          break;
        }
      }
    }
    return requested;
  }

  /**
   * @param forceRefresh if true the manager will read the AVD list from disk. If false, the cached version in memory
   *                     is returned if available
   * @return a list of AVDs currently present on the system.
   */
  @NotNull
  public List<AvdInfo> getAvds(boolean forceRefresh) {
    if (!initIfNecessary()) {
      return ImmutableList.of();
    }
    if (forceRefresh) {
      try {
        myAvdManager.reloadAvds(SDK_LOG);
      }
      catch (AndroidLocation.AndroidLocationException e) {
        IJ_LOG.error("Could not find Android SDK!", e);
      }
    }
    ArrayList<AvdInfo> avdInfos = Lists.newArrayList(myAvdManager.getAllAvds());
    boolean needsRefresh = false;
    for (AvdInfo info : avdInfos) {
      if (info.getStatus() == AvdInfo.AvdStatus.ERROR_DEVICE_CHANGED) {
        updateDeviceChanged(info);
        needsRefresh = true;
      }
    }
    if (needsRefresh) {
      return getAvds(true);
    } else {
      return avdInfos;
    }
  }

  public boolean deleteAvd(@NotNull String avdName) {
    if (!initIfNecessary()) {
      return false;
    }
    AvdInfo info = myAvdManager.getAvd(avdName, false);
    if (info == null) {
      return false;
    }
    return deleteAvd(info);
  }

  /**
   * Delete the given AVD if it exists.
   */
  public boolean deleteAvd(@NotNull AvdInfo info) {
    if (!initIfNecessary()) {
      return false;
    }
    return myAvdManager.deleteAvd(info, SDK_LOG);
  }

  public boolean isAvdRunning(@NotNull AvdInfo info) {
    return myAvdManager.isAvdRunning(info, SDK_LOG);
  }


  public void stopAvd(@NotNull final AvdInfo info) {
    myAvdManager.stopAvd(info);
  }

  @NotNull
  public ListenableFuture<IDevice> startAvd(@Nullable Project project, @NotNull AvdInfo info) {
    return startAvd(project, info, Collections.emptyList());
  }

  /**
   * Launches the given AVD in the emulator. Returns a future with the device that was launched.
   */
  @NotNull
  public ListenableFuture<IDevice> startAvd(@Nullable Project project, @NotNull AvdInfo info, @NotNull List<String> parameters) {
    return startAvd(project, info, false, parameters);
  }

  @NotNull
  ListenableFuture<IDevice> startAvdWithColdBoot(@Nullable Project project, @NotNull AvdInfo info) {
    return startAvd(project, info, true, Collections.emptyList());
  }

  @NotNull
  private ListenableFuture<IDevice> startAvd(@Nullable Project project,
                                             @NotNull AvdInfo info,
                                             boolean forceColdBoot,
                                             @NotNull List<String> parameters) {
    if (!initIfNecessary()) {
      return Futures.immediateFailedFuture(new RuntimeException("No Android SDK Found"));
    }

    final String skinPath = info.getProperties().get(AVD_INI_SKIN_PATH);
    if (skinPath != null) {
      File skinFile = new File(skinPath);
      File baseSkinFile = new File(skinFile.getName());
      // Ensure the skin files are up-to-date
      AvdWizardUtils.pathToUpdatedSkins(baseSkinFile, null, myFileOp);
    }

    // noinspection ConstantConditions, UnstableApiUsage
    return Futures.transformAsync(
      checkAccelerationAsync(),
      code -> continueToStartAvdIfAccelerationErrorIsNotBlocking(code, project, info, forceColdBoot, parameters),
      MoreExecutors.directExecutor());
  }

  @NotNull
  private ListenableFuture<IDevice> continueToStartAvdIfAccelerationErrorIsNotBlocking(@NotNull AccelerationErrorCode code,
                                                                                       @Nullable Project project,
                                                                                       @NotNull AvdInfo info,
                                                                                       boolean forceColdBoot,
                                                                                       @NotNull List<String> parameters) {
    switch (code) {
      case ALREADY_INSTALLED:
        return continueToStartAvd(project, info, forceColdBoot, parameters);
      case TOOLS_UPDATE_REQUIRED:
      case PLATFORM_TOOLS_UPDATE_ADVISED:
      case SYSTEM_IMAGE_UPDATE_ADVISED:
        // Launch the virtual device with possibly degraded performance even if there are updates
        // noinspection DuplicateBranchesInSwitch
        return continueToStartAvd(project, info, forceColdBoot, parameters);
      case NO_EMULATOR_INSTALLED:
        return handleAccelerationError(project, info, code);
      default:
        Abi abi = Abi.getEnum(info.getAbiType());

        if (abi == null) {
          return continueToStartAvd(project, info, forceColdBoot, parameters);
        }

        if (abi.equals(Abi.X86) || abi.equals(Abi.X86_64)) {
          return handleAccelerationError(project, info, code);
        }

        // Let ARM and MIPS virtual devices launch without hardware acceleration
        return continueToStartAvd(project, info, forceColdBoot, parameters);
    }
  }

  @NotNull
  private ListenableFuture<IDevice> continueToStartAvd(@Nullable Project project,
                                                       @NotNull AvdInfo avd,
                                                       boolean forceColdBoot,
                                                       @NotNull List<String> parameters) {
    File emulatorBinary = getEmulatorBinary();
    if (emulatorBinary == null) {
      IJ_LOG.error("No emulator binary found!");
      return Futures.immediateFailedFuture(new RuntimeException("No emulator binary found"));
    }

    avd = reloadAvd(avd); // Reload the AVD in case it was modified externally.
    String avdName = avd.getName();

    // TODO: The emulator stores pid of the running process inside the .lock file (userdata-qemu.img.lock in Linux and
    // userdata-qemu.img.lock/pid on Windows). We should detect whether those lock files are stale and if so, delete them without showing
    // this error. Either the emulator provides a command to do that, or we learn about its internals (qemu/android/utils/filelock.c) and
    // perform the same action here. If it is not stale, then we should show this error and if possible, bring that window to the front.
    if (myAvdManager.isAvdRunning(avd, SDK_LOG)) {
      myAvdManager.logRunningAvdInfo(avd, SDK_LOG);
      String baseFolder;
      try {
        baseFolder = myAvdManager.getBaseAvdFolder().getAbsolutePath();
      }
      catch (AndroidLocation.AndroidLocationException e) {
        baseFolder = "$HOME";
      }

      String message = String.format("AVD %1$s is already running.\n" +
                                     "If that is not the case, delete the files at\n" +
                                     "   %2$s/%1$s.avd/*.lock\n" +
                                     "and try again.", avdName, baseFolder);

      return Futures.immediateFailedFuture(new RuntimeException(message));
    }

    GeneralCommandLine commandLine = newEmulatorCommand(project, emulatorBinary, avd, forceColdBoot, parameters);
    EmulatorRunner runner = new EmulatorRunner(commandLine, avd);
    addListeners(runner);

    final ProcessHandler processHandler;
    try {
      processHandler = runner.start();
    }
    catch (ExecutionException e) {
      IJ_LOG.error("Error launching emulator", e);
      return Futures.immediateFailedFuture(new RuntimeException(String.format("Error launching emulator %1$s ", avdName), e));
    }

    notifyIfLaunchedStandalone(project, avd);

    // If we're using qemu2, it has its own progress bar, so put ours in the background. Otherwise show it.
    final ProgressWindow p = hasQEMU2Installed()
                             ? new BackgroundableProcessIndicator(project, "Launching Emulator", PerformInBackgroundOption.ALWAYS_BACKGROUND,
                                                                  "", "", false)
                             : new ProgressWindow(false, true, project);
    p.setIndeterminate(false);
    p.setDelayInMillis(0);

    // It takes >= 8 seconds to start the Emulator. Display a small progress indicator otherwise it seems like
    // the action wasn't invoked and users tend to click multiple times on it, ending up with several instances of the emulator
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        p.start();
        p.setText("Starting AVD...");
        for (double d = 0; d < 1; d += 1.0 / 80) {
          p.setFraction(d);
          //noinspection BusyWait
          Thread.sleep(100);
          if (processHandler.isProcessTerminated()) {
            break;
          }
        }
      }
      catch (InterruptedException ignore) {
      }
      finally {
        p.stop();
        p.processFinish();
      }
    });

    // Send notification that the device has been launched.
    MessageBus messageBus = project != null ? project.getMessageBus() : ApplicationManager.getApplication().getMessageBus();
    messageBus.syncPublisher(AvdLaunchListener.TOPIC).avdLaunched(avd, commandLine, project);

    return EmulatorConnectionListener.getDeviceForEmulator(project, avd.getName(), processHandler, 5, TimeUnit.MINUTES);
  }

  @NotNull
  GeneralCommandLine newEmulatorCommand(@Nullable Project project,
                                                @NotNull File emulator,
                                                @NotNull AvdInfo device,
                                                boolean forceColdBoot,
                                                @NotNull List<String> parameters) {
    GeneralCommandLine command = new GeneralCommandLine();

    command.setExePath(emulator.getPath());
    command.setWorkDirectory(emulator.getParentFile());
    addParameters(project, device, forceColdBoot, command);

    CharSequence arguments = System.getenv("studio.emu.params");

    if (arguments != null) {
      // noinspection UnstableApiUsage
      command.addParameters(Splitter.on(',').splitToList(arguments));
    }

    command.addParameters(parameters);
    return command;
  }

  /**
   * Notifies user if the AVD is launched standalone despite being configured to start in the Emulator tool window.
   */
  private static void notifyIfLaunchedStandalone(@Nullable Project project, @NotNull AvdInfo avd) {
    if (project == null || !isEmulatorToolWindowAvailable(project) || shouldBeLaunchedEmbedded(project, avd)) {
      return;
    }

    EmulatorSettings settings = EmulatorSettings.getInstance();
    boolean foldable = isFoldable(avd);
    boolean show =
        foldable ? settings.getShowLaunchedStandaloneNotificationForFoldable() : settings.getShowLaunchedStandaloneNotification();
    if (!show) {
      return; // Notified before.
    }

    String reason = foldable ? "to support folding" : "to be able to use extended controls";
    String text = avd.getDisplayName() + " was launched standalone " + reason;
    AndroidNotification.getInstance(project).showBalloon("AVD Launched Standalone", text, NotificationType.INFORMATION);
    if (foldable) {
      settings.setShowLaunchedStandaloneNotificationForFoldable(false);
    }
    else {
      settings.setShowLaunchedStandaloneNotification(false);
    }
  }

  /**
   * Allow subclasses to add listeners before starting the emulator.
   */
  protected void addListeners(@NotNull EmulatorRunner runner) {
  }

  /**
   * Adds necessary parameters to {@code commandLine}.
   */
  protected void addParameters(@Nullable Project project, @NotNull AvdInfo info, boolean forceColdBoot,
                               @NotNull GeneralCommandLine commandLine) {
    Map<String, String> properties = info.getProperties();
    String netDelay = properties.get(AvdWizardUtils.AVD_INI_NETWORK_LATENCY);
    String netSpeed = properties.get(AvdWizardUtils.AVD_INI_NETWORK_SPEED);
    if (netDelay != null) {
      commandLine.addParameters("-netdelay", netDelay);
    }

    if (netSpeed != null) {
      commandLine.addParameters("-netspeed", netSpeed);
    }

    // Control fast boot
    if (EmulatorAdvFeatures.emulatorSupportsFastBoot(mySdkHandler,
                                                     new StudioLoggerProgressIndicator(AvdManagerConnection.class),
                                                     new LogWrapper(Logger.getInstance(AvdManagerConnection.class)))) {
      if ("yes".equals(properties.get(AvdWizardUtils.USE_COLD_BOOT))) {
        // Do not fast boot and do not store a snapshot on exit
        commandLine.addParameter("-no-snapstorage");
      }
      else if (forceColdBoot) {
        // No fast boot now, but do store a snapshot on exit for next time
        commandLine.addParameter("-no-snapshot-load");
      }
      else if ("yes".equals(properties.get(AvdWizardUtils.USE_CHOSEN_SNAPSHOT_BOOT))) {
        // Fast boot with the specific file that was requested. Don't save on exit.
        commandLine.addParameters("-snapshot", StringUtil.notNullize(properties.get(AvdWizardUtils.CHOSEN_SNAPSHOT_FILE)));
        commandLine.addParameter("-no-snapshot-save");
      }
      // We could use "-snapstorage" for the "normal" case, but don't bother. It is the default.
    }

    writeParameterFile(commandLine);

    commandLine.addParameters("-avd", info.getName());
    if (shouldBeLaunchedEmbedded(project, info)) {
      commandLine.addParameters("-qt-hide-window", "-grpc-use-token", "-idle-grpc-timeout", "300"); // Launch headless.
    }
  }

  private static boolean shouldBeLaunchedEmbedded(@Nullable Project project, @NotNull AvdInfo avd) {
    // In order for an AVD to be launched in a tool window the corresponding option should be
    // enabled in Emulator settings and the AVD should not be foldable, TV, or Android Auto.
    return isEmulatorToolWindowAvailable(project) && // Emulator tool window is available only for Android projects.
           !isFoldable(avd) &&
           !"android-tv".equals(avd.getProperty(AVD_INI_TAG_ID)) &&
           !"android-automotive".equals(avd.getProperty(AVD_INI_TAG_ID));
  }

  public static boolean isEmulatorToolWindowAvailable(@Nullable Project project) {
    return EmulatorSettings.getInstance().getLaunchInToolWindow() && project != null && AndroidUtils.hasAndroidFacets(project);
  }

  public static boolean isFoldable(@NotNull AvdInfo avd) {
    String displayRegionWidth = avd.getProperty("hw.displayRegion.0.1.width");
    return displayRegionWidth != null && !"0".equals(displayRegionWidth);
  }

  /**
   * Indicates if the Emulator's version is at least {@code desired}
   * @return true if the Emulator version is the desired version or higher
   */
  public boolean emulatorVersionIsAtLeast(@NotNull Revision desired) {
    if (mySdkHandler == null) return false; // Don't know, so guess
    ProgressIndicator log = new StudioLoggerProgressIndicator(AvdWizardUtils.class);
    LocalPackage sdkPackage = mySdkHandler.getLocalPackage(SdkConstants.FD_EMULATOR, log);
    if (sdkPackage == null) {
      return false;
    }
    return (sdkPackage.getVersion().compareTo(desired) >= 0);
  }

  /**
   * Write HTTP Proxy information to a temporary file.
   * Put the file's name on the command line.
   */
  protected void writeParameterFile(@NotNull GeneralCommandLine commandLine) {
    if (!emulatorVersionIsAtLeast(EMULATOR_REVISION_SUPPORTS_STUDIO_PARAMS)) {
      // Older versions of the emulator don't accept this information.
      return;
    }
    HttpConfigurable httpInstance = HttpConfigurable.getInstance();
    if (httpInstance == null) {
      return; // No proxy info to send
    }

    // Extract the proxy information
    List<String> proxyParameters = new ArrayList<String>();

    List<Pair<String, String>> myPropList = httpInstance.getJvmProperties(false, null);
    for (Pair<String, String> kv : myPropList) {
      switch (kv.getFirst()) {
        case "http.proxyHost":
        case "http.proxyPort":
        case "https.proxyHost":
        case "https.proxyPort":
        case "proxy.authentication.username":
        case "proxy.authentication.password":
          proxyParameters.add(kv.getFirst() + "=" + kv.getSecond() + "\n");
          break;
        default:
          break; // Don't care about anything else
      }
    }

    if (proxyParameters.isEmpty()) {
      return; // No values to send
    }

    File tempFile = writeTempFile(proxyParameters);
    if (tempFile != null) {
        commandLine.addParameters("-studio-params", tempFile.getAbsolutePath());
    }
  }

  /** Create a directory under $ANDROID_HOME where we can write
   * temporary files.
   *
   * @return The directory file. This will be null if we
   * could not find, create, or write the directory.
   */
  @Nullable
  public static File tempFileDirectory() {
    // Create a temporary file in /temp under $ANDROID_HOME.
    String androidHomeValue = System.getenv(ANDROID_HOME_ENV);
    if (androidHomeValue == null) {
      // Fall back to the user's home directory
      androidHomeValue = System.getProperty("user.home");
    }
    File tempDir = new File(androidHomeValue, "temp");
    tempDir.mkdirs(); // Create if necessary
    if (!tempDir.exists()) {
      return null; // Give up
    }
    return tempDir;
  }

  /** Create a temporary file and write some parameters into it.
   * This is how we pass parameters to the Emulator (other than
   * on the command line).
   * The file is marked to be deleted when Studio exits. This is
   * to increase security in case the file contains sensitive
   * information.
   *
   * @param fileContents What should be written to the file.
   * @return The temporary file. This will be null
   * if we could not create or write the file.
   */
  @Nullable
  public static File writeTempFile(List<String> fileContents) {
    File tempFile = null;
    try {
      File tempDir = tempFileDirectory();
      if (tempDir == null) {
        return null; // Fail
      }
      tempFile = File.createTempFile("emu", ".tmp", tempDir);
      tempFile.deleteOnExit(); // File disappears when Studio exits
      tempFile.setReadable(false, false); // Non-owner cannot read
      tempFile.setReadable(true, true); // Owner can read

      final FileWriter fileWriter = new FileWriter(tempFile);
      try (BufferedWriter tempFileWriter = new BufferedWriter(fileWriter)) {
        for (String fileLine : fileContents) {
          tempFileWriter.write(fileLine);
        }
      }
    }
    catch (IOException ex) {
      // Try to remove the temporary file
      if (tempFile != null) {
        tempFile.delete(); // Ignore the return value
        tempFile = null;
      }
    }
    return tempFile;
  }

  @NotNull
  private ListenableFuture<IDevice> handleAccelerationError(@Nullable Project project,
                                                            @NotNull AvdInfo info,
                                                            @NotNull AccelerationErrorCode code) {
    if (code.getSolution().equals(SolutionCode.NONE)) {
      // noinspection UnstableApiUsage
      return Futures.immediateFailedFuture(new RuntimeException("Could not start AVD"));
    }

    // noinspection ConstantConditions, UnstableApiUsage
    return Futures.transformAsync(
      showAccelerationErrorDialog(code, project),
      result -> tryFixingAccelerationError(result, project, info, code),
      MoreExecutors.directExecutor());
  }

  @NotNull
  private ListenableFuture<Integer> showAccelerationErrorDialog(@NotNull AccelerationErrorCode code, @Nullable Project project) {
    return myEdtListeningExecutorService.submit(() -> {
      String message = (SystemInfo.isLinux ? "KVM" : "Intel HAXM") + " is required to run this AVD.\n"
                       + code.getProblem() + '\n'
                       + '\n'
                       + code.getSolutionMessage() + '\n';

      return Messages.showOkCancelDialog(
        project,
        message,
        code.getSolution().getDescription(),
        Messages.OK_BUTTON,
        Messages.CANCEL_BUTTON,
        AllIcons.General.WarningDialog);
    });
  }

  @NotNull
  private ListenableFuture<IDevice> tryFixingAccelerationError(int result,
                                                               @Nullable Project project,
                                                               @NotNull AvdInfo info,
                                                               @NotNull AccelerationErrorCode code) {
    if (result == Messages.CANCEL) {
      // noinspection UnstableApiUsage
      return Futures.immediateFailedFuture(new RuntimeException("Could not start AVD"));
    }

    SettableFuture<IDevice> future = SettableFuture.create();

    @SuppressWarnings("UnstableApiUsage")
    Runnable setFuture = () -> future.setFuture(startAvd(project, info));

    Runnable setException = () -> future.setException(new RuntimeException("Retry after fixing problem by hand"));
    ApplicationManager.getApplication().invokeLater(AccelerationErrorSolution.getActionForFix(code, project, setFuture, setException));

    return future;
  }

  /**
   * Run "emulator -accel-check" to check the status for emulator acceleration on this machine.
   * Return a {@link AccelerationErrorCode}.
   */
  public AccelerationErrorCode checkAcceleration() {
    if (!initIfNecessary()) {
      return AccelerationErrorCode.UNKNOWN_ERROR;
    }
    File emulatorBinary = getEmulatorBinary();
    if (emulatorBinary == null) {
      return AccelerationErrorCode.NO_EMULATOR_INSTALLED;
    }
    if (getMemorySize() < Storage.Unit.GiB.getNumberOfBytes()) {
      // TODO: The emulator -accel-check current does not check for the available memory, do it here instead:
      return AccelerationErrorCode.NOT_ENOUGH_MEMORY;
    }
    if (!hasQEMU2Installed()) {
      return AccelerationErrorCode.TOOLS_UPDATE_REQUIRED;
    }
    GeneralCommandLine commandLine = new GeneralCommandLine();
    File checkBinary = getEmulatorCheckBinary();
    if (checkBinary != null) {
      commandLine.setExePath(checkBinary.getPath());
      commandLine.addParameter("accel");
    }
    else {
      commandLine.setExePath(emulatorBinary.getPath());
      commandLine.addParameter("-accel-check");
    }
    int exitValue;
    try {
      CapturingAnsiEscapesAwareProcessHandler process = new CapturingAnsiEscapesAwareProcessHandler(commandLine);
      ProcessOutput output = process.runProcess();
      exitValue = output.getExitCode();
    }
    catch (ExecutionException e) {
      exitValue = AccelerationErrorCode.UNKNOWN_ERROR.getErrorCode();
    }
    if (exitValue != 0) {
      return AccelerationErrorCode.fromExitCode(exitValue);
    }
    if (!hasPlatformToolsForQEMU2Installed()) {
      return AccelerationErrorCode.PLATFORM_TOOLS_UPDATE_ADVISED;
    }
    if (!hasSystemImagesForQEMU2Installed()) {
      return AccelerationErrorCode.SYSTEM_IMAGE_UPDATE_ADVISED;
    }
    return AccelerationErrorCode.ALREADY_INSTALLED;
  }

  @NotNull
  public ListenableFuture<AccelerationErrorCode> checkAccelerationAsync() {
    return MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE).submit(this::checkAcceleration);
  }

  /**
   * Update the given AVD with the new settings or create one if no AVD is specified.
   * Returns the created AVD.
   */
  @Nullable
  public AvdInfo createOrUpdateAvd(@Nullable AvdInfo currentInfo,
                                   @NotNull String avdName,
                                   @NotNull Device device,
                                   @NotNull SystemImageDescription systemImageDescription,
                                   @NotNull ScreenOrientation orientation,
                                   boolean isCircular,
                                   @Nullable String sdCard,
                                   @Nullable File skinFolder,
                                   @NotNull Map<String, String> hardwareProperties,
                                   boolean removePrevious) {
    if (!initIfNecessary()) {
      return null;
    }

    File avdFolder;
    try {
      if (currentInfo != null) {
        avdFolder = new File(currentInfo.getDataFolderPath());
      } else {
        avdFolder = AvdInfo.getDefaultAvdFolder(myAvdManager, avdName, myFileOp, true);
      }
    }
    catch (AndroidLocation.AndroidLocationException e) {
      IJ_LOG.error("Could not create AVD " + avdName, e);
      return null;
    }

    Dimension resolution = device.getScreenSize(orientation);
    assert resolution != null;
    String skinName = null;

    if (skinFolder == null && isCircular) {
      skinFolder = getRoundSkin(systemImageDescription);
    }
    if (FileUtil.filesEqual(skinFolder, AvdWizardUtils.NO_SKIN)) {
      skinFolder = null;
    }
    if (skinFolder == null) {
      skinName = String.format(Locale.US, "%dx%d", Math.round(resolution.getWidth()), Math.round(resolution.getHeight()));
    }
    if (orientation == ScreenOrientation.LANDSCAPE) {
      hardwareProperties.put(HardwareProperties.HW_INITIAL_ORIENTATION,
                             StringUtil.toLowerCase(ScreenOrientation.LANDSCAPE.getShortDisplayValue()));
    }
    if (device.getId().equals("13.5in Freeform")) {
      hardwareProperties.put(AVD_INI_DISPLAY_SETTINGS_FILE, "freeform");
    }

    if (currentInfo != null && !avdName.equals(currentInfo.getName()) && removePrevious) {
      boolean success = myAvdManager.moveAvd(currentInfo, avdName, currentInfo.getDataFolderPath(), SDK_LOG);
      if (!success) {
        return null;
      }
    }

    return myAvdManager.createAvd(avdFolder,
                                  avdName,
                                  systemImageDescription.getSystemImage(),
                                  skinFolder,
                                  skinName,
                                  sdCard,
                                  hardwareProperties,
                                  device.getBootProps(),
                                  device.hasPlayStore(),
                                  false,
                                  removePrevious,
                                  SDK_LOG);
  }

  @Nullable
  private static File getRoundSkin(SystemImageDescription systemImageDescription) {
    File[] skins = systemImageDescription.getSkins();
    for (File skin : skins) {
      if (skin.getName().contains("Round")) {
        return skin;
      }
    }
    return null;
  }

  public static boolean doesSystemImageSupportQemu2(@Nullable SystemImageDescription description, @NotNull FileOp fileOp) {
    if (description == null) {
      return false;
    }
    ISystemImage systemImage = description.getSystemImage();
    if (systemImage == null) {
      return false;
    }
    File location = systemImage.getLocation();
    if (!fileOp.isDirectory(location)) {
      return false;
    }
    String[] files = fileOp.list(location, null);
    if (files != null) {
      for (String filename : files) {
        if (filename.startsWith("kernel-ranchu")) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean avdExists(String candidate) {
    if (!initIfNecessary()) {
      return false;
    }
    return myAvdManager.getAvd(candidate, false) != null;
  }

  static boolean isAvdRepairable(@NotNull AvdInfo.AvdStatus avdStatus) {
    return avdStatus == AvdInfo.AvdStatus.ERROR_IMAGE_DIR
           || avdStatus == AvdInfo.AvdStatus.ERROR_DEVICE_CHANGED
           || avdStatus == AvdInfo.AvdStatus.ERROR_DEVICE_MISSING
           || avdStatus == AvdInfo.AvdStatus.ERROR_IMAGE_MISSING;
  }

  public static boolean isSystemImageDownloadProblem(@NotNull AvdInfo.AvdStatus status) {
    switch (status) {
      case ERROR_IMAGE_DIR:
      case ERROR_IMAGE_MISSING:
        return true;
      default:
        return false;
    }
  }

  @Nullable
  public AvdInfo reloadAvd(@NotNull String avdId) {
    if (initIfNecessary()) {
      assert myAvdManager != null;
      AvdInfo avd = myAvdManager.getAvd(avdId, false);
      if (avd != null) {
        return reloadAvd(avd);
      }
    }
    return null;
  }

  @NotNull
  private AvdInfo reloadAvd(@NotNull AvdInfo avdInfo) {
    return myAvdManager.reloadAvd(avdInfo, SDK_LOG);
  }

  @Nullable
  public static String getRequiredSystemImagePath(@NotNull AvdInfo avdInfo) {
    String imageSystemDir = avdInfo.getProperties().get(AvdManager.AVD_INI_IMAGES_1);
    if (imageSystemDir == null) {
      return null;
    }
    return StringUtil.trimEnd(imageSystemDir.replace(File.separatorChar, RepoPackage.PATH_SEPARATOR), RepoPackage.PATH_SEPARATOR);
  }

  public boolean updateDeviceChanged(@NotNull AvdInfo avdInfo) {
    if (initIfNecessary()) {
      try {
        return myAvdManager.updateDeviceChanged(avdInfo, SDK_LOG) != null;
      }
      catch (IOException e) {
        IJ_LOG.warn("Could not update AVD Device " + avdInfo.getName(), e);
      }
    }
    return false;
  }

  public boolean wipeUserData(@NotNull AvdInfo avdInfo) {
    if (!initIfNecessary()) {
      return false;
    }
    // Delete the current user data file
    File userdataImage = new File(avdInfo.getDataFolderPath(), AvdManager.USERDATA_QEMU_IMG);
    if (myFileOp.exists(userdataImage)) {
      if (!myFileOp.delete(userdataImage)) {
        return false;
      }
    }
    // Delete the snapshots directory
    File snapshotDirectory = new File(avdInfo.getDataFolderPath(), AvdManager.SNAPSHOTS_DIRECTORY);
    myFileOp.deleteFileOrFolder(snapshotDirectory);

    return true;
  }

  public static String getAvdDisplayName(@NotNull AvdInfo avdInfo) {
    String displayName = avdInfo.getProperties().get(AVD_INI_DISPLAY_NAME);
    if (displayName == null) {
      displayName = avdInfo.getName().replaceAll("[_-]+", " ");
    }
    return displayName;
  }

  public String uniquifyDisplayName(String name) {
    int suffix = 1;
    String result = name;
    while (findAvdWithName(result)) {
      result = String.format(Locale.US, "%1$s %2$d", name, ++suffix);
    }
    return result;
  }

  public boolean findAvdWithName(String name) {
    for (AvdInfo avd : getAvds(false)) {
      if (getAvdDisplayName(avd).equals(name)) {
        return true;
      }
    }
    return false;
  }

  public static long getMemorySize() {
    if (ourMemorySize < 0) {
      ourMemorySize = checkMemorySize();
    }
    return ourMemorySize;
  }

  private static long checkMemorySize() {
    OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
    // This is specific to JDKs derived from Oracle JDK (including OpenJDK and Apple JDK among others).
    // Other then this, there's no standard way of getting memory size
    // without adding 3rd party libraries or using native code.
    try {
      Class<?> oracleSpecificMXBean = Class.forName("com.sun.management.OperatingSystemMXBean");
      Method getPhysicalMemorySizeMethod = oracleSpecificMXBean.getMethod("getTotalPhysicalMemorySize");
      Object result = getPhysicalMemorySizeMethod.invoke(osMXBean);
      if (result instanceof Number) {
        return ((Number)result).longValue();
      }
    }
    catch (ClassNotFoundException | NoSuchMethodException e) {
      // Unsupported JDK
    }
    catch (InvocationTargetException | IllegalAccessException e) {
      IJ_LOG.error(e); // Shouldn't happen (unsupported JDK?)
    }
    // Maximum memory allocatable to emulator - 32G. Only used if non-Oracle JRE.
    return 32L * Storage.Unit.GiB.getNumberOfBytes();
  }

  private static class SystemImageUpdateDependency {
    private final int myFeatureLevel;
    private final IdDisplay myTag;
    private final int myRequiredMajorRevision;

    public SystemImageUpdateDependency(int featureLevel, @NotNull IdDisplay tag, int requiredMajorRevision) {
      myFeatureLevel = featureLevel;
      myTag = tag;
      myRequiredMajorRevision = requiredMajorRevision;
    }

    public boolean updateRequired(@NotNull SystemImage image) {
      return updateRequired(image.getAbiType(), image.getAndroidVersion().getFeatureLevel(), image.getTag(), image.getRevision());
    }

    public boolean updateRequired(@NotNull String abiType, int featureLevel, @NotNull IdDisplay tag, @NotNull Revision revision) {
      Abi abi = Abi.getEnum(abiType);
      boolean isAvdIntel = abi == Abi.X86 || abi == Abi.X86_64;

      return isAvdIntel &&
             featureLevel == myFeatureLevel &&
             myTag.equals(tag) &&
             revision.getMajor() < myRequiredMajorRevision;
    }
  }
}

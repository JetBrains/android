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

import static com.android.sdklib.internal.avd.ConfigKey.SKIN_PATH;
import static java.nio.file.StandardOpenOption.WRITE;

import com.android.annotations.concurrency.Slow;
import com.android.ddmlib.IDevice;
import com.android.io.CancellableFileIo;
import com.android.prefs.AndroidLocationsException;
import com.android.prefs.AndroidLocationsSingleton;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoPackage;
import com.android.repository.io.FileOpUtils;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.deviceprovisioner.DeviceActionCanceledException;
import com.android.sdklib.deviceprovisioner.DeviceActionException;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.devices.Device;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.ConfigKey;
import com.android.sdklib.internal.avd.EmulatorAdvancedFeatures;
import com.android.sdklib.internal.avd.EmulatorPackage;
import com.android.sdklib.internal.avd.EmulatorPackages;
import com.android.sdklib.internal.avd.GenericSkin;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.sdklib.internal.avd.OnDiskSkin;
import com.android.sdklib.internal.avd.SdCard;
import com.android.sdklib.internal.avd.Skin;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode;
import com.android.tools.idea.avdmanager.AvdLaunchListener.RequestType;
import com.android.tools.idea.avdmanager.emulatorcommand.BootWithSnapshotEmulatorCommandBuilder;
import com.android.tools.idea.avdmanager.emulatorcommand.ColdBootEmulatorCommandBuilder;
import com.android.tools.idea.avdmanager.emulatorcommand.ColdBootNowEmulatorCommandBuilder;
import com.android.tools.idea.avdmanager.emulatorcommand.DefaultEmulatorCommandBuilderFactory;
import com.android.tools.idea.avdmanager.emulatorcommand.EmulatorCommandBuilder;
import com.android.tools.idea.avdmanager.emulatorcommand.EmulatorCommandBuilderFactory;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeAvdManagers;
import com.android.tools.idea.streaming.EmulatorSettings;
import com.android.utils.ILogger;
import com.android.utils.PathUtils;
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
import com.intellij.execution.process.ProcessHandler;
import com.intellij.icons.AllIcons;
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
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.net.HttpConfigurable;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.ide.PooledThreadExecutor;

/**
 * A wrapper class for communicating with {@link AvdManager} and exposing helper functions
 * for dealing with {@link AvdInfo} objects inside Android Studio.
 */
public class AvdManagerConnection {
  private static final Logger IJ_LOG = Logger.getInstance(AvdManagerConnection.class);
  private static final ILogger SDK_LOG = new LogWrapper(IJ_LOG);
  private static final ProgressIndicator REPO_LOG = new StudioLoggerProgressIndicator(AvdManagerConnection.class);
  private static final AvdManagerConnection NULL_CONNECTION = new AvdManagerConnection(null, null);

  private static final Map<Path, AvdManagerConnection> ourAvdCache = new WeakHashMap<>();

  private static @NotNull BiFunction<AndroidSdkHandler, Path, AvdManagerConnection> ourConnectionFactory =
    AvdManagerConnection::new;

  @Nullable
  private final AndroidSdkHandler mySdkHandler;

  @NotNull
  private final ListeningExecutorService myEdtListeningExecutorService;

  @Nullable
  private AvdManager myAvdManager;

  private final @Nullable Path myAvdHomeFolder;

  public @Nullable EmulatorPackage getEmulator() {
    if (mySdkHandler != null) {
      return EmulatorPackages.getEmulatorPackage(mySdkHandler, REPO_LOG);
    }
    return null;
  }

  @NotNull
  public static AvdManagerConnection getDefaultAvdManagerConnection() {
    AndroidSdkHandler handler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    if (handler.getLocation() == null) {
      return NULL_CONNECTION;
    }
    return getAvdManagerConnection(handler);
  }

  @NotNull
  public synchronized static AvdManagerConnection getAvdManagerConnection(@NotNull AndroidSdkHandler handler) {
    Path sdkPath = handler.getLocation();
    return ourAvdCache.computeIfAbsent(
      sdkPath, path -> {
        try {
          return ourConnectionFactory.apply(handler, AndroidLocationsSingleton.INSTANCE.getAvdLocation());
        }
        catch (AndroidLocationsException e) {
          IJ_LOG.warn(e);
          return NULL_CONNECTION;
        }
      });
  }

  private AvdManagerConnection(@Nullable AndroidSdkHandler sdkHandler, @Nullable Path avdHomeFolder) {
    this(sdkHandler, avdHomeFolder, MoreExecutors.listeningDecorator(EdtExecutorService.getInstance()));
  }

  @VisibleForTesting
  public AvdManagerConnection(
      @Nullable AndroidSdkHandler sdkHandler,
      @Nullable Path avdHomeFolder,
      @NotNull ListeningExecutorService edtListeningExecutorService) {
    mySdkHandler = sdkHandler;
    myEdtListeningExecutorService = edtListeningExecutorService;
    myAvdHomeFolder = avdHomeFolder;
  }

  /**
   * Sets the factory to be used for creating connections, so subclasses can be injected for testing.
   */
  @TestOnly
  public synchronized static void setConnectionFactory(@NotNull BiFunction<AndroidSdkHandler, Path, AvdManagerConnection> factory) {
    ourAvdCache.clear();
    ourConnectionFactory = factory;
  }

  @TestOnly
  public static void resetConnectionFactory() {
    setConnectionFactory(AvdManagerConnection::new);
  }

  /**
   * Setup our static instances if required. If the instance already exists, then this is a no-op.
   */
  private boolean initIfNecessary() {
    if (myAvdManager == null) {
      if (mySdkHandler == null) {
        return false;
      }
      if (myAvdHomeFolder == null) {
        IJ_LOG.warn("No AVD home folder");
        return false;
      }
      try {
        myAvdManager = IdeAvdManagers.INSTANCE.getAvdManager(mySdkHandler, myAvdHomeFolder);
      }
      catch (AndroidLocationsException e) {
        IJ_LOG.error(e);
        return false;
      }

      return myAvdManager != null;
    }
    return true;
  }

  /**
   * @param forceRefresh if true the manager will read the AVD list from disk. If false, the cached version in memory
   *                     is returned if available
   * @return a list of AVDs currently present on the system.
   */
  @NotNull
  @Slow
  public List<AvdInfo> getAvds(boolean forceRefresh) {
    if (!initIfNecessary()) {
      return ImmutableList.of();
    }
    assert myAvdManager != null;
    if (forceRefresh) {
      try {
        myAvdManager.reloadAvds();
      }
      catch (AndroidLocationsException e) {
        IJ_LOG.error("Could not find Android SDK!", e);
      }
    }
    return Lists.newArrayList(myAvdManager.getAllAvds());
  }

  /**
   * Delete the given AVD if it exists.
   */
  @Slow
  public boolean deleteAvd(@NotNull AvdInfo info) {
    if (!initIfNecessary()) {
      return false;
    }
    assert myAvdManager != null;
    return myAvdManager.deleteAvd(info);
  }

  @Slow
  public boolean isAvdRunning(@NotNull AvdInfo avd) {
    if (!initIfNecessary()) {
      return false;
    }

    assert myAvdManager != null;

    Optional<Boolean> online = myAvdManager.getPid(avd).stream()
      .mapToObj(ProcessHandle::of)
      .flatMap(Optional::stream)
      .map(ProcessHandle::isAlive)
      .findFirst();

    return online.orElseGet(() -> {
      SDK_LOG.warning("Unable to determine if " + avd.getName() + " is online, assuming it's not");
      return false;
    });
  }

  @Slow
  public void stopAvd(@NotNull AvdInfo info) {
    assert myAvdManager != null;
    myAvdManager.stopAvd(info);
  }

  /**
   * Starts the emulator without mounting a file to store or load state snapshots, forcing a full boot and disabling state snapshot
   * functionality.
   */
  public @NotNull ListenableFuture<IDevice> coldBoot(@Nullable Project project, @NotNull AvdInfo avd, @NotNull RequestType requestType) {
    return startAvd(project, avd, requestType, ColdBootEmulatorCommandBuilder::new);
  }

  /** Starts the emulator, booting from the "default_boot" snapshot. */
  public @NotNull ListenableFuture<IDevice> quickBoot(@Nullable Project project, @NotNull AvdInfo avd, @NotNull RequestType requestType) {
    return startAvd(project, avd, requestType, EmulatorCommandBuilder::new);
  }

  /** Starts the emulator, booting from the given snapshot (specified as the directory name beneath "snapshots", not a full path). */
  public @NotNull ListenableFuture<IDevice> bootWithSnapshot(
      @Nullable Project project, @NotNull AvdInfo avd, @NotNull String snapshot, @NotNull RequestType requestType) {
    return startAvd(project, avd, requestType, (emulator, a) -> new BootWithSnapshotEmulatorCommandBuilder(emulator, a, snapshot));
  }

  /** Boots the AVD, using its .ini file to determine the booting method. */
  public @NotNull ListenableFuture<IDevice> startAvd(@Nullable Project project, @NotNull AvdInfo info, @NotNull RequestType requestType) {
    return startAvd(project, info, requestType, new DefaultEmulatorCommandBuilderFactory());
  }

  /** Performs a cold boot and saves the emulator state on exit. */
  public @NotNull ListenableFuture<IDevice> startAvdWithColdBoot(
      @Nullable Project project, @NotNull AvdInfo info, @NotNull RequestType requestType) {
    return startAvd(project, info, requestType, ColdBootNowEmulatorCommandBuilder::new);
  }

  public @NotNull ListenableFuture<IDevice> startAvd(
      @Nullable Project project, @NotNull AvdInfo info, @NotNull RequestType requestType, @NotNull EmulatorCommandBuilderFactory factory) {
    if (!initIfNecessary()) {
      return Futures.immediateFailedFuture(new DeviceActionException("No Android SDK Found"));
    }
    assert mySdkHandler != null;

    String skinPath = info.getProperties().get(SKIN_PATH);
    if (skinPath != null) {
      Path skin = mySdkHandler.toCompatiblePath(skinPath);
      // For historical reasons skin.path in config.ini may be a path relative to SDK
      // rather than its "skins" directory. Remove the "skins" prefix in that case.
      if (!skin.isAbsolute() && skin.getNameCount() > 1 && skin.getName(0).toString().equals("skins")) {
        skin = skin.subpath(1, skin.getNameCount());
      }

      DeviceSkinUpdater.updateSkin(skin);
    }

    return Futures.transformAsync(
        MoreExecutors.listeningDecorator(PooledThreadExecutor.INSTANCE)
            .submit(() -> EmulatorAccelerationCheck.checkAcceleration(mySdkHandler)),
        code ->
            continueToStartAvdIfAccelerationErrorIsNotBlocking(
                code, project, info, requestType, factory),
        MoreExecutors.directExecutor());
  }

  private @NotNull ListenableFuture<IDevice> continueToStartAvdIfAccelerationErrorIsNotBlocking(
      @NotNull AccelerationErrorCode code,
      @Nullable Project project,
      @NotNull AvdInfo info,
      @NotNull RequestType requestType,
      @NotNull EmulatorCommandBuilderFactory factory) {
    if (!code.getProblem().isEmpty()) {
      IJ_LOG.warn(String.format("Launching %s: %s: %s", info.getName(), code, code.getProblem()));
    }
    switch (code) {
      case ALREADY_INSTALLED:
        return continueToStartAvd(project, info, requestType, factory);
      case TOOLS_UPDATE_REQUIRED:
      case PLATFORM_TOOLS_UPDATE_ADVISED:
      case SYSTEM_IMAGE_UPDATE_ADVISED:
        // Launch the virtual device with possibly degraded performance even if there are updates
        // noinspection DuplicateBranchesInSwitch
        return continueToStartAvd(project, info, requestType, factory);
      case NO_EMULATOR_INSTALLED:
        return handleAccelerationError(project, info, requestType, code);
      default:
        Abi abi = Abi.getEnum(info.getAbiType());

        if (abi == null) {
          return continueToStartAvd(project, info, requestType, factory);
        }

        if (abi.equals(Abi.X86) || abi.equals(Abi.X86_64)) {
          return handleAccelerationError(project, info, requestType, code);
        }

        // Let ARM and MIPS virtual devices launch without hardware acceleration
        return continueToStartAvd(project, info, requestType, factory);
    }
  }

  private @NotNull ListenableFuture<IDevice> continueToStartAvd(@Nullable Project project,
                                                                @NotNull AvdInfo avd,
                                                                @NotNull RequestType requestType,
                                                                @NotNull EmulatorCommandBuilderFactory factory) {
    EmulatorPackage emulator = getEmulator();
    if (emulator == null) {
      IJ_LOG.error("No emulator binary found!");
      return Futures.immediateFailedFuture(new DeviceActionException("No emulator installed"));
    }

    Path emulatorBinary = emulator.getEmulatorBinary();
    if (emulatorBinary == null) {
      IJ_LOG.error("No emulator binary found!");
      return Futures.immediateFailedFuture(new DeviceActionException("No emulator binary found"));
    }

    avd = reloadAvd(avd); // Reload the AVD in case it was modified externally.
    String avdName = avd.getDisplayName();

    // TODO: The emulator stores pid of the running process inside the .lock file (userdata-qemu.img.lock in Linux
    //       and userdata-qemu.img.lock/pid on Windows). We should detect whether those lock files are stale and if so,
    //       delete them without showing this error. Either the emulator provides a command to do that, or we learn
    //       about its internals (qemu/android/utils/filelock.c) and perform the same action here. If it is not stale,
    //       then we should show this error and if possible, bring that window to the front.
    assert myAvdManager != null;
    if (myAvdManager.isAvdRunning(avd)) {
      myAvdManager.logRunningAvdInfo(avd);
      return Futures.immediateFailedFuture(new AvdIsAlreadyRunningException(avd));
    }

    GeneralCommandLine commandLine =
        newEmulatorCommand(project, emulatorBinary, avd, requestType == RequestType.DIRECT_RUNNING_DEVICES, factory);
    EmulatorRunner runner = new EmulatorRunner(commandLine, avd);

    ProcessHandler processHandler;
    try {
      processHandler = runner.start();
    }
    catch (ExecutionException e) {
      IJ_LOG.error("Error launching emulator", e);
      return Futures.immediateFailedFuture(new DeviceActionException(String.format("Error launching emulator %1$s", avdName), e));
    }

    // If we're using qemu2, it has its own progress bar, so put ours in the background. Otherwise, show it.
    ProgressWindow p = emulator.isQemu2()
                       ? new BackgroundableProcessIndicator(project, "Launching emulator", PerformInBackgroundOption.ALWAYS_BACKGROUND,
                                                            "", "", false)
                       : new ProgressWindow(false, true, project);
    p.setIndeterminate(false);
    p.setDelayInMillis(0);

    // It takes >= 8 seconds to start the Emulator. Display a small progress indicator otherwise it seems like
    // the action wasn't invoked and users tend to click multiple times on it, ending up with several instances of the emulator.
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        p.start();
        p.setText("Starting AVD...");
        for (double d = 0; d < 1; d += 1.0 / 80) {
          p.setFraction(d);
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
    messageBus.syncPublisher(AvdLaunchListener.TOPIC).avdLaunched(avd, commandLine, requestType, project);

    return EmulatorConnectionListener.getDeviceForEmulator(project, avd.getName(), processHandler, 5, TimeUnit.MINUTES);
  }

  protected @NotNull GeneralCommandLine newEmulatorCommand(@Nullable Project project,
                                                           @NotNull Path emulator,
                                                           @NotNull AvdInfo avd,
                                                           boolean forceLaunchInToolWindow,
                                                           @NotNull EmulatorCommandBuilderFactory factory) {
    Optional<Collection<String>> params = Optional.ofNullable(System.getenv("studio.emu.params")).map(Splitter.on(',')::splitToList);

    return factory.newEmulatorCommandBuilder(emulator, avd)
      .setAvdHome(myAvdManager.getBaseAvdFolder())
      .setEmulatorSupportsSnapshots(EmulatorFeatures.getEmulatorFeatures(getEmulator()).contains(EmulatorAdvancedFeatures.FAST_BOOT))
      .setStudioParams(writeParameterFile().orElse(null))
      .setLaunchInToolWindow(canLaunchInToolWindow(avd, project) &&
                             (forceLaunchInToolWindow || EmulatorSettings.getInstance().getLaunchInToolWindow()))
      .addAllStudioEmuParams(params.orElse(Collections.emptyList()))
      .build();
  }

  /** Checks whether the emulator can be launched in the Running Device tool window. */
  private static boolean canLaunchInToolWindow(@NotNull AvdInfo avd, @Nullable Project project) {
    return project != null && ToolWindowManager.getInstance(project).getToolWindow("Running Devices") != null;
  }

  public static boolean isFoldable(@NotNull AvdInfo avd) {
    String displayRegionWidth = avd.getProperty("hw.displayRegion.0.1.width");
    return displayRegionWidth != null && !"0".equals(displayRegionWidth);
  }

  /**
   * Write HTTP Proxy information to a temporary file.
   */
  private @NotNull Optional<Path> writeParameterFile() {
    if (!getEmulator().hasStudioParamsSupport()) {
      return Optional.empty();
    }
    HttpConfigurable httpInstance = HttpConfigurable.getInstance();
    if (httpInstance == null) {
      return Optional.empty();
    }

    // Extract the proxy information
    List<String> proxyParameters = new ArrayList<>();

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
      return Optional.empty();
    }

    return Optional.ofNullable(writeTempFile(proxyParameters))
      .map(File::getAbsoluteFile)
      .map(File::toPath);
  }

  /**
   * Creates a temporary file and write some parameters into it.
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
  public static File writeTempFile(@NotNull List<String> fileContents) {
    File tempFile = null;
    try {
      tempFile = FileUtil.createTempFile("emu", ".tmp", true);
      tempFile.deleteOnExit(); // File disappears when Studio exits
      if (!tempFile.setReadable(false, false) || // Non-owner cannot read
          !tempFile.setReadable(true, true)) { // Owner can read
        IJ_LOG.warn("Error setting permissions for " + tempFile.getAbsolutePath());
      }

      Files.write(tempFile.toPath(), fileContents, WRITE);
    }
    catch (IOException e) {
      // Try to remove the temporary file
      if (tempFile != null) {
        //noinspection ResultOfMethodCallIgnored
        tempFile.delete();
        tempFile = null;
      }
    }
    return tempFile;
  }

  @NotNull
  private ListenableFuture<IDevice> handleAccelerationError(@Nullable Project project,
                                                            @NotNull AvdInfo info,
                                                            @NotNull RequestType requestType,
                                                            @NotNull AccelerationErrorCode code) {
    if (code.getSolution().equals(SolutionCode.NONE)) {
      return Futures.immediateFailedFuture(new DeviceActionException(code.getProblem() + "\n\n" + code.getSolutionMessage() + '\n'));
    }

    // noinspection ConstantConditions, UnstableApiUsage
    return Futures.transformAsync(
      showAccelerationErrorDialog(code, project),
      result -> tryFixingAccelerationError(result, project, info, requestType, code),
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
        Messages.getOkButton(),
        Messages.getCancelButton(),
        AllIcons.General.WarningDialog);
    });
  }

  @NotNull
  private ListenableFuture<IDevice> tryFixingAccelerationError(int result,
                                                               @Nullable Project project,
                                                               @NotNull AvdInfo info,
                                                               @NotNull RequestType requestType,
                                                               @NotNull AccelerationErrorCode code) {
    if (result == Messages.CANCEL) {
      return Futures.immediateFailedFuture(new DeviceActionCanceledException("Could not start AVD"));
    }

    SettableFuture<IDevice> future = SettableFuture.create();

    Runnable setFuture = () -> future.setFuture(startAvd(project, info, requestType));

    Runnable setException = () -> future.setException(new DeviceActionCanceledException("Retry after fixing problem by hand"));
    ApplicationManager.getApplication().invokeLater(AccelerationErrorSolution.getActionForFix(code, project, setFuture, setException));

    return future;
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
                                   @Nullable SdCard sdCard,
                                   @Nullable Path skinFolder,
                                   @NotNull Map<String, String> hardwareProperties,
                                   @Nullable Map<String, String> userSettings,
                                   boolean removePrevious) {
    if (!initIfNecessary()) {
      return null;
    }
    assert mySdkHandler != null;

    Path avdFolder;
    try {
      if (currentInfo != null) {
        avdFolder = currentInfo.getDataFolderPath();
      }
      else {
        assert myAvdManager != null;
        avdFolder = AvdInfo.getDefaultAvdFolder(myAvdManager, avdName, true);
      }
    }
    catch (Throwable e) {
      IJ_LOG.error("Could not create AVD " + avdName, e);
      return null;
    }

    Dimension resolution = device.getScreenSize(orientation);
    assert resolution != null;
    Skin skin;
    if (skinFolder == null && isCircular) {
      File skinFile = getRoundSkin(systemImageDescription);
      skinFolder = skinFile == null ? null : mySdkHandler.toCompatiblePath(skinFile);
    }
    if (Objects.equals(skinFolder, SkinUtils.noSkin())) {
      skinFolder = null;
    }
    if (skinFolder == null) {
      skin = new GenericSkin((int) Math.round(resolution.getWidth()), (int) Math.round(resolution.getHeight()));
    } else {
      skin = new OnDiskSkin(skinFolder);
    }
    if (orientation == ScreenOrientation.LANDSCAPE) {
      hardwareProperties.put(HardwareProperties.HW_INITIAL_ORIENTATION,
                             ScreenOrientation.LANDSCAPE.getShortDisplayValue().toLowerCase(Locale.ROOT));
    }
    if (currentInfo != null && !avdName.equals(currentInfo.getName()) && removePrevious) {
      assert myAvdManager != null;
      boolean success = myAvdManager.moveAvd(currentInfo, avdName, currentInfo.getDataFolderPath());
      if (!success) {
        return null;
      }
    }

    assert myAvdManager != null;
    return myAvdManager.createAvd(avdFolder,
                                  avdName,
                                  systemImageDescription.getSystemImage(),
                                  skin,
                                  sdCard,
                                  hardwareProperties,
                                  userSettings,
                                  device.getBootProps(),
                                  device.hasPlayStore(),
                                  false,
                                  removePrevious);
  }

  @Nullable
  private File getRoundSkin(SystemImageDescription systemImageDescription) {
    for (Path skin : systemImageDescription.getSkins()) {
      if (skin.getFileName().toString().contains("Round")) {
        return FileOpUtils.toFile(skin);
      }
    }
    return null;
  }

  public static boolean doesSystemImageSupportQemu2(@Nullable SystemImageDescription description) {
    if (description == null) {
      return false;
    }
    ISystemImage systemImage = description.getSystemImage();
    if (systemImage == null) {
      return false;
    }
    Path location = systemImage.getLocation();
    try (Stream<Path> files = CancellableFileIo.list(location)) {
      return files.anyMatch(file -> file.getFileName().toString().startsWith("kernel-ranchu"));
    }
    catch (IOException e) {
      return false;
    }
  }

  public @Nullable AvdInfo findAvd(@NotNull String avdId) {
    if (initIfNecessary()) {
      assert myAvdManager != null;
      return myAvdManager.getAvd(avdId, false);
    }
    return null;
  }

  public boolean avdExists(@NotNull String candidate) {
    return findAvd(candidate) != null;
  }

  @Nullable
  public AvdInfo reloadAvd(@NotNull String avdId) {
    AvdInfo avd = findAvd(avdId);
    if (avd != null) {
      return reloadAvd(avd);
    }
    return null;
  }

  @NotNull
  private AvdInfo reloadAvd(@NotNull AvdInfo avdInfo) {
    assert myAvdManager != null;
    return myAvdManager.reloadAvd(avdInfo);
  }

  @Nullable
  public static String getRequiredSystemImagePath(@NotNull AvdInfo avdInfo) {
    String imageSystemDir = avdInfo.getProperties().get(ConfigKey.IMAGES_1);
    if (imageSystemDir == null) {
      return null;
    }
    return StringUtil.trimEnd(imageSystemDir.replace(File.separatorChar, RepoPackage.PATH_SEPARATOR), RepoPackage.PATH_SEPARATOR);
  }

  public final @NotNull ListenableFuture<Boolean> wipeUserDataAsync(@NotNull AvdInfo avd) {
    return Futures.submit(() -> wipeUserData(avd), AppExecutorUtil.getAppExecutorService());
  }

  @Slow
  public boolean wipeUserData(@NotNull AvdInfo avdInfo) {
    if (!initIfNecessary()) {
      return false;
    }
    assert mySdkHandler != null;
    // Delete the current user data file
    Path path = avdInfo.getDataFolderPath().resolve(AvdManager.USERDATA_QEMU_IMG);
    if (Files.exists(path)) {
      try {
        PathUtils.deleteRecursivelyIfExists(path);
      }
      catch (IOException e) {
        return false;
      }
    }
    // Delete the snapshots directory
    Path snapshotDirectory = avdInfo.getDataFolderPath().resolve(AvdManager.SNAPSHOTS_DIRECTORY);
    try {
      PathUtils.deleteRecursivelyIfExists(snapshotDirectory);
    }
    catch (IOException ignore) {}

    return true;
  }

  /**
   * Computes a reasonable display name for a newly-created AVD with the given device and version.
   */
  public @NotNull String getDefaultDeviceDisplayName(@NotNull Device device, @NotNull AndroidVersion version) {
    // A device name might include the device's screen size as, e.g., 7". The " is not allowed in
    // a display name. Ensure that the display name does not include any forbidden characters.
    return uniquifyDisplayName(AvdNameVerifier.stripBadCharacters(device.getDisplayName()) + " API " + version.getApiStringWithExtension());
  }

  public String uniquifyDisplayName(@NotNull String name) {
    int suffix = 1;
    String result = name;
    while (findAvdWithDisplayName(result)) {
      result = String.format(Locale.US, "%1$s %2$d", name, ++suffix);
    }
    return result;
  }

  public boolean findAvdWithDisplayName(@NotNull String name) {
    for (AvdInfo avd : getAvds(false)) {
      if (avd.getDisplayName().equals(name)) {
        return true;
      }
    }
    return false;
  }
}

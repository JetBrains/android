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

import static com.android.SdkConstants.ANDROID_SDK_ROOT_ENV;
import static com.android.SdkConstants.FD_EMULATOR;
import static com.android.SdkConstants.FD_LIB;
import static com.android.SdkConstants.FN_HARDWARE_INI;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_DISPLAY_SETTINGS_FILE;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_FOLD_AT_POSTURE;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_HINGE;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_HINGE_ANGLES_POSTURE_DEFINITIONS;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_HINGE_AREAS;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_HINGE_COUNT;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_HINGE_DEFAULTS;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_HINGE_RANGES;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_HINGE_SUB_TYPE;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_HINGE_TYPE;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_POSTURE_LISTS;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_RESIZABLE_CONFIG;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_ROLL;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_ROLL_COUNT;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_ROLL_DEFAULTS;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_ROLL_DIRECTION;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_ROLL_PERCENTAGES_POSTURE_DEFINITIONS;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_ROLL_RADIUS;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_ROLL_RANGES;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_ROLL_RESIZE_1_AT_POSTURE;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_ROLL_RESIZE_2_AT_POSTURE;
import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_SKIN_PATH;
import static com.android.sdklib.repository.targets.SystemImage.DEFAULT_TAG;
import static com.android.sdklib.repository.targets.SystemImage.GOOGLE_APIS_TAG;
import static java.nio.file.StandardOpenOption.WRITE;

import com.android.SdkConstants;
import com.android.annotations.concurrency.Slow;
import com.android.ddmlib.IDevice;
import com.android.io.CancellableFileIo;
import com.android.prefs.AndroidLocationsException;
import com.android.prefs.AndroidLocationsSingleton;
import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoPackage;
import com.android.repository.io.FileOpUtils;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.PathFileWrapper;
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
import com.android.tools.idea.avdmanager.emulatorcommand.BootWithSnapshotEmulatorCommandBuilder;
import com.android.tools.idea.avdmanager.emulatorcommand.ColdBootEmulatorCommandBuilder;
import com.android.tools.idea.avdmanager.emulatorcommand.ColdBootNowEmulatorCommandBuilder;
import com.android.tools.idea.avdmanager.emulatorcommand.DefaultEmulatorCommandBuilderFactory;
import com.android.tools.idea.avdmanager.emulatorcommand.EmulatorCommandBuilder;
import com.android.tools.idea.avdmanager.emulatorcommand.EmulatorCommandBuilderFactory;
import com.android.tools.idea.emulator.EmulatorSettings;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.AndroidSdks;
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
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Stream;
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
  private static final AvdManagerConnection NULL_CONNECTION = new AvdManagerConnection(null, null);
  private static final int MNC_API_LEVEL_23 = 23;
  private static final int LMP_MR1_API_LEVEL_22 = 22;

  private static final String INTERNAL_STORAGE_KEY = AvdManager.AVD_INI_DATA_PARTITION_SIZE;
  private static final String SD_CARD_STORAGE_KEY = AvdManager.AVD_INI_SDCARD_SIZE;

  public static final String AVD_INI_HW_LCD_DENSITY = "hw.lcd.density";
  public static final Revision TOOLS_REVISION_WITH_FIRST_QEMU2 = Revision.parseRevision("25.0.0 rc1");
  public static final Revision TOOLS_REVISION_25_0_2_RC3 = Revision.parseRevision("25.0.2 rc3");
  public static final Revision PLATFORM_TOOLS_REVISION_WITH_FIRST_QEMU2 = Revision.parseRevision("23.1.0");
  protected static final Revision EMULATOR_REVISION_SUPPORTS_STUDIO_PARAMS = Revision.parseRevision("26.1.0");

  private static final SystemImageUpdateDependency[] SYSTEM_IMAGE_DEPENDENCY_WITH_FIRST_QEMU2 = {
    new SystemImageUpdateDependency(LMP_MR1_API_LEVEL_22, DEFAULT_TAG, 2),
    new SystemImageUpdateDependency(LMP_MR1_API_LEVEL_22, GOOGLE_APIS_TAG, 2),
    new SystemImageUpdateDependency(MNC_API_LEVEL_23, DEFAULT_TAG, 6),
    new SystemImageUpdateDependency(MNC_API_LEVEL_23, GOOGLE_APIS_TAG, 10),
  };
  private static final SystemImageUpdateDependency[] SYSTEM_IMAGE_DEPENDENCY_WITH_25_0_2_RC3 = {
    new SystemImageUpdateDependency(LMP_MR1_API_LEVEL_22, DEFAULT_TAG, 4),
    new SystemImageUpdateDependency(LMP_MR1_API_LEVEL_22, GOOGLE_APIS_TAG, 4),
    new SystemImageUpdateDependency(MNC_API_LEVEL_23, DEFAULT_TAG, 8),
    new SystemImageUpdateDependency(MNC_API_LEVEL_23, GOOGLE_APIS_TAG, 12),
  };

  private static final Map<Path, AvdManagerConnection> ourAvdCache = new WeakHashMap<>();
  private static final @NotNull Map<@NotNull Path, @NotNull AvdManagerConnection> ourGradleAvdCache = new WeakHashMap<>();
  private static long ourMemorySize = -1;

  private static @NotNull BiFunction<@Nullable AndroidSdkHandler, @Nullable Path, @NotNull AvdManagerConnection> ourConnectionFactory =
    AvdManagerConnection::new;

  // A map from hardware config name to its belonging hardware property.
  private static @Nullable Map<String, HardwareProperties.HardwareProperty> ourHardwareProperties;

  @Nullable
  private final AndroidSdkHandler mySdkHandler;

  @NotNull
  private final ListeningExecutorService myEdtListeningExecutorService;

  @Nullable
  private AvdManager myAvdManager;

  private final @Nullable Path myAvdHomeFolder;

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

  public synchronized static @NotNull AvdManagerConnection getDefaultGradleAvdManagerConnection() {
    AndroidSdkHandler handler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    if (handler.getLocation() == null) {
      return NULL_CONNECTION;
    }
    return getGradleAvdManagerConnection(handler);
  }

  public synchronized static @NotNull AvdManagerConnection getGradleAvdManagerConnection(@NotNull AndroidSdkHandler handler) {
    Path sdkPath = handler.getLocation();
    return ourGradleAvdCache.computeIfAbsent(
      sdkPath, path -> {
        try {
          return ourConnectionFactory.apply(handler, AndroidLocationsSingleton.INSTANCE.getGradleAvdLocation());
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
   * Sets a factory to be used for creating connections, so subclasses can be injected for testing.
   */
  @VisibleForTesting
  protected synchronized static void setConnectionFactory(
    @NotNull BiFunction<@Nullable AndroidSdkHandler, @Nullable Path, @NotNull AvdManagerConnection> factory) {
    ourAvdCache.clear();
    ourGradleAvdCache.clear();
    ourConnectionFactory = factory;
  }

  @VisibleForTesting
  protected static void resetConnectionFactory() {
    setConnectionFactory(AvdManagerConnection::new);
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
      if (myAvdHomeFolder == null) {
        IJ_LOG.warn("No AVD Home Folder");
        return false;
      }
      try {
        myAvdManager = AvdManager.getInstance(
          mySdkHandler,
          myAvdHomeFolder,
          SDK_LOG);
      }
      catch (AndroidLocationsException e) {
        IJ_LOG.error(e);
        return false;
      }

      return myAvdManager != null;
    }
    return true;
  }

  @Nullable
  public String getSdCardSizeFromHardwareProperties() {
    assert mySdkHandler != null;
    return getHardwarePropertyDefaultValue(SD_CARD_STORAGE_KEY, mySdkHandler);
  }

  @Nullable
  public String getInternalStorageSizeFromHardwareProperties() {
    assert mySdkHandler != null;
    return getHardwarePropertyDefaultValue(INTERNAL_STORAGE_KEY, mySdkHandler);
  }

  /**
   * Get the default value of hardware property from hardware-properties.ini.
   *
   * @param name the name of the requested hardware property
   * @return the default value
   */
  @Nullable
  private String getHardwarePropertyDefaultValue(@NotNull String name, @Nullable AndroidSdkHandler sdkHandler) {
    if (ourHardwareProperties == null && sdkHandler != null) {
      // get the list of possible hardware properties
      // The file is in the emulator component
      LocalPackage emulatorPackage = sdkHandler.getLocalPackage(FD_EMULATOR, new StudioLoggerProgressIndicator(AvdManagerConnection.class));
      if (emulatorPackage != null) {
        Path hardwareDefs = emulatorPackage.getLocation().resolve(FD_LIB + File.separator + FN_HARDWARE_INI);
        ourHardwareProperties = HardwareProperties.parseHardwareDefinitions(
          new PathFileWrapper(hardwareDefs), new LogWrapper(Logger.getInstance(AvdManagerConnection.class)));
      }
    }
    HardwareProperties.HardwareProperty hwProp = (ourHardwareProperties == null) ? null : ourHardwareProperties.get(name);
    return (hwProp == null) ? null : hwProp.getDefault();
  }

  @Nullable
  private Path getBinaryLocation(@NotNull String filename) {
    assert mySdkHandler != null;
    LocalPackage sdkPackage = mySdkHandler.getLocalPackage(SdkConstants.FD_EMULATOR, REPO_LOG);
    if (sdkPackage == null) {
      return null;
    }
    Path binaryFile = sdkPackage.getLocation().resolve(filename);
    if (CancellableFileIo.notExists(binaryFile)) {
      return null;
    }
    return binaryFile;
  }

  @Nullable
  public Path getEmulatorBinary() {
    return getBinaryLocation(SdkConstants.FN_EMULATOR);
  }

  @Nullable
  public Path getEmulatorCheckBinary() {
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
      return SYSTEM_IMAGE_DEPENDENCY_WITH_25_0_2_RC3;
    }
    if (info.getVersion().compareTo(TOOLS_REVISION_WITH_FIRST_QEMU2) >= 0) {
      return SYSTEM_IMAGE_DEPENDENCY_WITH_FIRST_QEMU2;
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

    return info.getVersion().compareTo(PLATFORM_TOOLS_REVISION_WITH_FIRST_QEMU2) >= 0;
  }

  private boolean hasSystemImagesForQEMU2Installed() {
    return getSystemImageUpdates().isEmpty();
  }

  /**
   * The qemu2 emulator has changes in the system images for platform 22 and 23 (Intel CPU architecture only).
   * This method will generate package updates if we detect that we have outdated system images for platform
   * 22 and 23. We also check the addon system images which includes the Google API.
   *
   * @return a list of package paths that need to be updated.
   */
  @NotNull
  public List<String> getSystemImageUpdates() {
    List<String> requested = Lists.newArrayList();
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
  @Slow
  public List<AvdInfo> getAvds(boolean forceRefresh) {
    if (!initIfNecessary()) {
      return ImmutableList.of();
    }
    if (forceRefresh) {
      try {
        assert myAvdManager != null;
        myAvdManager.reloadAvds();
      }
      catch (AndroidLocationsException e) {
        IJ_LOG.error("Could not find Android SDK!", e);
      }
    }
    assert myAvdManager != null;
    ArrayList<AvdInfo> avds = Lists.newArrayList(myAvdManager.getAllAvds());
    boolean needsRefresh = false;
    for (AvdInfo avd : avds) {
      if (avd.getStatus() == AvdInfo.AvdStatus.ERROR_DEVICE_CHANGED) {
        updateDeviceChanged(avd);
        needsRefresh = true;
      }
    }
    if (needsRefresh) {
      return getAvds(true);
    }
    else {
      return avds;
    }
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

  public @NotNull ListenableFuture<@NotNull Boolean> isAvdRunningAsync(@NotNull AvdInfo info) {
    ListeningExecutorService service = MoreExecutors.listeningDecorator(AppExecutorUtil.getAppExecutorService());

    return service.submit(() -> isAvdRunning(info));
  }

  public final @NotNull ListenableFuture<@Nullable Void> stopAvdAsync(@NotNull AvdInfo avd) {
    // noinspection UnstableApiUsage
    return Futures.submit(() -> stopAvd(avd), AppExecutorUtil.getAppExecutorService());
  }

  @Slow
  public void stopAvd(@NotNull AvdInfo info) {
    assert myAvdManager != null;
    myAvdManager.stopAvd(info);
  }

  public @NotNull ListenableFuture<@NotNull IDevice> coldBoot(@NotNull Project project, @NotNull AvdInfo avd) {
    return startAvd(project, avd, ColdBootEmulatorCommandBuilder::new);
  }

  public @NotNull ListenableFuture<@NotNull IDevice> quickBoot(@NotNull Project project, @NotNull AvdInfo avd) {
    return startAvd(project, avd, EmulatorCommandBuilder::new);
  }

  public @NotNull ListenableFuture<@NotNull IDevice> bootWithSnapshot(@NotNull Project project,
                                                                      @NotNull AvdInfo avd,
                                                                      @NotNull String snapshot) {
    return startAvd(project, avd, (emulator, a) -> new BootWithSnapshotEmulatorCommandBuilder(emulator, a, snapshot));
  }

  public @NotNull ListenableFuture<@NotNull IDevice> startAvd(@Nullable Project project, @NotNull AvdInfo info) {
    return startAvd(project, info, new DefaultEmulatorCommandBuilderFactory());
  }

  public @NotNull ListenableFuture<@NotNull IDevice> startAvdWithColdBoot(@Nullable Project project, @NotNull AvdInfo info) {
    return startAvd(project, info, ColdBootNowEmulatorCommandBuilder::new);
  }

  public @NotNull ListenableFuture<@NotNull IDevice> startAvd(@Nullable Project project,
                                                              @NotNull AvdInfo info,
                                                              @NotNull EmulatorCommandBuilderFactory factory) {
    if (!initIfNecessary()) {
      return Futures.immediateFailedFuture(new RuntimeException("No Android SDK Found"));
    }
    assert mySdkHandler != null;

    String skinPath = info.getProperties().get(AVD_INI_SKIN_PATH);
    if (skinPath != null) {
      DeviceSkinUpdater.updateSkins(mySdkHandler.toCompatiblePath(skinPath), null);
    }

    // noinspection ConstantConditions, UnstableApiUsage
    return Futures.transformAsync(
      checkAccelerationAsync(),
      code -> continueToStartAvdIfAccelerationErrorIsNotBlocking(code, project, info, factory),
      MoreExecutors.directExecutor());
  }

  private @NotNull ListenableFuture<IDevice> continueToStartAvdIfAccelerationErrorIsNotBlocking(@NotNull AccelerationErrorCode code,
                                                                                                @Nullable Project project,
                                                                                                @NotNull AvdInfo info,
                                                                                                @NotNull EmulatorCommandBuilderFactory factory) {
    switch (code) {
      case ALREADY_INSTALLED:
        return continueToStartAvd(project, info, factory);
      case TOOLS_UPDATE_REQUIRED:
      case PLATFORM_TOOLS_UPDATE_ADVISED:
      case SYSTEM_IMAGE_UPDATE_ADVISED:
        // Launch the virtual device with possibly degraded performance even if there are updates
        // noinspection DuplicateBranchesInSwitch
        return continueToStartAvd(project, info, factory);
      case NO_EMULATOR_INSTALLED:
        return handleAccelerationError(project, info, code);
      default:
        Abi abi = Abi.getEnum(info.getAbiType());

        if (abi == null) {
          return continueToStartAvd(project, info, factory);
        }

        if (abi.equals(Abi.X86) || abi.equals(Abi.X86_64)) {
          return handleAccelerationError(project, info, code);
        }

        // Let ARM and MIPS virtual devices launch without hardware acceleration
        return continueToStartAvd(project, info, factory);
    }
  }

  private @NotNull ListenableFuture<IDevice> continueToStartAvd(@Nullable Project project,
                                                                @NotNull AvdInfo avd,
                                                                @NotNull EmulatorCommandBuilderFactory factory) {
    Path emulatorBinary = getEmulatorBinary();
    if (emulatorBinary == null) {
      IJ_LOG.error("No emulator binary found!");
      return Futures.immediateFailedFuture(new RuntimeException("No emulator binary found"));
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

    GeneralCommandLine commandLine = newEmulatorCommand(project, emulatorBinary, avd, factory);
    EmulatorRunner runner = new EmulatorRunner(commandLine, avd);

    ProcessHandler processHandler;
    try {
      processHandler = runner.start();
    }
    catch (ExecutionException e) {
      IJ_LOG.error("Error launching emulator", e);
      return Futures.immediateFailedFuture(new RuntimeException(String.format("Error launching emulator %1$s", avdName), e));
    }

    // If we're using qemu2, it has its own progress bar, so put ours in the background. Otherwise, show it.
    ProgressWindow p = hasQEMU2Installed()
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
    messageBus.syncPublisher(AvdLaunchListener.TOPIC).avdLaunched(avd, commandLine, project);

    return EmulatorConnectionListener.getDeviceForEmulator(project, avd.getName(), processHandler, 5, TimeUnit.MINUTES);
  }

  protected @NotNull GeneralCommandLine newEmulatorCommand(@Nullable Project project,
                                                           @NotNull Path emulator,
                                                           @NotNull AvdInfo avd,
                                                           @NotNull EmulatorCommandBuilderFactory factory) {
    ProgressIndicator indicator = new StudioLoggerProgressIndicator(AvdManagerConnection.class);
    ILogger logger = new LogWrapper(Logger.getInstance(AvdManagerConnection.class));
    Optional<Collection<String>> params = Optional.ofNullable(System.getenv("studio.emu.params")).map(Splitter.on(',')::splitToList);

    return factory.newEmulatorCommandBuilder(emulator, avd)
      .setAvdHome(myAvdManager.getBaseAvdFolder())
      .setEmulatorSupportsSnapshots(EmulatorAdvFeatures.emulatorSupportsFastBoot(mySdkHandler, indicator, logger))
      .setStudioParams(writeParameterFile().orElse(null))
      .setLaunchInToolWindow(shouldLaunchInToolWindow(project))
      .addAllStudioEmuParams(params.orElse(Collections.emptyList()))
      .build();
  }

  /**
   * Checks whether the emulator should launch in a tool window or standalone.
   */
  private static boolean shouldLaunchInToolWindow(@Nullable Project project) {
    return EmulatorSettings.getInstance().getLaunchInToolWindow() &&
           project != null && ToolWindowManager.getInstance(project).getToolWindow("Running Devices") != null;
  }

  public static boolean isFoldable(@NotNull AvdInfo avd) {
    String displayRegionWidth = avd.getProperty("hw.displayRegion.0.1.width");
    return displayRegionWidth != null && !"0".equals(displayRegionWidth);
  }

  /**
   * Indicates if the Emulator's version is at least {@code desired}
   *
   * @return true if the Emulator version is the desired version or higher
   */
  public boolean emulatorVersionIsAtLeast(@NotNull Revision desired) {
    if (mySdkHandler == null) return false; // Don't know, so guess
    ProgressIndicator log = new StudioLoggerProgressIndicator(AvdManagerConnection.class);
    LocalPackage sdkPackage = mySdkHandler.getLocalPackage(SdkConstants.FD_EMULATOR, log);
    if (sdkPackage == null) {
      return false;
    }
    return (sdkPackage.getVersion().compareTo(desired) >= 0);
  }

  /**
   * Write HTTP Proxy information to a temporary file.
   */
  private @NotNull Optional<@NotNull Path> writeParameterFile() {
    if (!emulatorVersionIsAtLeast(EMULATOR_REVISION_SUPPORTS_STUDIO_PARAMS)) {
      // Older versions of the emulator don't accept this information.
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
   * Create a directory under $ANDROID_SDK_ROOT where we can write
   * temporary files.
   *
   * @return The directory file. This will be null if we
   * could not find, create, or write the directory.
   */
  @Nullable
  public static File tempFileDirectory() {
    // Create a temporary file in /temp under $ANDROID_SDK_ROOT.
    String androidSdkRootValue = System.getenv(ANDROID_SDK_ROOT_ENV);
    if (androidSdkRootValue == null) {
      // Fall back to the user's home directory
      androidSdkRootValue = System.getProperty("user.home");
    }
    File tempDir = new File(androidSdkRootValue, "temp");
    //noinspection ResultOfMethodCallIgnored
    tempDir.mkdirs(); // Create if necessary
    if (!tempDir.exists()) {
      return null; // Give up
    }
    return tempDir;
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
      File tempDir = tempFileDirectory();
      if (tempDir == null) {
        return null; // Fail
      }
      tempFile = FileUtil.createTempFile(tempDir, "emu", ".tmp", true);
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
                                                            @NotNull AccelerationErrorCode code) {
    if (code.getSolution().equals(SolutionCode.NONE)) {
      return Futures.immediateFailedFuture(new RuntimeException(code.getProblem() + "\n\n" + code.getSolutionMessage() + '\n'));
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
        Messages.getOkButton(),
        Messages.getCancelButton(),
        AllIcons.General.WarningDialog);
    });
  }

  @NotNull
  private ListenableFuture<IDevice> tryFixingAccelerationError(int result,
                                                               @Nullable Project project,
                                                               @NotNull AvdInfo info,
                                                               @NotNull AccelerationErrorCode code) {
    if (result == Messages.CANCEL) {
      return Futures.immediateFailedFuture(new RuntimeException("Could not start AVD"));
    }

    SettableFuture<IDevice> future = SettableFuture.create();

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
    Path emulatorBinary = getEmulatorBinary();
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
    Path checkBinary = getEmulatorCheckBinary();
    if (checkBinary != null) {
      commandLine.setExePath(checkBinary.toString());
      commandLine.addParameter("accel");
    }
    else {
      commandLine.setExePath(emulatorBinary.toString());
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
                                   @Nullable Path skinFolder,
                                   @NotNull Map<String, String> hardwareProperties,
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
    String skinName = null;

    if (skinFolder == null && isCircular) {
      File skin = getRoundSkin(systemImageDescription);
      skinFolder = skin == null ? null : mySdkHandler.toCompatiblePath(skin);
    }
    if (skinFolder != null && skinFolder.toString().equals(SkinUtils.NO_SKIN)) {
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
    if (device.getId().equals(("7.6in Foldable"))) {
      hardwareProperties.put(AVD_INI_HINGE, "yes");
      hardwareProperties.put(AVD_INI_HINGE_COUNT, "1");
      hardwareProperties.put(AVD_INI_HINGE_TYPE, "1");
      hardwareProperties.put(AVD_INI_HINGE_SUB_TYPE, "1");
      hardwareProperties.put(AVD_INI_HINGE_RANGES, "0-180");
      hardwareProperties.put(AVD_INI_HINGE_DEFAULTS, "180");
      hardwareProperties.put(AVD_INI_HINGE_AREAS, "884-0-1-2208");
      hardwareProperties.put(AVD_INI_POSTURE_LISTS, "1,2,3");
      hardwareProperties.put(AVD_INI_HINGE_ANGLES_POSTURE_DEFINITIONS, "0-30, 30-150, 150-180");
    }
    if (device.getId().equals(("8in Foldable"))) {
      hardwareProperties.put(AVD_INI_HINGE, "yes");
      hardwareProperties.put(AVD_INI_HINGE_COUNT, "1");
      hardwareProperties.put(AVD_INI_HINGE_TYPE, "1");
      hardwareProperties.put(AVD_INI_HINGE_SUB_TYPE, "1");
      hardwareProperties.put(AVD_INI_HINGE_RANGES, "180-360");
      hardwareProperties.put(AVD_INI_HINGE_DEFAULTS, "180");
      hardwareProperties.put(AVD_INI_HINGE_AREAS, "1148-0-1-2480");
      hardwareProperties.put(AVD_INI_FOLD_AT_POSTURE, "4");
      hardwareProperties.put(AVD_INI_POSTURE_LISTS, "3, 4");
      hardwareProperties.put(AVD_INI_HINGE_ANGLES_POSTURE_DEFINITIONS, "180-330, 330-360");
    }
    if (device.getId().equals(("6.7in Foldable"))) {
      hardwareProperties.put(AVD_INI_HINGE, "yes");
      hardwareProperties.put(AVD_INI_HINGE_COUNT, "1");
      hardwareProperties.put(AVD_INI_HINGE_TYPE, "0");
      hardwareProperties.put(AVD_INI_HINGE_SUB_TYPE, "1");
      hardwareProperties.put(AVD_INI_HINGE_RANGES, "0-180");
      hardwareProperties.put(AVD_INI_HINGE_DEFAULTS, "180");
      hardwareProperties.put(AVD_INI_HINGE_AREAS, "0-1318-1080-1");
      hardwareProperties.put(AVD_INI_POSTURE_LISTS, "1, 2, 3");
      hardwareProperties.put(AVD_INI_HINGE_ANGLES_POSTURE_DEFINITIONS, "0-30, 30-150, 150-180");
    }
    if (device.getId().equals("7.4in Rollable")) {
      hardwareProperties.put(AVD_INI_ROLL, "yes");
      hardwareProperties.put(AVD_INI_ROLL_COUNT, "1");
      hardwareProperties.put(AVD_INI_HINGE_TYPE, "3");
      hardwareProperties.put(AVD_INI_ROLL_RANGES, "58.55-100");
      hardwareProperties.put(AVD_INI_ROLL_DEFAULTS, "67.5");
      hardwareProperties.put(AVD_INI_ROLL_RADIUS, "3");
      hardwareProperties.put(AVD_INI_ROLL_DIRECTION, "1");
      hardwareProperties.put(AVD_INI_ROLL_RESIZE_1_AT_POSTURE, "1");
      hardwareProperties.put(AVD_INI_ROLL_RESIZE_2_AT_POSTURE, "2");
      hardwareProperties.put(AVD_INI_POSTURE_LISTS, "1, 2, 3");
      hardwareProperties.put(AVD_INI_ROLL_PERCENTAGES_POSTURE_DEFINITIONS, "58.55-76.45, 76.45-94.35, 94.35-100");
    }
    if (device.getId().equals("resizable")) {
      hardwareProperties.put(AVD_INI_RESIZABLE_CONFIG, "phone-0-1080-2340-420, foldable-1-1768-2208-420, tablet-2-1920-1200-240, desktop-3-1920-1080-160");
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
                                  skinFolder,
                                  skinName,
                                  sdCard,
                                  hardwareProperties,
                                  device.getBootProps(),
                                  device.hasPlayStore(),
                                  false,
                                  removePrevious);
  }

  @Nullable
  private File getRoundSkin(SystemImageDescription systemImageDescription) {
    Path[] skins = systemImageDescription.getSkins();
    for (Path skin : skins) {
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

  public static boolean isAvdRepairable(@NotNull AvdInfo.AvdStatus avdStatus) {
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
    String imageSystemDir = avdInfo.getProperties().get(AvdManager.AVD_INI_IMAGES_1);
    if (imageSystemDir == null) {
      return null;
    }
    return StringUtil.trimEnd(imageSystemDir.replace(File.separatorChar, RepoPackage.PATH_SEPARATOR), RepoPackage.PATH_SEPARATOR);
  }

  public void updateDeviceChanged(@NotNull AvdInfo avdInfo) {
    if (initIfNecessary()) {
      try {
        assert myAvdManager != null;
        myAvdManager.updateDeviceChanged(avdInfo);
      }
      catch (IOException e) {
        IJ_LOG.warn("Could not update AVD Device " + avdInfo.getName(), e);
      }
    }
  }

  public boolean wipeUserData(@NotNull AvdInfo avdInfo) {
    if (!initIfNecessary()) {
      return false;
    }
    assert mySdkHandler != null;
    // Delete the current user data file
    Path path = avdInfo.getDataFolderPath().resolve(AvdManager.USERDATA_QEMU_IMG);
    if (Files.exists(path)) {
      if (!FileOpUtils.deleteFileOrFolder(path)) {
        return false;
      }
    }
    // Delete the snapshots directory
    Path snapshotDirectory = avdInfo.getDataFolderPath().resolve(AvdManager.SNAPSHOTS_DIRECTORY);
    FileOpUtils.deleteFileOrFolder(snapshotDirectory);

    return true;
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

    SystemImageUpdateDependency(int featureLevel, @NotNull IdDisplay tag, int requiredMajorRevision) {
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

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

import com.android.SdkConstants;
import com.android.prefs.AndroidLocation;
import com.android.repository.Revision;
import com.android.resources.Density;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.SystemImage;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.tools.idea.run.ExternalToolRunner;
import com.android.tools.idea.sdk.LogWrapper;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * A wrapper class for communicating with {@link AvdManager} and exposing helper functions
 * for dealing with {@link AvdInfo} objects inside Android studio.
 */
public class AvdManagerConnection {
  private static final Logger IJ_LOG = Logger.getInstance(AvdManagerConnection.class);
  private static final ILogger SDK_LOG = new LogWrapper(IJ_LOG);
  private static final AvdManagerConnection NULL_CONNECTION = new AvdManagerConnection(null);
  private static final int MNC_API_LEVEL_23 = 23;
  private static final int LMP_MR1_API_LEVEL_22 = 22;
  private static final int MNC_AOSP_MIN_REVISION = 6;
  private static final int MNC_GAPI_MIN_REVISION = 10;
  private static final int LMP_AOSP_MIN_REVISION = 2;
  private static final int LMP_GAPI_MIN_REVISION = 2;

  public static final String AVD_INI_HW_LCD_DENSITY = "hw.lcd.density";
  public static final String AVD_INI_DISPLAY_NAME = "avd.ini.displayname";
  public static final IdDisplay GOOGLE_APIS_TAG = new com.android.sdklib.repository.descriptors.IdDisplay("google_apis", "");
  public static final Revision TOOLS_REVISION_WITH_FIRST_QEMU2 = Revision.parseRevision("25.0.0");
  public static final Revision PLATFORM_TOOLS_REVISION_WITH_FIRST_QEMU2 = Revision.parseRevision("23.1.0");

  private AvdManager ourAvdManager;
  private Map<File, SkinLayoutDefinition> ourSkinLayoutDefinitions = Maps.newHashMap();
  private static Map<LocalSdk, AvdManagerConnection> ourCache = new WeakHashMap<LocalSdk, AvdManagerConnection>();
  private static long ourMemorySize = -1;

  @Nullable private final LocalSdk myLocalSdk;

  @NotNull
  public static AvdManagerConnection getDefaultAvdManagerConnection() {
      AndroidSdkData androidSdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
      LocalSdk localSdk = null;
      if (androidSdkData != null) {
        localSdk = androidSdkData.getLocalSdk();
      }
    if (localSdk == null) {
      return NULL_CONNECTION;
    }
    else {
      return getAvdManagerConnection(localSdk);
    }
  }

  @NotNull
  public synchronized static AvdManagerConnection getAvdManagerConnection(@NotNull LocalSdk localSdk) {
    if (!ourCache.containsKey(localSdk)) {
      ourCache.put(localSdk, new AvdManagerConnection(localSdk));
    }
    return ourCache.get(localSdk);
  }

  private AvdManagerConnection(@Nullable LocalSdk localSdk) {
    myLocalSdk = localSdk;
  }

  /**
   * Setup our static instances if required. If the instance already exists, then this is a no-op.
   */
  private boolean initIfNecessary() {
    if (ourAvdManager == null) {
      if (myLocalSdk == null) {
        IJ_LOG.error("No Android SDK Found");
        return false;
      }
      try {
        ourAvdManager = AvdManager.getInstance(myLocalSdk, SDK_LOG);
      }
      catch (AndroidLocation.AndroidLocationException e) {
        IJ_LOG.error("Could not instantiate AVD Manager from SDK", e);
        return false;
      }
    }
    return true;
  }

  private File getEmulatorBinary() {
    assert ourAvdManager != null;
    return new File(ourAvdManager.getLocalSdk().getLocation(), FileUtil.join(SdkConstants.OS_SDK_TOOLS_FOLDER, SdkConstants.FN_EMULATOR));
  }

  private boolean hasQEMU2Installed() {
    assert myLocalSdk != null;
    LocalPkgInfo info = myLocalSdk.getPkgInfo(PkgType.PKG_TOOLS);
    if (info == null) {
      return false;
    }
    return info.getDesc().getRevision().compareTo(TOOLS_REVISION_WITH_FIRST_QEMU2) >= 0;
  }

  private boolean hasPlatformToolsForQEMU2Installed() {
    assert myLocalSdk != null;
    LocalPkgInfo[] infos = myLocalSdk.getPkgsInfos(PkgType.PKG_PLATFORM_TOOLS);
    if (infos.length == 0) {
      return false;
    }
    for (LocalPkgInfo info : infos) {
      if (info.getDesc().getRevision().compareTo(PLATFORM_TOOLS_REVISION_WITH_FIRST_QEMU2) >= 0) {
        return true;
      }
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
   * @return a list of packages that need to be updated.
   */
  @NotNull
  public List<IPkgDesc> getSystemImageUpdates() {
    List<IPkgDesc> requested = Lists.newArrayList();

    assert myLocalSdk != null;
    LocalPkgInfo[] infos = myLocalSdk.getPkgsInfos(PkgType.PKG_SYS_IMAGE);
    for (LocalPkgInfo info : infos) {
      IPkgDesc desc = info.getDesc();
      Abi abi = desc.getPath() != null ? Abi.getEnum(desc.getPath()) : Abi.ARMEABI;
      boolean isAvdIntel = abi == Abi.X86 || abi == Abi.X86_64;

      if (isAvdIntel &&
          desc.getAndroidVersion() != null &&
          desc.getAndroidVersion().getApiLevel() == LMP_MR1_API_LEVEL_22 &&
          SystemImage.DEFAULT_TAG.equals(desc.getTag()) &&
          desc.getRevision().getMajor() < LMP_AOSP_MIN_REVISION) {
        requested.add(
          PkgDesc.Builder.newSysImg(desc.getAndroidVersion(), desc.getTag(), abi.toString(), new Revision(LMP_AOSP_MIN_REVISION)).create());
      }

      if (isAvdIntel &&
          desc.getAndroidVersion() != null &&
          desc.getAndroidVersion().getApiLevel() == MNC_API_LEVEL_23 &&
          SystemImage.DEFAULT_TAG.equals(desc.getTag()) &&
          desc.getRevision().getMajor() < MNC_AOSP_MIN_REVISION) {
        requested.add(
          PkgDesc.Builder.newSysImg(desc.getAndroidVersion(), desc.getTag(), abi.toString(), new Revision(MNC_AOSP_MIN_REVISION)).create());
      }
    }

    infos = myLocalSdk.getPkgsInfos(PkgType.PKG_ADDON_SYS_IMAGE);
    for (LocalPkgInfo info : infos) {
      IPkgDesc desc = info.getDesc();
      Abi abi = desc.getPath() != null ? Abi.getEnum(desc.getPath()) : Abi.ARMEABI;
      boolean isAvdIntel = abi == Abi.X86 || abi == Abi.X86_64;

      if (isAvdIntel &&
          desc.getAndroidVersion() != null &&
          desc.getAndroidVersion().getApiLevel() == LMP_MR1_API_LEVEL_22 &&
          GOOGLE_APIS_TAG.equals(desc.getTag()) &&
          desc.getRevision().getMajor() < LMP_GAPI_MIN_REVISION &&
          desc.getVendor() != null) {
        requested.add(PkgDesc.Builder.newAddonSysImg(
          desc.getAndroidVersion(), desc.getVendor(), desc.getTag(), abi.toString(), new Revision(LMP_GAPI_MIN_REVISION)).create());
      }
      if (isAvdIntel &&
          desc.getAndroidVersion() != null &&
          desc.getAndroidVersion().getApiLevel() == MNC_API_LEVEL_23 &&
          GOOGLE_APIS_TAG.equals(desc.getTag()) &&
          desc.getRevision().getMajor() < MNC_GAPI_MIN_REVISION &&
          desc.getVendor() != null) {
        requested.add(PkgDesc.Builder.newAddonSysImg(
          desc.getAndroidVersion(), desc.getVendor(), desc.getTag(), desc.getPath(), new Revision(MNC_GAPI_MIN_REVISION)).create());
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
        ourAvdManager.reloadAvds(SDK_LOG);
      }
      catch (AndroidLocation.AndroidLocationException e) {
        IJ_LOG.error("Could not find Android SDK!", e);
      }
    }
    ArrayList<AvdInfo> avdInfos = Lists.newArrayList(ourAvdManager.getAllAvds());
    boolean needsRefresh = false;
    for (AvdInfo info : avdInfos) {
      if (info.getStatus() == AvdInfo.AvdStatus.ERROR_IMAGE_DIR) {
        updateAvdImageFolder(info);
        needsRefresh = true;
      } else if (info.getStatus() == AvdInfo.AvdStatus.ERROR_DEVICE_CHANGED) {
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

  /**
   * @return a Dimension object representing the screen size of the given AVD in pixels or null if
   * the AVD does not define a resolution.
   */
  @Nullable
  public Dimension getAvdResolution(@NotNull AvdInfo info) {
    if (!initIfNecessary()) {
      return null;
    }
    Map<String, String> properties = info.getProperties();
    String skin = properties.get(AvdManager.AVD_INI_SKIN_NAME);
    if (skin != null) {
      Matcher m = AvdManager.NUMERIC_SKIN_SIZE.matcher(skin);
      if (m.matches()) {
        int size1 = Integer.parseInt(m.group(1));
        int size2 = Integer.parseInt(m.group(2));
        return new Dimension(size1, size2);
      }
    }
    skin = properties.get(AvdManager.AVD_INI_SKIN_PATH);
    if (skin != null) {
      File skinPath = new File(skin);
      File skinDir;
      if (skinPath.isAbsolute()) {
        skinDir = skinPath;
      } else {
        skinDir = new File(ourAvdManager.getLocalSdk().getLocation(), skin);
      }
      if (skinDir.isDirectory()) {
        File layoutFile = new File(skinDir, "layout");
        if (layoutFile.isFile()) {
          return getResolutionFromLayoutFile(layoutFile);
        }
      }
    }
    return null;
  }

  /**
   * Read the resolution from a layout definition file. See {@link SkinLayoutDefinition} for details on the format
   * of that file.
   */
  @Nullable
  protected Dimension getResolutionFromLayoutFile(@NotNull File layoutFile) {
    if (!ourSkinLayoutDefinitions.containsKey(layoutFile)) {
      ourSkinLayoutDefinitions.put(layoutFile, SkinLayoutDefinition.parseFile(layoutFile));
    }
    SkinLayoutDefinition layoutDefinition = ourSkinLayoutDefinitions.get(layoutFile);
    if (layoutDefinition != null) {
      String heightString = layoutDefinition.get("parts.device.display.height");
      String widthString = layoutDefinition.get("parts.device.display.width");
      if (widthString == null || heightString == null) {
        return null;
      }
      int height = Integer.parseInt(heightString);
      int width = Integer.parseInt(widthString);
      return new Dimension(width, height);
    }
    return null;
  }

  /**
   * @return A string representing the AVD's screen density. One of ["ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi"]
   */
  @Nullable
  public static Density getAvdDensity(@NotNull AvdInfo info) {
    Map<String, String> properties = info.getProperties();
    String densityString = properties.get(AVD_INI_HW_LCD_DENSITY);
    if (densityString != null) {
      int density = Integer.parseInt(densityString);
      Density[] knownDensities = Density.values();
      // Densities are declared high to low
      int i = 0;
      while (density < knownDensities[i].getDpiValue()) {
        i++;
      }
      if (i < knownDensities.length) {
        return knownDensities[i];
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  /**
   * Delete the given AVD if it exists.
   */
  public void deleteAvd(@NotNull AvdInfo info) {
    if (!initIfNecessary()) {
      return;
    }
    ourAvdManager.deleteAvd(info, SDK_LOG);
  }

  public boolean isAvdRunning(@NotNull AvdInfo info) {
    return ourAvdManager.isAvdRunning(info, SDK_LOG);
  }


  public void stopAvd(@NotNull final AvdInfo info) {
    ourAvdManager.stopAvd(info);
  }

  /**
   * Launch the given AVD in the emulator.
   * @return the {@link ProcessHandler} corresponding to the running emulator if the emulator process was launched, null otherwise
   */
  @Nullable
  public ProcessHandler startAvd(@Nullable final Project project, @NotNull final AvdInfo info) {
    if (!initIfNecessary()) {
      return null;
    }
    final File emulatorBinary = getEmulatorBinary();
    if (!emulatorBinary.isFile()) {
      IJ_LOG.error("No emulator binary found!");
      return null;
    }

    final String avdName = info.getName();

    // TODO: The emulator stores pid of the running process inside the .lock file (userdata-qemu.img.lock in Linux and
    // userdata-qemu.img.lock/pid on Windows). We should detect whether those lock files are stale and if so, delete them without showing
    // this error. Either the emulator provides a command to do that, or we learn about its internals (qemu/android/utils/filelock.c) and
    // perform the same action here. If it is not stale, then we should show this error and if possible, bring that window to the front.
    if (ourAvdManager.isAvdRunning(info, SDK_LOG)) {
      String baseFolder;
      try {
        baseFolder = ourAvdManager.getBaseAvdFolder();
      }
      catch (AndroidLocation.AndroidLocationException e) {
        baseFolder = "$HOME";
      }

      String message = String.format("AVD %1$s is already running.\n" +
                                     "If that is not the case, delete the files at\n" +
                                     "   %2$s/%1$s.avd/*.lock\n" +
                                     "and try again.", avdName, baseFolder);
      Messages.showErrorDialog(project, message, "AVD Manager");
      return null;
    }

    Map<String, String> properties = info.getProperties();
    String scaleFactor = properties.get(AvdWizardConstants.AVD_INI_SCALE_FACTOR);
    String netDelay = properties.get(AvdWizardConstants.AVD_INI_NETWORK_LATENCY);
    String netSpeed = properties.get(AvdWizardConstants.AVD_INI_NETWORK_SPEED);

    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(emulatorBinary.getPath());

    // Don't explicitly set auto since that seems to be the default behavior, but when set
    // can cause the emulator to fail to launch with this error message:
    //  "could not get monitor DPI resolution from system. please use -dpi-monitor to specify one"
    // (this happens on OSX where we don't have a reliable, Retina-correct way to get the dpi)
    if (scaleFactor != null && !"auto".equals(scaleFactor)) {
      commandLine.addParameters("-scale", scaleFactor);
    }

    if (netDelay != null) {
      commandLine.addParameters("-netdelay", netDelay);
    }

    if (netSpeed != null) {
      commandLine.addParameters("-netspeed", netSpeed);
    }

    commandLine.addParameters("-avd", avdName);

    EmulatorRunner runner = new EmulatorRunner(project, "AVD: " + avdName, commandLine, info);
    final ProcessHandler processHandler;
    try {
      processHandler = runner.start();
    }
    catch (ExecutionException e) {
      IJ_LOG.error("Error launching emulator", e);
      return null;
    }

    // If we're using qemu2, it has its own progress bar, so put ours in the background. Otherwise show it.
    final ProgressWindow p = hasQEMU2Installed()
                             ? new BackgroundableProcessIndicator(project, "Launching Emulator", PerformInBackgroundOption.ALWAYS_BACKGROUND,
                                                                  "", "", false)
                             : new ProgressWindow(false, true, project);
    p.setIndeterminate(false);
    p.setDelayInMillis(0);

    // It takes >= 8 seconds to start the Emulator. Display a small progress indicator otherwise it seems like
    // the action wasn't invoked and users tend to click multiple times on it, ending up with several instances of the emulator
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        ExternalToolRunner.ProcessOutputCollector collector = new ExternalToolRunner.ProcessOutputCollector();
        processHandler.addProcessListener(collector);

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

        processHandler.removeProcessListener(collector);
        final String message = collector.getText();

        if (message.toLowerCase().contains("error") || processHandler.isProcessTerminated() && !message.trim().isEmpty()) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              Messages.showErrorDialog(project, "Cannot launch AVD in emulator.\nOutput:\n" + message, avdName);
            }
          });
        }
      }
    });

    return processHandler;
  }

  /**
   * Run "emulator -accel-check" to check the status for emulator acceleration on this machine.
   * Return a {@link AccelerationErrorCode}.
   */
  public AccelerationErrorCode checkAcceration() {
    if (!initIfNecessary()) {
      return AccelerationErrorCode.UNKNOWN_ERROR;
    }
    File emulatorBinary = getEmulatorBinary();
    if (!emulatorBinary.isFile()) {
      return AccelerationErrorCode.NO_EMULATOR_INSTALLED;
    }
    if (getMemorySize() < Storage.Unit.GiB.getNumberOfBytes()) {
      // TODO: The emulator -accel-check current does not check for the available memory, do it here instead:
      return AccelerationErrorCode.NOT_ENOUGH_MEMORY;
    }
    if (!hasQEMU2Installed()) {
      // TODO: Return this error when the new emulator has been released.
      // return AccelerationErrorCode.TOOLS_UPDATE_REQUIRED;
      // TODO: For now just ignore the rest of the checks
      return AccelerationErrorCode.ALREADY_INSTALLED;
    }
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(emulatorBinary.getPath());
    commandLine.addParameter("-accel-check");
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
                                          boolean createSnapshot) {
    if (!initIfNecessary()) {
      return null;
    }

    File avdFolder;
    try {
      if (currentInfo != null) {
        avdFolder = new File(currentInfo.getDataFolderPath());
      } else {
        avdFolder = AvdInfo.getDefaultAvdFolder(ourAvdManager, avdName, true);
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
    if (FileUtil.filesEqual(skinFolder, AvdWizardConstants.NO_SKIN)) {
      skinFolder = null;
      hardwareProperties.remove(AvdManager.AVD_INI_SKIN_PATH);
    }
    if (skinFolder == null) {
      skinName = String.format("%dx%d", Math.round(resolution.getWidth()), Math.round(resolution.getHeight()));
    }
    if (orientation == ScreenOrientation.LANDSCAPE) {
      hardwareProperties.put(HardwareProperties.HW_INITIAL_ORIENTATION, ScreenOrientation.LANDSCAPE.getShortDisplayValue().toLowerCase());
    }
    if (currentInfo != null && !avdName.equals(currentInfo.getName())) {
      boolean success = ourAvdManager.moveAvd(currentInfo, avdName, currentInfo.getDataFolderPath(), SDK_LOG);
      if (!success) {
        return null;
      }
    }
    return ourAvdManager.createAvd(avdFolder,
                                   avdName,
                                   systemImageDescription.getTarget(),
                                   systemImageDescription.getTag(),
                                   systemImageDescription.getAbiType(),
                                   skinFolder,
                                   skinName,
                                   sdCard,
                                   hardwareProperties,
                                   device.getBootProps(),
                                   createSnapshot,
                                   false, // Remove Previous
                                   currentInfo != null, // edit existing
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

  public static boolean doesSystemImageSupportRanchu(SystemImageDescription description) {
    return doesSystemImageSupportRanchu(description.getVersion(), description.getTag(), description.getRevision());
  }

  private static boolean doesSystemImageSupportRanchu(@NotNull AvdInfo info) {
    IAndroidTarget target = info.getTarget();
    assert target != null;
    ISystemImage systemImage = target.getSystemImage(info.getTag(), info.getAbiType());
    assert systemImage != null;
    return doesSystemImageSupportRanchu(target.getVersion(), info.getTag(), systemImage.getRevision());
  }

  @Contract("null, _ -> false")
  private static boolean doesSystemImageSupportRanchu(@Nullable AndroidVersion version,
                                                      @NotNull IdDisplay tag,
                                                      @Nullable Revision revision) {
    if (version == null || revision == null) {
      return false;
    }
    int apiLevel = version.getApiLevel();
    if (apiLevel < LMP_MR1_API_LEVEL_22) {
      return false;
    }
    else if (apiLevel == LMP_MR1_API_LEVEL_22) {
      int minRevision = GOOGLE_APIS_TAG.equals(tag) ? LMP_GAPI_MIN_REVISION : LMP_AOSP_MIN_REVISION;
      return revision.getMajor() >= minRevision;
    }
    else if (apiLevel == MNC_API_LEVEL_23) {
      int minRevision = GOOGLE_APIS_TAG.equals(tag) ? MNC_GAPI_MIN_REVISION : MNC_AOSP_MIN_REVISION;
      return revision.getMajor() >= minRevision;
    }
    else {
      return true;
    }
  }

  public boolean avdExists(String candidate) {
    if (!initIfNecessary()) {
      return false;
    }
    return ourAvdManager.getAvd(candidate, false) != null;
  }

  static boolean isAvdRepairable(AvdInfo.AvdStatus avdStatus) {
    return avdStatus == AvdInfo.AvdStatus.ERROR_IMAGE_DIR
           || avdStatus == AvdInfo.AvdStatus.ERROR_DEVICE_CHANGED
           || avdStatus == AvdInfo.AvdStatus.ERROR_DEVICE_MISSING
           || avdStatus == AvdInfo.AvdStatus.ERROR_IMAGE_MISSING;
  }

  public boolean updateAvdImageFolder(@NotNull AvdInfo avdInfo) {
    if (initIfNecessary()) {
      try {
        ourAvdManager.updateAvd(avdInfo, SDK_LOG);
        return true;
      }
      catch (IOException e) {
        IJ_LOG.error("Could not update AVD " + avdInfo.getName(), e);
      }
    }
    return false;
  }

  public boolean updateDeviceChanged(@NotNull AvdInfo avdInfo) {
    if (initIfNecessary()) {
      try {
        ourAvdManager.updateDeviceChanged(avdInfo, SDK_LOG);
        return true;
      }
      catch (IOException e) {
        IJ_LOG.error("Could not update AVD Device " + avdInfo.getName(), e);
      }
    }
    return false;
  }

  public boolean wipeUserData(@NotNull AvdInfo avdInfo) {
    if (initIfNecessary()) {
      File userdataImage = new File(avdInfo.getDataFolderPath(), "userdata-qemu.img");
      if (userdataImage.isFile()) {
        return userdataImage.delete();
      }
      return true;
    }
    return false;
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
      result = String.format("%1$s %2$d", name, ++suffix);
    }
    return result;
  }

  public boolean findAvdWithName(String name) {
    for (AvdInfo avd : getDefaultAvdManagerConnection().getAvds(false)) {
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
    catch (ClassNotFoundException e) {
      // Unsupported JDK
    }
    catch (NoSuchMethodException e) {
      // Unsupported JDK
    }
    catch (InvocationTargetException e) {
      IJ_LOG.error(e); // Shouldn't happen (unsupported JDK?)
    }
    catch (IllegalAccessException e) {
      IJ_LOG.error(e); // Shouldn't happen (unsupported JDK?)
    }
    // Maximum memory allocatable to emulator - 32G. Only used if non-Oracle JRE.
    return 32L * Storage.Unit.GiB.getNumberOfBytes();
  }
}

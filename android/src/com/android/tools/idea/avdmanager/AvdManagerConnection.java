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
import com.android.resources.Density;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.tools.idea.run.ExternalToolRunner;
import com.android.tools.idea.sdk.LogWrapper;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static com.android.tools.idea.avdmanager.AvdWizardConstants.NO_SKIN;

/**
 * A wrapper class for communicating with {@link com.android.sdklib.internal.avd.AvdManager} and exposing helper functions
 * for dealing with {@link AvdInfo} objects inside Android studio.
 */
public class AvdManagerConnection {
  private static final Logger IJ_LOG = Logger.getInstance(AvdManagerConnection.class);
  private static final ILogger SDK_LOG = new LogWrapper(IJ_LOG);
  public static final String AVD_INI_HW_LCD_DENSITY = "hw.lcd.density";
  public static final String AVD_INI_DISPLAY_NAME = "avd.ini.displayname";
  private static final AvdManagerConnection NULL_CONNECTION = new AvdManagerConnection(null);

  private AvdManager ourAvdManager;
  private Map<File, SkinLayoutDefinition> ourSkinLayoutDefinitions = Maps.newHashMap();
  private File ourEmulatorBinary;
  private static Map<LocalSdk, AvdManagerConnection> ourCache = new WeakHashMap<LocalSdk, AvdManagerConnection>();
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
      ourEmulatorBinary =
        new File(ourAvdManager.getLocalSdk().getLocation(), FileUtil.join(SdkConstants.OS_SDK_TOOLS_FOLDER, SdkConstants.FN_EMULATOR));
      if (!ourEmulatorBinary.isFile()) {
        IJ_LOG.error("No emulator binary found!");
        return false;
      }
    }
    return true;
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
   */
  public void startAvd(@Nullable final Project project, @NotNull final AvdInfo info) {
    if (!initIfNecessary()) {
      return;
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
      return;
    }

    Map<String, String> properties = info.getProperties();
    final String scaleFactor = properties.get(AvdWizardConstants.AVD_INI_SCALE_FACTOR);
    final String netDelay = properties.get(AvdWizardConstants.AVD_INI_NETWORK_LATENCY);
    final String netSpeed = properties.get(AvdWizardConstants.AVD_INI_NETWORK_SPEED);

    final ProgressWindow p = new ProgressWindow(false, true, project);
    p.setIndeterminate(false);
    p.setDelayInMillis(0);

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.setExePath(ourEmulatorBinary.getPath());

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
        ProcessHandler processHandler;
        try {
          processHandler = runner.start();
        }
        catch (ExecutionException e) {
          IJ_LOG.error("Error launching emulator", e);
          return;
        }

        ExternalToolRunner.ProcessOutputCollector collector = new ExternalToolRunner.ProcessOutputCollector();
        processHandler.addProcessListener(collector);

        // It takes >= 8 seconds to start the Emulator. Display a small
        // progress indicator otherwise it seems like the action wasn't invoked and users tend
        // to click multiple times on it, ending up with several instances of the manager
        // window.
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
    if (FileUtil.filesEqual(skinFolder, NO_SKIN)) {
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
}

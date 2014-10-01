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
import com.android.sdklib.ISystemImage;
import com.android.sdklib.devices.Device;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.local.LocalSdk;
import com.android.utils.ILogger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ExecutionStatus;
import org.jetbrains.android.util.StringBuildingOutputProcessor;
import org.jetbrains.android.util.WaitingStrategies;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * A wrapper class for communicating with {@link com.android.sdklib.internal.avd.AvdManager} and exposing helper functions
 * for dealing with {@link AvdInfo} objects inside Android studio.
 */
public class AvdManagerConnection {
  private static final Logger IJ_LOG = Logger.getInstance(AvdManagerConnection.class);
  private static final ILogger SDK_LOG = new LogWrapper(IJ_LOG) {
    @Override
    public void error(Throwable t, String errorFormat, Object... args) {
      IJ_LOG.error(String.format(errorFormat, args), t);
    }
  };
  private static final String AVD_INI_HW_LCD_DENSITY = "hw.lcd.density";
  private static AvdManager ourAvdManager;
  private static Map<File, SkinLayoutDefinition> ourSkinLayoutDefinitions = Maps.newHashMap();
  private static File ourEmulatorBinary;

  /**
   * Setup our static instances if required. If the instance already exists, then this is a no-op.
   */
  private static boolean initIfNecessary() {
    if (ourAvdManager == null) {
      AndroidSdkData androidSdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
      if (androidSdkData == null) {
        IJ_LOG.error("No Android SDK Found");
        return false;
      }
      LocalSdk localSdk = androidSdkData.getLocalSdk();
      try {
        ourAvdManager = AvdManager.getInstance(localSdk, SDK_LOG);
      }
      catch (AndroidLocation.AndroidLocationException e) {
        IJ_LOG.error("Could not instantiate AVD Manager from SDK", e);
        return false;
      }
      ourEmulatorBinary = new File(ourAvdManager.getLocalSdk().getLocation(),
                                   FileUtil.join(SdkConstants.OS_SDK_TOOLS_FOLDER, SdkConstants.FN_EMULATOR));
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
  public static List<AvdInfo> getAvds(boolean forceRefresh) {
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
  public static Dimension getAvdResolution(@NotNull AvdInfo info) {
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
  protected static Dimension getResolutionFromLayoutFile(@NotNull File layoutFile) {
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
  public static void deleteAvd(@NotNull AvdInfo info) {
    if (!initIfNecessary()) {
      return;
    }
    ourAvdManager.deleteAvd(info, SDK_LOG);
  }

  /**
   * Launch the given AVD in the emulator.
   */
  public static void startAvd(@NotNull final AvdInfo info) {
    if (!initIfNecessary()) {
      return;
    }
    final String avdName = info.getName();
    if (info.isRunning()) {
      return;
    }

    Map<String, String> properties = info.getProperties();
    final String scaleFactor = properties.get(AvdWizardConstants.AVD_INI_SCALE_FACTOR);
    final String netDelay = properties.get(AvdWizardConstants.AVD_INI_NETWORK_LATENCY);
    final String netSpeed = properties.get(AvdWizardConstants.AVD_INI_NETWORK_SPEED);

    final ProgressWindow p = new ProgressWindow(false, true, null);
    p.setIndeterminate(false);
    p.setDelayInMillis(0);

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.setExePath(ourEmulatorBinary.getPath());

        if (scaleFactor != null) {
          commandLine.addParameters("-scale", scaleFactor);
        }

        if (netDelay != null) {
          commandLine.addParameters("-netdelay", netDelay);
        }

        if (netSpeed != null) {
          commandLine.addParameters("-netspeed", netSpeed);
        }

        commandLine.addParameters("-avd", avdName);


        final StringBuildingOutputProcessor processor = new StringBuildingOutputProcessor();
        try {
          if (AndroidUtils.executeCommand(commandLine, processor, WaitingStrategies.WaitForTime.getInstance(1000)) ==
              ExecutionStatus.TIMEOUT) {

            // It takes about 2 seconds to start the Emulator. Display a small
            // progress indicator otherwise it seems like the action wasn't invoked and users tend
            // to click multiple times on it, ending up with several instances of the manager
            // window.
            try {
              p.start();
              p.setText("Starting AVD...");
              for (double d = 0; d < 1; d += 1.0 / 20) {
                p.setFraction(d);
                //noinspection BusyWait
                Thread.sleep(100);
              }
            }
            catch (InterruptedException ignore) {
            }
            finally {
              p.stop();
            }

            return;
          }
        }
        catch (ExecutionException e) {
          IJ_LOG.error(e);
          return;
        }
        final String message = processor.getMessage();

        if (message.toLowerCase().contains("error")) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              IJ_LOG.error("Cannot launch AVD in emulator.\nOutput:\n" + message, avdName);
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
  public static AvdInfo createOrUpdateAvd(@Nullable AvdInfo currentInfo,
                                          @NotNull String avdName,
                                          @NotNull Device device,
                                          @NotNull AvdWizardConstants.SystemImageDescription systemImageDescription,
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
      avdFolder = AvdInfo.getDefaultAvdFolder(ourAvdManager, avdName);
    }
    catch (AndroidLocation.AndroidLocationException e) {
      IJ_LOG.error("Could not create AVD " + avdName, e);
      return null;
    }
    ISystemImage image = systemImageDescription.systemImage;

    // TODO: Fix this so that the screen appears in the proper orientation
    Dimension resolution = device.getScreenSize(device.getDefaultState().getOrientation()); //device.getScreenSize(orientation);
    assert resolution != null;
    String skinName = null;

    if (skinFolder == null && isCircular) {
      skinFolder = getRoundSkin(systemImageDescription);
    }
    if (skinFolder == null) {
      skinName = String.format("%dx%d", Math.round(resolution.getWidth()), Math.round(resolution.getHeight()));
    }

    return ourAvdManager.createAvd(avdFolder,
                                    avdName,
                                    systemImageDescription.target,
                                    image.getTag(),
                                    image.getAbiType(),
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
  private static File getRoundSkin(AvdWizardConstants.SystemImageDescription systemImageDescription) {
    File[] skins = systemImageDescription.systemImage.getSkins();
    for (File skin : skins) {
      if (skin.getName().contains("Round")) {
        return skin;
      }
    }
    return null;
  }

  public static boolean avdExists(String candidate) {
    if (!initIfNecessary()) {
      return false;
    }
    return ourAvdManager.getAvd(candidate, false) != null;
  }

  static boolean isAvdRepairable(AvdInfo.AvdStatus avdStatus) {
    return avdStatus == AvdInfo.AvdStatus.ERROR_IMAGE_DIR
           || avdStatus == AvdInfo.AvdStatus.ERROR_DEVICE_CHANGED
           || avdStatus == AvdInfo.AvdStatus.ERROR_DEVICE_MISSING;
  }

  public static boolean updateAvdImageFolder(@NotNull AvdInfo avdInfo) {
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

  public static boolean updateDeviceChanged(@NotNull AvdInfo avdInfo) {
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

  public static boolean wipeUserData(@NotNull AvdInfo avdInfo) {
    if (initIfNecessary()) {
      File userdataImage = new File(avdInfo.getDataFolderPath(), "userdata-qemu.img");
      if (userdataImage.isFile()) {
        return userdataImage.delete();
      }
      return true;
    }
    return false;
  }
}

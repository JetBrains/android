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

import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.resources.Navigation;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Hardware;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.devices.Storage.Unit;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.GpuMode;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.sdklib.repositoryv2.targets.SystemImage;
import com.android.tools.idea.ddms.screenshot.DeviceArtDescriptor;
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ui.JBFont;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.STEP;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.createKey;

/**
 * State store keys for the AVD Manager wizards
 */
public class AvdWizardUtils {

  public static final String WIZARD_ONLY = "AvdManager.WizardOnly.";

  // Avd option keys
  public static final Key<Device> DEVICE_DEFINITION_KEY = createKey(WIZARD_ONLY + "DeviceDefinition", WIZARD, Device.class);
  public static final Key<SystemImageDescription> SYSTEM_IMAGE_KEY = createKey(WIZARD_ONLY + "SystemImage", WIZARD, SystemImageDescription.class);

  public static final Key<Storage> RAM_STORAGE_KEY = createKey(AvdManager.AVD_INI_RAM_SIZE, WIZARD, Storage.class);
  public static final Key<Storage> VM_HEAP_STORAGE_KEY = createKey(AvdManager.AVD_INI_VM_HEAP_SIZE, WIZARD, Storage.class);
  public static final Key<Storage> INTERNAL_STORAGE_KEY = createKey(AvdManager.AVD_INI_DATA_PARTITION_SIZE, WIZARD, Storage.class);
  public static final Key<Storage> SD_CARD_STORAGE_KEY = createKey(AvdManager.AVD_INI_SDCARD_SIZE, WIZARD, Storage.class);
  public static final Key<String> EXISTING_SD_LOCATION = createKey(AvdManager.AVD_INI_SDCARD_PATH, WIZARD, String.class);

  // Keys used for display properties within the wizard. The values are derived from (and used to derive) the values for
  // SD_CARD_STORAGE_KEY and EXISTING_SD_LOCATION
  public static final Key<Storage> DISPLAY_SD_SIZE_KEY = createKey(WIZARD_ONLY + "displaySdCardSize", WIZARD, Storage.class);
  public static final Key<String> DISPLAY_SD_LOCATION_KEY = createKey(WIZARD_ONLY + "displaySdLocation", WIZARD, String.class);
  public static final Key<Boolean> DISPLAY_USE_EXTERNAL_SD_KEY = createKey(WIZARD_ONLY + "displayUseExistingSd", WIZARD, Boolean.class);

  public static final String AVD_INI_SCALE_FACTOR = "runtime.scalefactor";
  public static final Key<AvdScaleFactor> SCALE_SELECTION_KEY = createKey(AVD_INI_SCALE_FACTOR, WIZARD, AvdScaleFactor.class);

  public static final Key<ScreenOrientation> DEFAULT_ORIENTATION_KEY = createKey(WIZARD_ONLY + "DefaultOrientation", WIZARD, ScreenOrientation.class);

  public static final String AVD_INI_NETWORK_SPEED = "runtime.network.speed";
  public static final Key<String> NETWORK_SPEED_KEY = createKey(AVD_INI_NETWORK_SPEED, WIZARD, String.class);
  public static final String AVD_INI_NETWORK_LATENCY = "runtime.network.latency";
  public static final Key<String> NETWORK_LATENCY_KEY = createKey(AVD_INI_NETWORK_LATENCY, WIZARD, String.class);

  public static final Key<String> FRONT_CAMERA_KEY = createKey(AvdManager.AVD_INI_CAMERA_FRONT, WIZARD, String.class);
  public static final Key<String> BACK_CAMERA_KEY = createKey(AvdManager.AVD_INI_CAMERA_BACK, WIZARD, String.class);
  public static final String CHOOSE_DEVICE_DEFINITION_STEP = "Choose Device Definition Step";
  public static final String CHOOSE_SYSTEM_IMAGE_STEP = "Choose System Image Step";

  public static final Key<Boolean> USE_HOST_GPU_KEY = createKey(AvdManager.AVD_INI_GPU_EMULATION, WIZARD, Boolean.class);
  public static final Key<GpuMode> HOST_GPU_MODE_KEY = createKey(AvdManager.AVD_INI_GPU_MODE, WIZARD, GpuMode.class);

  public static final Key<Boolean> IS_IN_EDIT_MODE_KEY = createKey(WIZARD_ONLY + "isInEditMode", WIZARD, Boolean.class);

  public static final Key<File> DISPLAY_SKIN_FILE_KEY = createKey("displaySkinPath", STEP, File.class);
  public static final Key<File> CUSTOM_SKIN_FILE_KEY = createKey(AvdManager.AVD_INI_SKIN_PATH, WIZARD, File.class);
  public static final Key<File> BACKUP_SKIN_FILE_KEY = createKey(AvdManager.AVD_INI_BACKUP_SKIN_PATH, WIZARD, File.class);
  public static final Key<Boolean> DEVICE_FRAME_KEY = createKey("showDeviceFrame", STEP, Boolean.class);

  public static final Key<String> DISPLAY_NAME_KEY = createKey(AvdManagerConnection.AVD_INI_DISPLAY_NAME, WIZARD, String.class);
  public static final String AVD_INI_AVD_ID = "AvdId";
  public static final Key<String> AVD_ID_KEY = createKey(AVD_INI_AVD_ID, WIZARD, String.class);

  public static final Key<Boolean> RANCHU_KEY = createKey(WIZARD_ONLY + "ranchu.emulator", WIZARD, Boolean.class);
  public static final Key<Integer> CPU_CORES_KEY = createKey(AvdManager.AVD_INI_CPU_CORES, WIZARD, Integer.class);

  // Device definition keys

  public static final Key<String> DEVICE_NAME_KEY = createKey("DeviceName", STEP, String.class);

  public static final Key<Double> DIAGONAL_SCREENSIZE_KEY = createKey("DiagonalScreenSize", STEP, Double.class);
  public static final Key<Integer> RESOLUTION_WIDTH_KEY = createKey("ResolutionWidth", STEP, Integer.class);
  public static final Key<Integer> RESOLUTION_HEIGHT_KEY = createKey("ResolutionHeight", STEP, Integer.class);

  public static final Key<Boolean> HAS_HARDWARE_BUTTONS_KEY = createKey("HasHardwareButtons", STEP, Boolean.class);
  public static final Key<Boolean> HAS_HARDWARE_KEYBOARD_KEY = createKey(HardwareProperties.HW_KEYBOARD, WIZARD, Boolean.class);
  public static final Key<Navigation> NAVIGATION_KEY = createKey("Navigation", STEP, Navigation.class);

  public static final Key<Boolean> SUPPORTS_LANDSCAPE_KEY = createKey("SupportsLandscape", STEP, Boolean.class);
  public static final Key<Boolean> SUPPORTS_PORTRAIT_KEY = createKey("SupportsPortrait", STEP, Boolean.class);

  public static final Key<Boolean> HAS_BACK_CAMERA_KEY = createKey("HasBackCamera", STEP, Boolean.class);
  public static final Key<Boolean> HAS_FRONT_CAMERA_KEY = createKey("HasFrontCamera", STEP, Boolean.class);

  public static final Key<Boolean> HAS_ACCELEROMETER_KEY = createKey("HasAccelerometer", STEP, Boolean.class);
  public static final Key<Boolean> HAS_GYROSCOPE_KEY = createKey("HasGyroscope", STEP, Boolean.class);
  public static final Key<Boolean> HAS_GPS_KEY = createKey("HasGPS", STEP, Boolean.class);
  public static final Key<Boolean> HAS_PROXIMITY_SENSOR_KEY = createKey("HasProximitySensor", STEP, Boolean.class);

  public static final Key<Screen> WIP_SCREEN_KEY = createKey("ScreenUnderConstruction", STEP, Screen.class);
  public static final Key<Hardware> WIP_HARDWARE_KEY = createKey("HardwareUnderConstruction" ,STEP, Hardware.class);
  public static final Key<Double> WIP_SCREEN_DPI_KEY = createKey("ScreenDPI", STEP, Double.class);

  public static final Key<IdDisplay> TAG_ID_KEY = createKey("TagId", STEP, IdDisplay.class);

  // Defaults
  public static final AvdScaleFactor DEFAULT_SCALE = AvdScaleFactor.AUTO;
  public static final String DEFAULT_NETWORK_SPEED = "full";
  public static final String DEFAULT_NETWORK_LATENCY = "none";
  public static final String DEFAULT_CAMERA = "none";
  public static final Storage DEFAULT_INTERNAL_STORAGE = new Storage(200, Unit.MiB);

  // Fonts
  public static final Font STANDARD_FONT = JBFont.create(new Font("Sans", Font.PLAIN, 12));
  public static final Font FIGURE_FONT = JBFont.create(new Font("Sans", Font.PLAIN, 10));
  public static final Font TITLE_FONT = JBFont.create(new Font("Sans", Font.BOLD, 16));

  // Tags
  public static final IdDisplay WEAR_TAG = IdDisplay.create("android-wear", "Android Wear");
  public static final IdDisplay TV_TAG = IdDisplay.create("android-tv", "Android TV");
  public static final IdDisplay GOOGLE_APIS_TAG = IdDisplay.create("google_apis", "Google APIs");

  public static final List<IdDisplay> ALL_TAGS = ImmutableList.of(SystemImage.DEFAULT_TAG, WEAR_TAG, TV_TAG);
  public static final List<IdDisplay> TAGS_WITH_GOOGLE_API = ImmutableList.of(GOOGLE_APIS_TAG, WEAR_TAG, TV_TAG);

  public static final String CREATE_SKIN_HELP_LINK = "http://developer.android.com/tools/devices/managing-avds.html#skins";

  public static final File NO_SKIN = new File("_no_skin");

  // The AVD wizard needs a bit of extra width as its options panel is pretty dense
  private static final Dimension AVD_WIZARD_SIZE = new Dimension(1000, 650);

  private static final String AVD_WIZARD_HELP_URL = "https://developer.android.com/r/studio-ui/avd-manager.html";

  /** Maximum amount of RAM to *default* an AVD to, if the physical RAM on the device is higher */
  private static final int MAX_RAM_MB = 1536;

  private static Logger getLog() {
    return Logger.getInstance(AvdWizardUtils.class);
  }


  /**
   * Get the default amount of ram to use for the given hardware in an AVD. This is typically
   * the same RAM as is used in the hardware, but it is maxed out at {@link #MAX_RAM_MB} since more than that
   * is usually detrimental to development system performance and most likely not needed by the
   * emulated app (e.g. it's intended to let the hardware run smoothly with lots of services and
   * apps running simultaneously)
   *
   * @param hardware the hardware to look up the default amount of RAM on
   * @return the amount of RAM to default an AVD to for the given hardware
   */
  @NotNull
  public static Storage getDefaultRam(@NotNull Hardware hardware) {
    return getMaxPossibleRam(hardware.getRam());
  }

  /**
   * Get the default amount of ram to use for the given hardware in an AVD. This is typically
   * the same RAM as is used in the hardware, but it is maxed out at {@link #MAX_RAM_MB} since more than that
   * is usually detrimental to development system performance and most likely not needed by the
   * emulated app (e.g. it's intended to let the hardware run smoothly with lots of services and
   * apps running simultaneously)
   *
   * @param data the {@link AvdDeviceData} to look up the default amount of RAM on
   * @return the amount of RAM to default an AVD to for the given hardware
   */
  @NotNull
  public static Storage getDefaultRam(@NotNull AvdDeviceData data) {
    return getMaxPossibleRam(data.ramStorage().get());
  }

  /**
   * Limits the ram to {@link #MAX_RAM_MB}
   */
  @NotNull
  private static Storage getMaxPossibleRam(Storage ram) {
    if (ram.getSizeAsUnit(Storage.Unit.MiB) >= MAX_RAM_MB) {
      return new Storage(MAX_RAM_MB, Storage.Unit.MiB);
    }
    return ram;
  }

  /**
   * Return the max number of cores that an AVD can use on this development system.
   */
  public static int getMaxCpuCores() {
    return Runtime.getRuntime().availableProcessors() / 2;
  }

  /**
   * Get a version of {@code candidateBase} modified such that it is a valid filename. Invalid characters will be
   * removed, and if requested the name will be made unique.
   *
   * @param candidateBase the name on which to base the avd name.
   * @param uniquify      if true, _n will be appended to the name if necessary to make the name unique, where n is the first
   *                      number that makes the filename unique.
   * @return The modified filename.
   */
  public static String cleanAvdName(@NotNull AvdManagerConnection connection, @NotNull String candidateBase, boolean uniquify) {
    candidateBase = candidateBase.replaceAll("[^0-9a-zA-Z_-]+", " ").trim().replaceAll("[ _]+", "_");
    if (candidateBase.isEmpty()) {
      candidateBase = "myavd";
    }
    String candidate = candidateBase;
    if (uniquify) {
      int i = 1;
      while (connection.avdExists(candidate)) {
        candidate = String.format("%1$s_%2$d", candidateBase, i++);
      }
    }
    return candidate;
  }

  @Nullable
  public static File resolveSkinPath(@Nullable File path, @Nullable SystemImageDescription image, @NotNull FileOp fop) {
    if (path == null || path.getPath().isEmpty()) {
      return path;
    }
    if (FileUtil.filesEqual(path, NO_SKIN)) {
      return NO_SKIN;
    }
    if (!path.isAbsolute()) {
      if (image != null) {
        File[] skins = image.getSkins();
        for (File skin : skins) {
          if (skin.getPath().endsWith(File.separator + path.getPath())) {
            return skin;
          }
        }
      }
      AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
      File dest = null;
      if (sdkData != null) {
        File sdkDir = sdkData.getLocation();
        File sdkSkinDir = new File(sdkDir, "skins");
        dest = new File(sdkSkinDir, path.getPath());
        if (fop.exists(dest)) {
          return dest;
        }
      }

      File resourceDir = DeviceArtDescriptor.getBundledDescriptorsFolder();
      if (resourceDir != null) {
        File resourcePath = new File(resourceDir, path.getPath());
        if (fop.exists(resourcePath)) {
          if (dest != null) {
            try {
              FileOpUtils.recursiveCopy(resourcePath, dest.getParentFile(), fop);
              return new File(dest, path.getPath());
            }
            catch (IOException e) {
              getLog().warn(String.format("Failed to copy skin directory to %1$s, using studio-relative path %2$s",
                                     dest, resourcePath));
            }
          }
          return resourcePath;
        }
      }
    }
    return path;
  }

  /**
   * Creates a {@link ModelWizardDialog} containing all the steps needed to create a new AVD
   */
  public static ModelWizardDialog createAvdWizard(@Nullable Component parent,
                                                  @Nullable Project project) {
    return createAvdWizard(parent, project, new AvdOptionsModel(null));
  }

  /**
   * Creates a {@link ModelWizardDialog} containing all the steps needed to create or edit AVDs
   */
  public static ModelWizardDialog createAvdWizard(@Nullable Component parent,
                                                  @Nullable Project project,
                                                  @Nullable AvdInfo avdInfo) {
    return createAvdWizard(parent, project, new AvdOptionsModel(avdInfo));
  }

  /**
   * Creates a {@link ModelWizardDialog} containing all the steps needed to create or edit AVDs
   */
  public static ModelWizardDialog createAvdWizard(@Nullable Component parent,
                                                  @Nullable Project project,
                                                  @NotNull AvdOptionsModel model) {
    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();
    if (!model.isInEditMode().get()) {
      wizardBuilder.addStep(new ChooseDeviceDefinitionStep(model.device()));
      wizardBuilder.addStep(new ChooseSystemImageStep(project, model.device(), model.systemImage()));
    }
    wizardBuilder.addStep(new ConfigureAvdOptionsStep(project, model));
    ModelWizard wizard = wizardBuilder.build();
    StudioWizardDialogBuilder builder = new StudioWizardDialogBuilder(wizard, "Virtual Device Configuration", parent);
    builder.setMinimumSize(AVD_WIZARD_SIZE);
    return builder.setHelpUrl(WizardUtils.toUrl(AVD_WIZARD_HELP_URL)).build();
  }
}

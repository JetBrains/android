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
import com.android.sdklib.devices.Hardware;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.devices.Storage.Unit;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.HardwareProperties;
import com.android.sdklib.repository.IdDisplay;
import com.android.tools.idea.ddms.screenshot.DeviceArtDescriptor;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
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

import static com.android.sdklib.repository.targets.SystemImage.*;

/**
 * State store keys for the AVD Manager wizards
 */
public class AvdWizardUtils {

  public static final String WIZARD_ONLY = "AvdManager.WizardOnly.";

  // Avd option keys
  public static final String DEVICE_DEFINITION_KEY = WIZARD_ONLY + "DeviceDefinition";
  public static final String SYSTEM_IMAGE_KEY = WIZARD_ONLY + "SystemImage";

  public static final String RAM_STORAGE_KEY = AvdManager.AVD_INI_RAM_SIZE;
  public static final String VM_HEAP_STORAGE_KEY = AvdManager.AVD_INI_VM_HEAP_SIZE;
  public static final String INTERNAL_STORAGE_KEY = AvdManager.AVD_INI_DATA_PARTITION_SIZE;
  public static final String SD_CARD_STORAGE_KEY = AvdManager.AVD_INI_SDCARD_SIZE;
  public static final String EXISTING_SD_LOCATION = AvdManager.AVD_INI_SDCARD_PATH;

  // Keys used for display properties within the wizard. The values are derived from (and used to derive) the values for
  // SD_CARD_STORAGE_KEY and EXISTING_SD_LOCATION
  public static final String DISPLAY_SD_SIZE_KEY = WIZARD_ONLY + "displaySdCardSize";
  public static final String DISPLAY_SD_LOCATION_KEY = WIZARD_ONLY + "displaySdLocation";
  public static final String DISPLAY_USE_EXTERNAL_SD_KEY = WIZARD_ONLY + "displayUseExistingSd";

  public static final String AVD_INI_SCALE_FACTOR = "runtime.scalefactor";
  public static final String SCALE_SELECTION_KEY = AVD_INI_SCALE_FACTOR;

  public static final String DEFAULT_ORIENTATION_KEY = WIZARD_ONLY + "DefaultOrientation";

  public static final String AVD_INI_NETWORK_SPEED = "runtime.network.speed";
  public static final String NETWORK_SPEED_KEY = AVD_INI_NETWORK_SPEED;
  public static final String AVD_INI_NETWORK_LATENCY = "runtime.network.latency";
  public static final String NETWORK_LATENCY_KEY = AVD_INI_NETWORK_LATENCY;

  public static final String FRONT_CAMERA_KEY = AvdManager.AVD_INI_CAMERA_FRONT;
  public static final String BACK_CAMERA_KEY = AvdManager.AVD_INI_CAMERA_BACK;

  public static final String USE_HOST_GPU_KEY = AvdManager.AVD_INI_GPU_EMULATION;
  public static final String HOST_GPU_MODE_KEY = AvdManager.AVD_INI_GPU_MODE;

  public static final String IS_IN_EDIT_MODE_KEY = WIZARD_ONLY + "isInEditMode";

  public static final String CUSTOM_SKIN_FILE_KEY = AvdManager.AVD_INI_SKIN_PATH;
  public static final String BACKUP_SKIN_FILE_KEY = AvdManager.AVD_INI_BACKUP_SKIN_PATH;
  public static final String DEVICE_FRAME_KEY = "showDeviceFrame";

  public static final String DISPLAY_NAME_KEY = AvdManagerConnection.AVD_INI_DISPLAY_NAME;
  public static final String AVD_INI_AVD_ID = "AvdId";
  public static final String AVD_ID_KEY = AVD_INI_AVD_ID;

  public static final String CPU_CORES_KEY = AvdManager.AVD_INI_CPU_CORES;

  // Device definition keys

  public static final String HAS_HARDWARE_KEYBOARD_KEY = HardwareProperties.HW_KEYBOARD;

  // Defaults
  public static final AvdScaleFactor DEFAULT_SCALE = AvdScaleFactor.AUTO;
  public static final AvdNetworkSpeed DEFAULT_NETWORK_SPEED = AvdNetworkSpeed.FULL;
  public static final AvdNetworkLatency DEFAULT_NETWORK_LATENCY = AvdNetworkLatency.NONE;
  public static final AvdCamera DEFAULT_CAMERA = AvdCamera.NONE;
  public static final Storage DEFAULT_INTERNAL_STORAGE = new Storage(200, Unit.MiB);

  // Fonts
  public static final Font STANDARD_FONT = JBFont.create(new Font("Sans", Font.PLAIN, 12));
  public static final Font FIGURE_FONT = JBFont.create(new Font("Sans", Font.PLAIN, 10));
  public static final Font TITLE_FONT = JBFont.create(new Font("Sans", Font.BOLD, 16));

  // Tags
  public static final List<IdDisplay> ALL_DEVICE_TAGS = ImmutableList.of(DEFAULT_TAG, WEAR_TAG, TV_TAG);
  public static final List<IdDisplay> TAGS_WITH_GOOGLE_API = ImmutableList.of(GOOGLE_APIS_TAG, GOOGLE_APIS_X86_TAG, WEAR_TAG, TV_TAG);

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
   * @return the amount of RAM to default an AVD to for the given hardware
   */
  @NotNull
  public static Storage getMaxPossibleRam() {
    return new Storage(MAX_RAM_MB, Storage.Unit.MiB);
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
              FileOpUtils.recursiveCopy(resourcePath, dest, fop, new StudioLoggerProgressIndicator(AvdWizardUtils.class));
              return dest;
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
      wizardBuilder.addStep(new ChooseDeviceDefinitionStep(model));
      wizardBuilder.addStep(new ChooseSystemImageStep(model, project));
    }
    wizardBuilder.addStep(new ConfigureAvdOptionsStep(project, model));
    ModelWizard wizard = wizardBuilder.build();
    StudioWizardDialogBuilder builder = new StudioWizardDialogBuilder(wizard, "Virtual Device Configuration", parent);
    builder.setMinimumSize(AVD_WIZARD_SIZE);
    return builder.setHelpUrl(WizardUtils.toUrl(AVD_WIZARD_HELP_URL)).build();
  }
}

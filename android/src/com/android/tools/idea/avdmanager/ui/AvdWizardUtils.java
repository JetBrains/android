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
package com.android.tools.idea.avdmanager.ui;

import static com.android.sdklib.SystemImageTags.ANDROID_TV_TAG;
import static com.android.sdklib.SystemImageTags.AUTOMOTIVE_TAG;
import static com.android.sdklib.SystemImageTags.CHROMEOS_TAG;
import static com.android.sdklib.SystemImageTags.DEFAULT_TAG;
import static com.android.sdklib.SystemImageTags.DESKTOP_TAG;
import static com.android.sdklib.SystemImageTags.GOOGLE_TV_TAG;
import static com.android.sdklib.SystemImageTags.WEAR_TAG;

import com.android.annotations.concurrency.Slow;
import com.android.sdklib.devices.Hardware;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.IdDisplay;
import com.android.tools.idea.avdmanager.DeviceSkinUpdater;
import com.android.tools.idea.avdmanager.DeviceSkinUpdaterService;
import com.android.tools.idea.avdmanager.SkinUtils;
import com.android.tools.idea.avdmanager.SystemImageDescription;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * State store keys for the AVD Manager wizards
 */
public class AvdWizardUtils {

  public static final String WIZARD_ONLY = "AvdManager.WizardOnly.";

  // Avd option keys
  public static final String DEVICE_DEFINITION_KEY = WIZARD_ONLY + "DeviceDefinition";
  public static final String SYSTEM_IMAGE_KEY = WIZARD_ONLY + "SystemImage";

  public static final String DEFAULT_ORIENTATION_KEY = WIZARD_ONLY + "DefaultOrientation";

  public static final String IS_IN_EDIT_MODE_KEY = WIZARD_ONLY + "isInEditMode";

  // Fonts
  public static final Font STANDARD_FONT = JBFont.create(new Font("DroidSans", Font.PLAIN, 12));
  public static final Font FIGURE_FONT = JBFont.create(new Font("DroidSans", Font.PLAIN, 10));
  public static final Font TITLE_FONT = JBFont.create(new Font("DroidSans", Font.BOLD, 16));

  // Tags
  public static final List<IdDisplay> ALL_DEVICE_TAGS = ImmutableList.of(DEFAULT_TAG, WEAR_TAG, DESKTOP_TAG,
                                                                         ANDROID_TV_TAG, GOOGLE_TV_TAG,
                                                                         CHROMEOS_TAG, AUTOMOTIVE_TAG);
  public static final String CREATE_SKIN_HELP_LINK = "http://developer.android.com/tools/devices/managing-avds.html#skins";

  /**
   * @deprecated Use {@link SkinUtils#noSkin()}
   */
  @Deprecated
  static final File NO_SKIN = SkinUtils.noSkin().toFile();

  // The AVD wizard needs a bit of extra width as its options panel is pretty dense
  private static final Dimension AVD_WIZARD_MIN_SIZE = JBUI.size(600, 400);
  private static final Dimension AVD_WIZARD_SIZE = JBUI.size(1100, 750);

  private static final String AVD_WIZARD_HELP_URL = "https://developer.android.com/r/studio-ui/avd-manager.html";

  /** Maximum amount of RAM to *default* an AVD to, if the physical RAM on the device is higher */
  private static final int MAX_RAM_MB = 2048;

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
   * @deprecated Use {@link DeviceSkinUpdaterService#updateSkins(Path, SystemImageDescription)}
   */
  @Deprecated
  @Slow
  public static @Nullable File pathToUpdatedSkins(@Nullable Path device,
                                                  @Nullable SystemImageDescription image) {
    return device == null ? null : DeviceSkinUpdater.updateSkin(device, image).toFile();
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
    builder.setMinimumSize(AVD_WIZARD_MIN_SIZE);
    builder.setPreferredSize(AVD_WIZARD_SIZE);
    return builder.setHelpUrl(toUrl(AVD_WIZARD_HELP_URL)).build();
  }

  /**
   * Utility method used to create a URL from its String representation without throwing a {@link MalformedURLException}.
   * Callers should use this if they're absolutely certain their URL is well formatted.
   */
  @NotNull
  private static URL toUrl(@NotNull String urlAsString) {
    URL url;
    try {
      url = new URL(urlAsString);
    }
    catch (MalformedURLException e) {
      // Caller should guarantee this will never happen!
      throw new RuntimeException(e);
    }
    return url;
  }

  /**
   * Creates a {@link ModelWizardDialog} containing all the steps needed to duplicate
   * an existing AVD
   */
  public static ModelWizardDialog createAvdWizardForDuplication(@Nullable Component parent,
                                                                @Nullable Project project,
                                                                @NotNull AvdOptionsModel avdOptions) {
    // Set this AVD as a copy
    avdOptions.setAsCopy();

    return createAvdWizard(parent, project, avdOptions);
  }

}

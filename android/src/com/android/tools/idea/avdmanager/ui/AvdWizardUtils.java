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
import static com.android.sdklib.SystemImageTags.AUTOMOTIVE_DISTANT_DISPLAY_TAG;
import static com.android.sdklib.SystemImageTags.AUTOMOTIVE_TAG;
import static com.android.sdklib.SystemImageTags.DEFAULT_TAG;
import static com.android.sdklib.SystemImageTags.DESKTOP_TAG;
import static com.android.sdklib.SystemImageTags.GOOGLE_TV_TAG;
import static com.android.sdklib.SystemImageTags.WEAR_TAG;
import static com.android.sdklib.SystemImageTags.XR_TAG;

import com.android.annotations.concurrency.Slow;
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

  // Fonts
  public static final Font STANDARD_FONT = JBFont.create(new Font("DroidSans", Font.PLAIN, 12));
  public static final Font FIGURE_FONT = JBFont.create(new Font("DroidSans", Font.PLAIN, 10));
  public static final Font TITLE_FONT = JBFont.h3().asBold();

  // Tags
  public static final List<IdDisplay> ALL_DEVICE_TAGS = ImmutableList.of(DEFAULT_TAG, WEAR_TAG, DESKTOP_TAG,
                                                                         ANDROID_TV_TAG, GOOGLE_TV_TAG,
                                                                         AUTOMOTIVE_TAG, AUTOMOTIVE_DISTANT_DISPLAY_TAG,
                                                                         XR_TAG);
  public static final String CREATE_SKIN_HELP_LINK = "http://developer.android.com/tools/devices/managing-avds.html#skins";

  /**
   * @deprecated Use {@link SkinUtils#noSkin()}
   */
  @Deprecated
  static final File NO_SKIN = SkinUtils.noSkin().toFile();

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
}

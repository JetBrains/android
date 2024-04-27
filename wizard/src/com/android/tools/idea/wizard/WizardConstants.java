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
package com.android.tools.idea.wizard;

import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.createKey;

import com.android.tools.idea.wizard.ui.StudioWizardLayout;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import java.awt.Dimension;
import java.awt.Insets;

/**
 * Constants for template based wizards.
 *
 * <p>These concepts are effectively deprecated and should now exist in appropriate locations inside the com.android.tools.idea.ui.wizard
 * package.
 */
public class WizardConstants {

  // Colors
  /**
   * @deprecated Obsoleted by {@link StudioWizardLayout}
   */
  public static final JBColor ANDROID_NPW_HEADER_COLOR = new JBColor(0x616161, 0x4B4B4B);

  // Dimensions
  /**
   * @deprecated Obsoleted by {@link StudioWizardLayout}
   */
  public static final int STUDIO_WIZARD_INSET_SIZE = JBUI.scale(12);
  /**
   * @deprecated Obsoleted by {@link StudioWizardLayout}
   */
  public static final Insets STUDIO_WIZARD_INSETS =
    new Insets(0, STUDIO_WIZARD_INSET_SIZE, STUDIO_WIZARD_INSET_SIZE, STUDIO_WIZARD_INSET_SIZE);
  /**
   * @deprecated Obsoleted by {@link StudioWizardLayout}
   */
  public static final int STUDIO_WIZARD_TOP_INSET = 18;

  /**
   * @deprecated Obsoleted by {@link StudioWizardLayout}
   */
  public static final Dimension DEFAULT_WIZARD_WINDOW_SIZE = JBUI.size(1080, 650);

  public static final Dimension DEFAULT_GALLERY_THUMBNAIL_SIZE = JBUI.size(192, 192);

  // State Store Keys
  // TODO After the wizard migration delete as many of these keys as possible
  public static final Key<String> KEY_SDK_INSTALL_LOCATION = createKey("download.sdk.location", WIZARD, String.class);
  // TODO: change this an IntProperty, see com.android.tools.idea.sdk.wizard.InstallSelectedPackagesStep#checkForUpgrades
  public static final Key<Integer> NEWLY_INSTALLED_API_KEY = createKey("newly.installed.api.level", WIZARD, Integer.class);
}

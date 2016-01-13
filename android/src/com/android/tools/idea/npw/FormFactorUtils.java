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
package com.android.tools.idea.npw;

import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.tools.idea.configurations.DeviceMenuAction;
import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;

import static com.android.tools.idea.npw.FormFactorApiComboBox.AndroidTargetComboBoxItem;
import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.WizardConstants.INVALID_FILENAME_CHARS;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.STEP;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.createKey;

/**
 * Utility methods for dealing with Form Factors in Wizards.
 *
 * TODO: After wizard migration, much of this class may go away (as a lot of it is specific to
 * dynamic wizard). Consider folding remaining methods into {@link FormFactor} at that time.
 */
public class FormFactorUtils {
  public static final String INCLUDE_FORM_FACTOR = "included";
  public static final String ATTR_MODULE_NAME = "projectName";

  @NotNull
  public static Key<AndroidTargetComboBoxItem> getTargetComboBoxKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_MIN_API + "combo", STEP, AndroidTargetComboBoxItem.class);
  }

  @NotNull
  public static Key<Integer> getMinApiLevelKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_MIN_API_LEVEL, WIZARD, Integer.class);
  }

  @NotNull
  public static Key<String> getMinApiKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_MIN_API, WIZARD, String.class);
  }

  @NotNull
  public static Key<String> getBuildApiKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_BUILD_API_STRING, WIZARD, String.class);
  }

  @NotNull
  public static Key<Integer> getTargetApiLevelKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_TARGET_API, WIZARD, Integer.class);
  }

  @NotNull
  public static Key<String> getTargetApiStringKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_TARGET_API_STRING, WIZARD, String.class);
  }

  @NotNull
  public static Key<Integer> getBuildApiLevelKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_BUILD_API, WIZARD, Integer.class);
  }

  @NotNull
  public static Key<String> getLanguageLevelKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_JAVA_VERSION, WIZARD, String.class);
  }

  @NotNull
  public static Key<Boolean> getInclusionKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + INCLUDE_FORM_FACTOR, WIZARD, Boolean.class);
  }

  @NotNull
  public static Key<String> getModuleNameKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_MODULE_NAME, WIZARD, String.class);
  }

  public static Map<String, Object> scrubFormFactorPrefixes(@NotNull FormFactor formFactor, @NotNull Map<String, Object> values) {
    Map<String, Object> toReturn = Maps.newHashMapWithExpectedSize(values.size());
    for (String key : values.keySet()) {
      if (key.startsWith(formFactor.id)) {
        toReturn.put(key.substring(formFactor.id.length()), values.get(key));
      } else {
        toReturn.put(key, values.get(key));
      }
    }
    return toReturn;
  }

  public static String getPropertiesComponentMinSdkKey(@NotNull FormFactor formFactor) {
    return formFactor.id + ATTR_MIN_API;
  }

  @NotNull
  public static String getModuleName(@NotNull FormFactor formFactor) {
    if (formFactor.baseFormFactor != null) {
      // Form factors like Android Auto build upon another form factor
      formFactor = formFactor.baseFormFactor;
    }
    String name = formFactor.id.replaceAll(INVALID_FILENAME_CHARS, "");
    name = name.replaceAll("\\s", "_");
    return name.toLowerCase();
  }

  public static Predicate<AndroidTargetComboBoxItem> getMinSdkComboBoxFilter(@NotNull final FormFactor formFactor, final int minSdkLevel) {
    return new Predicate<AndroidTargetComboBoxItem>() {
      @Override
      public boolean apply(@Nullable AndroidTargetComboBoxItem input) {
        if (input == null) {
          return false;
        }

        return doFilter(formFactor, minSdkLevel, input.target != null ? input.target.getName() : null, input.apiLevel) ||
               (input.target != null && input.target.getVersion().isPreview());
      }
    };
  }

  public static Predicate<RepoPackage> getMinSdkPackageFilter(
    @NotNull final FormFactor formFactor, final int minSdkLevel) {
    return new Predicate<RepoPackage>() {
      @Override
      public boolean apply(@Nullable RepoPackage input) {
        if (input == null) {
          return false;
        }
        return filterPkgDesc(input, formFactor, minSdkLevel);
      }
    };
  }

  private static boolean filterPkgDesc(@NotNull RepoPackage p, @NotNull FormFactor formFactor, int minSdkLevel) {
    TypeDetails details = p.getTypeDetails();
    if (details instanceof DetailsTypes.AddonDetailsType) {
      DetailsTypes.AddonDetailsType addonDetails = (DetailsTypes.AddonDetailsType)details;
      return doFilter(formFactor, minSdkLevel, addonDetails.getTag().getId(),
                      DetailsTypes.getAndroidVersion(addonDetails).getFeatureLevel());
    }
    // TODO: add other package types
    return false;
  }

  private static boolean doFilter(@NotNull FormFactor formFactor, int minSdkLevel, @Nullable String inputName, int targetSdkLevel) {
    if (!formFactor.getApiWhitelist().isEmpty()) {
      // If a whitelist is present, only allow things on the whitelist
      boolean found = false;
      for (String filterItem : formFactor.getApiWhitelist()) {
        if (matches(filterItem, inputName, targetSdkLevel)) {
          found = true;
          break;
        }
      }
      if (!found) {
        return false;
      }
    }

    // Now check the blacklist
    for (String filterItem : formFactor.getApiBlacklist()) {
      if (matches(filterItem, inputName, targetSdkLevel)) {
        return false;
      }
    }

    // Finally, we'll check that the minSDK is honored
    return targetSdkLevel >= minSdkLevel;
  }


  /**
   * @return true iff inputVersion is parsable as an int that matches filterItem, or if inputName contains filterItem.
   */
  private static boolean matches(@NotNull String filterItem, @Nullable String inputName, int inputVersion) {
    if (Integer.toString(inputVersion).equals(filterItem)) {
      return true;
    }
    if (inputName != null && inputName.contains(filterItem)) {
      return true;
    }
    return false;
  }

  /**
   * Create an image showing icons for each of the available form factors.
   * @param component Icon will be drawn in the context of the given {@code component}
   * @param requireEmulator If true, only include icons for form factors that have an emulator available.
   * @return The new Icon
   */
  @Nullable
  public static Icon getFormFactorsImage(JComponent component, boolean requireEmulator) {
    int width = 0;
    int height = 0;
    for (DeviceMenuAction.FormFactor formFactor : DeviceMenuAction.FormFactor.values()) {
      Icon icon = formFactor.getLargeIcon();
      height = icon.getIconHeight();
      if (!requireEmulator || formFactor.hasEmulator()) {
        width += formFactor.getLargeIcon().getIconWidth();
      }
    }
    //noinspection UndesirableClassUsage
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    int x = 0;
    for (DeviceMenuAction.FormFactor formFactor : DeviceMenuAction.FormFactor.values()) {
      if (requireEmulator && !formFactor.hasEmulator()) {
        continue;
      }
      Icon icon = formFactor.getLargeIcon();
      icon.paintIcon(component, graphics, x, 0);
      x += icon.getIconWidth();
    }
    if (graphics != null) {
      graphics.dispose();
      return new ImageIcon(image);
    }
    else {
      return null;
    }
  }
}

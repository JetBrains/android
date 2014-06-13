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

import com.google.common.collect.Maps;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.ConfigureAndroidProjectStep.INVALID_FILENAME_CHARS;
import static com.android.tools.idea.wizard.ConfigureFormFactorStep.AndroidTargetComboBoxItem;
import static com.android.tools.idea.wizard.NewProjectWizardState.ATTR_MODULE_NAME;
import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.STEP;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * Utility methods for dealing with Form Factors in Wizards.
 */
public class FormFactorUtils {
  public static final String INCLUDE_FORM_FACTOR = "included";

  public static enum FormFactor {
    MOBILE("Mobile", AndroidIcons.Wizards.FormFactorPhoneTabletLight,
                     AndroidIcons.Wizards.FormFactorPhoneTabletDark,
                     "Phone and Tablet"),
    WEAR("Wear", AndroidIcons.Wizards.FormFactorWearLight, AndroidIcons.Wizards.FormFactorWearDark, "Wear"),
    GLASS("Glass", AndroidIcons.Wizards.FormFactorGlassLight, AndroidIcons.Wizards.FormFactorGlassDark, "Glass");

    public final String id;
    @Nullable private final Icon myLightIcon;
    @Nullable private final Icon myDarkIcon;
    @Nullable private String displayName;

    FormFactor(@NotNull String id, @Nullable Icon lightIcon, @Nullable Icon darkIcon, @Nullable String displayName) {
      this.id = id;
      myLightIcon = lightIcon;
      myDarkIcon = darkIcon;
      this.displayName = displayName;
    }

    @Nullable
    public static FormFactor get(@NotNull String id) {
      for (FormFactor formFactor : FormFactor.values()) {
        if (id.equalsIgnoreCase(formFactor.id)) {
          return formFactor;
        }
      }
      return null;
    }

    @Override
    public String toString() {
      return displayName == null ? id : displayName;
    }

    @Nullable
    public Icon getIcon() {
      return UIUtil.isUnderDarcula() ? myDarkIcon : myLightIcon;
    }
  }

  public static Key<AndroidTargetComboBoxItem> getTargetComboBoxKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_MIN_API + "combo", STEP, AndroidTargetComboBoxItem.class);
  }

  public static Key<Integer> getMinApiLevelKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_MIN_API_LEVEL, WIZARD, Integer.class);
  }

  public static Key<String> getMinApiKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_MIN_API, WIZARD, String.class);
  }

  public static Key<String> getBuildApiKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_BUILD_API_STRING, WIZARD, String.class);
  }

  public static Key<Integer> getTargetApiLevelKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_TARGET_API, WIZARD, Integer.class);
  }

  public static Key<String> getTargetApiStringKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_TARGET_API_STRING, WIZARD, String.class);
  }

  public static Key<Integer> getBuildApiLevelKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_BUILD_API, WIZARD, Integer.class);
  }

  public static Key<String> getLanguageLevelKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + ATTR_JAVA_VERSION, WIZARD, String.class);
  }

  public static Key<Boolean> getInclusionKey(@NotNull FormFactor formFactor) {
    return createKey(formFactor.id + INCLUDE_FORM_FACTOR, WIZARD, Boolean.class);
  }

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
    String name = formFactor.id.replaceAll(INVALID_FILENAME_CHARS, "");
    name = name.replaceAll("\\s", "_");
    return name.toLowerCase();
  }
}

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.ConfigureAndroidProjectStep.INVALID_FILENAME_CHARS;
import static com.android.tools.idea.wizard.NewProjectWizardState.ATTR_MODULE_NAME;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * Utility methods for dealing with Form Factors in Wizards.
 */
public class FormFactorUtils {
  public static final String INCLUDE_FORM_FACTOR = "included";
  public static final String PHONE_TABLET_FORM_FACTOR_NAME = "Phone and Tablet";

  public static ScopedStateStore.Key<Integer> getMinApiLevelKey(@NotNull String formFactorName) {
    return createKey(formFactorName + ATTR_MIN_API_LEVEL, WIZARD, Integer.class);
  }

  public static ScopedStateStore.Key<Integer> getMinApiKey(@NotNull String formFactorName) {
    return createKey(formFactorName + ATTR_MIN_API, WIZARD, Integer.class);
  }

  public static ScopedStateStore.Key<Integer> getTargetApiLevelKey(@NotNull String formFactorName) {
    return createKey(formFactorName + ATTR_TARGET_API, WIZARD, Integer.class);
  }

  public static ScopedStateStore.Key<Integer> getBuildApiLevelKey(@NotNull String formFactorName) {
    return createKey(formFactorName + ATTR_BUILD_API, WIZARD, Integer.class);
  }

  public static ScopedStateStore.Key<String> getLanguageLevelKey(@NotNull String formFactorName) {
    return createKey(formFactorName + ATTR_JAVA_VERSION, WIZARD, String.class);
  }

  public static ScopedStateStore.Key<Boolean> getInclusionKey(@NotNull String formFactorName) {
    return createKey(formFactorName + INCLUDE_FORM_FACTOR, WIZARD, Boolean.class);
  }

  public static ScopedStateStore.Key<String> getModuleNameKey(@NotNull String formFactorName) {
    return createKey(formFactorName + ATTR_MODULE_NAME, WIZARD, String.class);
  }

  public static Map<String, Object> scrubFormFactorPrefixes(@NotNull String formFactorName, @NotNull Map<String, Object> values) {
    Map<String, Object> toReturn = Maps.newHashMapWithExpectedSize(values.size());
    for (String key : values.keySet()) {
      if (key.startsWith(formFactorName)) {
        toReturn.put(key.substring(formFactorName.length()), values.get(key));
      } else {
        toReturn.put(key, values.get(key));
      }
    }
    return toReturn;
  }

  public static String getPropertiesComponentMinSdkKey(@NotNull String formFactorName) {
    return formFactorName + ATTR_MIN_API_LEVEL;
  }

  @NotNull
  public static String getModuleName(@NotNull String formFactor) {
    String name = formFactor.replaceAll(INVALID_FILENAME_CHARS, "");
    name = name.replaceAll("\\s", "_");
    return name.toLowerCase();
  }
}

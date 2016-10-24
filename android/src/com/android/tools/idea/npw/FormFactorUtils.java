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

import com.android.SdkConstants;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.android.tools.idea.npw.FormFactorApiComboBox.AndroidTargetComboBoxItem;
import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.STEP;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.createKey;

/**
 * Utility methods for dealing with Form Factors in Wizards.
 *
 * TODO: After wizard migration, much of this class may go away (as a lot of it is specific to
 * dynamic wizard). Consider folding remaining methods into {@link FormFactor} at that time.
 * @deprecated replaced by {@link com.android.tools.idea.npw.platform.FormFactorUtils}
 */
@Deprecated
public class FormFactorUtils {
  private static final IdDisplay NO_MATCH = IdDisplay.create("no_match", "No Match");
  /*
   * @deprecated Use {@link TemplateMetadata.ATTR_MODULE_NAME} instead.
   */
  @Deprecated
  public static final String ATTR_MODULE_NAME = TemplateMetadata.ATTR_MODULE_NAME;

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
    return createKey(formFactor.id + ATTR_INCLUDE_FORM_FACTOR, WIZARD, Boolean.class);
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

  static Predicate<AndroidTargetComboBoxItem> getMinSdkComboBoxFilter(@NotNull final FormFactor formFactor, final int minSdkLevel) {
    return input -> input != null && doFilter(formFactor, minSdkLevel, SystemImage.DEFAULT_TAG, input.getApiLevel());
  }

  static Predicate<RepoPackage> getMinSdkPackageFilter(@NotNull final FormFactor formFactor, final int minSdkLevel) {
    return input -> input != null && filterPkgDesc(input, formFactor, minSdkLevel);
  }

  public static boolean isFormFactorAvailable(@NotNull FormFactor formFactor, int minSdkLevel, int targetSdkLevel) {
    return doFilter(formFactor,  minSdkLevel, SystemImage.DEFAULT_TAG, targetSdkLevel);
  }

  private static boolean filterPkgDesc(@NotNull RepoPackage p, @NotNull FormFactor formFactor, int minSdkLevel) {
    return isApiType(p) && doFilter(formFactor, minSdkLevel, getTag(p), getFeatureLevel(p));
  }

  private static boolean doFilter(@NotNull FormFactor formFactor, int minSdkLevel, @Nullable IdDisplay tag, int targetSdkLevel) {
    return formFactor.isSupported(tag, targetSdkLevel) && targetSdkLevel >= minSdkLevel;
  }

  private static boolean isApiType(@NotNull RepoPackage repoPackage) {
    return repoPackage.getTypeDetails() instanceof DetailsTypes.ApiDetailsType;
  }

  static int getFeatureLevel(@NotNull RepoPackage repoPackage) {
    return getAndroidVersion(repoPackage).getFeatureLevel();
  }

  @NotNull
  static AndroidVersion getAndroidVersion(@NotNull RepoPackage repoPackage) {
    TypeDetails details = repoPackage.getTypeDetails();
    if (details instanceof DetailsTypes.ApiDetailsType) {
      return ((DetailsTypes.ApiDetailsType)details).getAndroidVersion();
    }
    throw new RuntimeException("Could not determine version");
  }

  /**
   * Return the tag for the specified repository package.
   * We are only interested in 2 package types.
   */
  @Nullable
  static IdDisplay getTag(@NotNull RepoPackage repoPackage) {
    TypeDetails details = repoPackage.getTypeDetails();
    IdDisplay tag = NO_MATCH;
    if (details instanceof DetailsTypes.AddonDetailsType) {
      tag = ((DetailsTypes.AddonDetailsType)details).getTag();
    }
    if (details instanceof DetailsTypes.SysImgDetailsType) {
      DetailsTypes.SysImgDetailsType imgDetailsType = (DetailsTypes.SysImgDetailsType)details;
      if (imgDetailsType.getAbi().equals(SdkConstants.CPU_ARCH_INTEL_ATOM)) {
        tag = imgDetailsType.getTag();
      }
    }
    return tag;
  }
}

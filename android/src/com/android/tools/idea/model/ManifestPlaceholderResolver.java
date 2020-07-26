/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.model;

import com.android.ide.common.gradle.model.IdeBuildTypeContainer;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class that resolves manifest merger placeholder values in gradle module.
 */
public class ManifestPlaceholderResolver {
  /**
   * {@link Pattern} that matches the manifest placeholders with the form ${name}
   */
  public static Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

  private final ImmutableMap<String, Object> myPlaceholders;

  public ManifestPlaceholderResolver(@NotNull Module module) {
    AndroidModuleModel model = AndroidModuleModel.get(module);

    if (model != null) {
      ImmutableMap.Builder<String, Object> placeholdersBuilder = ImmutableMap.builder();

      IdeVariant selectedVariant = model.getSelectedVariant();
      IdeBuildTypeContainer buildType = model.findBuildType(selectedVariant.getBuildType());
      if (buildType != null) {
        placeholdersBuilder.putAll(buildType.getBuildType().getManifestPlaceholders());
      }
      // flavors and default config
      placeholdersBuilder.putAll(selectedVariant.getMergedFlavor().getManifestPlaceholders());

      myPlaceholders = placeholdersBuilder.build();
    } else {
      myPlaceholders = ImmutableMap.of();
    }
  }

  /**
   * Returns a map of all the existing placeholders and its associated value.
   */
  @NotNull
  public ImmutableMap<String, Object> getPlaceholders() {
    return myPlaceholders;
  }

  /**
   * Replaces any manifest placeholders in the passed value string with the resolved values.
   */
  @Nullable
  public String resolve(@Nullable String value) {
    if (value == null) {
      return null;
    }

    if (myPlaceholders.isEmpty() || !value.contains("${")) {
      // No placeholders defined or no placeholders in the string
      return value;
    }

    StringBuffer output = new StringBuffer();
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
    while (matcher.find()) {
      Object placeholderValue = myPlaceholders.get(matcher.group(1));
      // Now replace the placeholder with the value. If the value is null, we just simply leave the placeholder using $0.
      // $0, when passed to appendReplacement, will be replaced with the original matched value, in our case, the placeholder string.
      matcher.appendReplacement(output, placeholderValue != null ? Matcher.quoteReplacement(placeholderValue.toString()) : "$0");
    }
    matcher.appendTail(output);

    return output.toString();
  }
}

/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.settings;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.CUSTOM;
import static com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile.REPOSITORIES_MODE_ENUM_MAP;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.RawText;
import com.android.tools.idea.gradle.dsl.api.settings.RepositoriesModePropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.ResolvedPropertyModelImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RepositoriesModePropertyModelImpl extends ResolvedPropertyModelImpl implements RepositoriesModePropertyModel {
  final GradlePropertyModel model;

  public RepositoriesModePropertyModelImpl(GradlePropertyModelImpl model) {
    super(model);
    this.model = model;
  }

  @Override
  public @NotNull ValueType getValueType() {
    return CUSTOM;
  }

  public @Nullable String getRepositoriesMode() {
    String value = model.toString();
    if (value == null) return null;
    if (value.startsWith("RepositoriesMode.")) {
      value = value.substring("RepositoriesMode.".length());
    }
    return value;
  }

  public void setRepositoriesMode(@NotNull String value) {
    if (REPOSITORIES_MODE_ENUM_MAP.containsKey(value)) {
      String qualifiedValue = "RepositoriesMode." + value;
      model.setValue(new RawText(qualifiedValue, qualifiedValue));
    }
  }
}

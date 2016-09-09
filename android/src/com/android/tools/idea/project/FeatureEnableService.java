/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.project;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FeatureEnableService {
  private static ExtensionPointName<FeatureEnableService> EP_NAME =
    ExtensionPointName.create("com.android.project.featureEnableService");

  @Nullable
  public static FeatureEnableService getInstance(@NotNull Project project) {
    for (FeatureEnableService extension : EP_NAME.getExtensions()) {
      if (extension.isApplicable(project)) {
        return extension;
      }
    }
    return null;
  }

  protected abstract boolean isApplicable(@NotNull Project project);

  public abstract boolean isLayoutEditorEnabled();
}

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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.configurables.ui.PsdUISettings;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public final class ArtifactDependencySpecs {
  private ArtifactDependencySpecs() {
  }

  @NotNull
  public static String asText(@NotNull ArtifactDependencySpec spec) {
    boolean showGroupId = PsdUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID;
    StringBuilder text = new StringBuilder();
    if (showGroupId && isNotEmpty(spec.group)) {
      text.append(spec.group).append(GRADLE_PATH_SEPARATOR);
    }
    text.append(spec.name);
    if (isNotEmpty(spec.version)) {
      text.append(GRADLE_PATH_SEPARATOR).append(spec.version);
    }
    return text.toString();
  }
}

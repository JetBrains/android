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
package com.android.tools.idea.gradle.structure.model.android;

import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PsdModuleDependencyModel extends PsdAndroidDependencyModel {
  @NotNull private final String myGradlePath;

  @Nullable private final String myVariant;
  @Nullable private final String myName;

  PsdModuleDependencyModel(@NotNull PsdAndroidModuleModel parent,
                           @NotNull String gradlePath,
                           @Nullable String variant,
                           @Nullable Module module,
                           @Nullable PsdAndroidArtifactModel artifactModel,
                           @Nullable ModuleDependencyModel parsedModel) {
    super(parent, artifactModel, parsedModel);
    myGradlePath = gradlePath;
    myVariant = variant;
    String name = null;
    if (module != null) {
      name = module.getName();
    }
    else if (parsedModel != null) {
      name = parsedModel.name();
    }
    myName = name;
  }

  @NotNull
  public String getGradlePath() {
    return myGradlePath;
  }

  @Nullable
  public String getModuleVariant() {
    return myVariant;
  }

  @Nullable
  public String getName() {
    return myName;
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return AllIcons.Nodes.Module;
  }

  @Override
  @NotNull
  public String getValueAsText() {
    if (myName != null) {
      return myName;
    }
    return myGradlePath;
  }
}

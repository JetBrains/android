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
  @NotNull private final String myName;

  @Nullable private final String myVariant;
  @Nullable private final Module myResolvedModel;

  PsdModuleDependencyModel(@NotNull PsdAndroidModuleModel parent,
                           @NotNull String gradlePath,
                           @Nullable String variant,
                           @Nullable Module resolvedModel,
                           @Nullable PsdAndroidArtifactModel artifactModel,
                           @Nullable ModuleDependencyModel parsedModel) {
    super(parent, artifactModel, parsedModel);
    myGradlePath = gradlePath;
    myVariant = variant;
    myResolvedModel = resolvedModel;
    String name = null;
    if (resolvedModel != null) {
      name = resolvedModel.getName();
    }
    else if (parsedModel != null) {
      name = parsedModel.name();
    }
    assert name != null;
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

  @Override
  @NotNull
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
    return myName;
  }

  @Override
  @Nullable
  public Module getResolvedModel() {
    return myResolvedModel;
  }
}

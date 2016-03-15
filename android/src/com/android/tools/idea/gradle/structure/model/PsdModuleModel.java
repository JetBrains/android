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
package com.android.tools.idea.gradle.structure.model;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class PsdModuleModel extends PsdChildModel {
  @NotNull private final String myGradlePath;

  // Module can be null in the case of new modules created in the PSD.
  @NotNull private final Module myResolvedModel;

  private boolean myInitParsedModel;
  private GradleBuildModel myParsedModel;
  private String myModuleName;

  protected PsdModuleModel(@NotNull PsdProjectModel parent,
                           @NotNull Module resolvedModel,
                           @NotNull String moduleGradlePath) {
    super(parent);
    myResolvedModel = resolvedModel;
    myGradlePath = moduleGradlePath;
    myModuleName = resolvedModel.getName();
  }

  @Override
  @NotNull
  public PsdProjectModel getParent() {
    return (PsdProjectModel)super.getParent();
  }

  @Override
  @NotNull
  public String getName() {
    return myModuleName;
  }

  @NotNull
  public String getGradlePath() {
    return myGradlePath;
  }

  @Override
  public boolean isEditable() {
    return myParsedModel != null;
  }

  @Nullable
  public GradleBuildModel getParsedModel() {
    if (!myInitParsedModel) {
      myInitParsedModel = true;
      myParsedModel = GradleBuildModel.get(myResolvedModel);
    }
    return myParsedModel;
  }

  @Override
  @NotNull
  public Module getResolvedModel() {
    return myResolvedModel;
  }

  public Icon getModuleIcon() {
    return AllIcons.Nodes.Module;
  }
}

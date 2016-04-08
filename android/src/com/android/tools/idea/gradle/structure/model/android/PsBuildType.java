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

import com.android.builder.model.BuildType;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.dsl.model.android.BuildTypeModel;
import com.android.tools.idea.gradle.structure.model.PsChildModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsBuildType extends PsChildModel implements PsAndroidModel {
  @Nullable private final BuildType myResolvedModel;
  @Nullable private final BuildTypeModel myParsedModel;

  private String myName = "";

  PsBuildType(@NotNull PsAndroidModule parent, @Nullable BuildType resolvedModel, @Nullable BuildTypeModel parsedModel) {
    super(parent);
    myResolvedModel = resolvedModel;
    myParsedModel = parsedModel;

    if (resolvedModel != null) {
      myName = resolvedModel.getName();
    }
    else if (parsedModel != null) {
      myName = parsedModel.name();
    }
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public PsAndroidModule getParent() {
    return (PsAndroidModule)super.getParent();
  }

  @Override
  public boolean isDeclared() {
    return myParsedModel != null;
  }

  @Nullable
  @Override
  public BuildType getResolvedModel() {
    return myResolvedModel;
  }

  @Override
  @NotNull
  public AndroidGradleModel getGradleModel() {
    return getParent().getGradleModel();
  }
}

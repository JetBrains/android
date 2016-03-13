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

import com.android.builder.model.ProductFlavor;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.dsl.model.android.ProductFlavorModel;
import com.android.tools.idea.gradle.structure.model.PsChildModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsProductFlavor extends PsChildModel implements PsAndroidModel {
  @Nullable private final ProductFlavor myResolvedModel;
  @Nullable private final ProductFlavorModel myParsedModel;

  private String myName = "";

  PsProductFlavor(@NotNull PsAndroidModule parent,
                  @Nullable ProductFlavor resolvedModel,
                  @Nullable ProductFlavorModel parsedModel) {
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
  public AndroidGradleModel getGradleModel() {
    return getParent().getGradleModel();
  }

  @Override
  @NotNull
  public PsAndroidModule getParent() {
    return (PsAndroidModule)super.getParent();
  }

  @Override
  @Nullable
  public ProductFlavor getResolvedModel() {
    return myResolvedModel;
  }

  @Override
  public boolean isEditable() {
    return myParsedModel != null;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    myName = name;
    setModified(true);
  }
}

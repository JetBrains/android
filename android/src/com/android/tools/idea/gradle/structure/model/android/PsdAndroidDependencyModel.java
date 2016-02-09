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

import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyModel;
import com.android.tools.idea.gradle.structure.model.PsdChildModel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public abstract class PsdAndroidDependencyModel extends PsdChildModel {
  @NotNull private final Set<String> myVariants = Sets.newHashSet();

  @Nullable private DependencyModel myParsedModel;

  PsdAndroidDependencyModel(@NotNull PsdAndroidModuleModel parent,
                            @Nullable DependencyModel parsedModel) {
    super(parent);
    myParsedModel = parsedModel;
  }

  @Override
  @NotNull
  public PsdAndroidModuleModel getParent() {
    return (PsdAndroidModuleModel)super.getParent();
  }

  void addContainer(@NotNull PsdVariantModel container) {
    myVariants.add(container.getName());
  }

  @NotNull
  public List<String> getVariants() {
    return ImmutableList.copyOf(myVariants);
  }

  public boolean isInVariant(@NotNull PsdVariantModel variantName) {
    return myVariants.contains(variantName.getName());
  }

  @Nullable
  public String getConfigurationName() {
    return myParsedModel != null ? myParsedModel.configurationName() : null;
  }

  @Override
  public boolean isEditable() {
    return myParsedModel != null;
  }

  @Nullable
  protected DependencyModel getParsedModel() {
    return myParsedModel;
  }

  @NotNull
  public abstract String getValueAsText();
}

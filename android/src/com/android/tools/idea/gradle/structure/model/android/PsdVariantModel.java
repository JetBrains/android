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

import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.structure.model.PsdChildModel;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public class PsdVariantModel extends PsdChildModel {
  @NotNull private final String myName;
  @NotNull private final List<String> myProductFlavors;

  @Nullable private final Variant myGradleModel;

  private PsdAndroidArtifactModels myArtifactModels;

  PsdVariantModel(@NotNull PsdAndroidModuleModel parent,
                  @NotNull String name,
                  @NotNull List<String> productFlavors,
                  @Nullable Variant gradleModel) {
    super(parent);
    myName = name;
    myProductFlavors = productFlavors;
    myGradleModel = gradleModel;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public PsdAndroidModuleModel getParent() {
    return (PsdAndroidModuleModel)super.getParent();
  }

  @Nullable
  public Variant getGradleModel() {
    return myGradleModel;
  }

  @Nullable
  public PsdAndroidArtifactModel findArtifact(@NotNull String name) {
    return getOrCreateArtifactModels().findArtifact(name);
  }

  @NotNull
  public Collection<PsdAndroidArtifactModel> getArtifacts() {
    return getOrCreateArtifactModels().getArtifacts();
  }

  @NotNull
  private PsdAndroidArtifactModels getOrCreateArtifactModels() {
    if (myArtifactModels == null) {
      myArtifactModels = new PsdAndroidArtifactModels(this);
    }
    return myArtifactModels;
  }

  @NotNull
  public List<String> getProductFlavors() {
    return ImmutableList.copyOf(myProductFlavors);
  }

  @Override
  public boolean isEditable() {
    return false;
  }

  @Override
  @Nullable
  public Icon getIcon() {
    return AndroidIcons.Variant;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PsdVariantModel that = (PsdVariantModel)o;
    return Objects.equal(myName, that.myName);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myName);
  }

  @Override
  public String toString() {
    return myName;
  }
}

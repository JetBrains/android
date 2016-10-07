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
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.structure.model.PsChildModel;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

public class PsVariant extends PsChildModel implements PsAndroidModel {
  @NotNull private final String myName;
  @NotNull private final String myBuildType;
  @NotNull private final List<String> myProductFlavors;

  @Nullable private final Variant myResolvedModel;

  private PsAndroidArtifactCollection myArtifactCollection;

  public PsVariant(@NotNull PsAndroidModule parent,
                   @NotNull String name,
                   @NotNull String buildType,
                   @NotNull List<String> productFlavors,
                   @Nullable Variant resolvedModel) {
    super(parent);
    myName = name;
    myBuildType = buildType;
    myProductFlavors = productFlavors;
    myResolvedModel = resolvedModel;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
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
  public Variant getResolvedModel() {
    return myResolvedModel;
  }

  @NotNull
  public PsBuildType getBuildType() {
    PsBuildType buildType = getParent().findBuildType(myBuildType);
    assert buildType != null;
    return buildType;
  }

  @Nullable
  public PsAndroidArtifact findArtifact(@NotNull String name) {
    return getOrCreateArtifactCollection().findElement(name, PsAndroidArtifact.class);
  }

  public void forEachArtifact(@NotNull Consumer<PsAndroidArtifact> consumer) {
    getOrCreateArtifactCollection().forEach(consumer);
  }

  @NotNull
  private PsAndroidArtifactCollection getOrCreateArtifactCollection() {
    return myArtifactCollection == null ? myArtifactCollection = new PsAndroidArtifactCollection(this) : myArtifactCollection;
  }

  public void forEachProductFlavor(@NotNull Consumer<PsProductFlavor> consumer) {
    PsAndroidModule module = getParent();
    for (String name : myProductFlavors) {
      PsProductFlavor productFlavor = module.findProductFlavor(name);
      consumer.accept(productFlavor);
    }
  }

  @TestOnly
  @NotNull
  public List<String> getProductFlavors() {
    return ImmutableList.copyOf(myProductFlavors);
  }

  @Override
  public boolean isDeclared() {
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
    PsVariant that = (PsVariant)o;
    return Objects.equal(myName, that.myName);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myName);
  }
}

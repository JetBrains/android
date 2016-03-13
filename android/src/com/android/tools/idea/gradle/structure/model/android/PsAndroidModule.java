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

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsParsedDependencies;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.intellij.openapi.module.Module;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public class PsAndroidModule extends PsModule implements PsAndroidModel {
  @NotNull private final AndroidGradleModel myGradleModel;

  private PsParsedDependencies myParsedDependencies;
  private PsVariantCollection myVariantCollection;
  private PsProductFlavorCollection myProductFlavorCollection;
  private PsAndroidDependencyCollection myDependencyCollection;

  public PsAndroidModule(@NotNull PsProject parent,
                         @NotNull Module resolvedModel,
                         @NotNull String gradlePath,
                         @NotNull AndroidGradleModel gradleModel) {
    super(parent, resolvedModel, gradlePath);
    myGradleModel = gradleModel;
  }

  @NotNull
  public List<PsProductFlavor> getProductFlavors() {
    return getOrCreateProductFlavorCollection().getElements();
  }

  @Nullable
  public PsProductFlavor findProductFlavor(@NotNull String name) {
    return getOrCreateProductFlavorCollection().findElement(name, PsProductFlavor.class);
  }

  @NotNull
  private PsProductFlavorCollection getOrCreateProductFlavorCollection() {
    if (myProductFlavorCollection == null) {
      myProductFlavorCollection = new PsProductFlavorCollection(this);
    }
    return myProductFlavorCollection;
  }

  @NotNull
  public List<PsVariant> getVariants() {
    return getOrCreateVariantCollection().getElements();
  }

  @Nullable
  public PsVariant findVariant(@NotNull String variantName) {
    return getOrCreateVariantCollection().find(variantName);
  }

  @NotNull
  public List<PsAndroidDependency> getDeclaredDependencies() {
    return getOrCreateDependencyCollection().getDeclaredDependencies();
  }

  @NotNull
  public List<PsAndroidDependency> getDependencies() {
    return getOrCreateDependencyCollection().getElements();
  }

  @NotNull
  public Collection<PsModuleDependency> getModuleDependencies() {
    return getOrCreateDependencyCollection().getModuleDependencies();
  }

  @Nullable
  public PsLibraryDependency findLibraryDependency(@NotNull String compactNotation) {
    return getOrCreateDependencyCollection().findElement(compactNotation, PsLibraryDependency.class);
  }

  @Nullable
  public PsLibraryDependency findLibraryDependency(@NotNull PsArtifactDependencySpec dependency) {
    return getOrCreateDependencyCollection().findElement(dependency.toString(), PsLibraryDependency.class);
  }

  @NotNull
  private PsAndroidDependencyCollection getOrCreateDependencyCollection() {
    if (myDependencyCollection == null) {
      myDependencyCollection = new PsAndroidDependencyCollection(this);
    }
    return myDependencyCollection;
  }

  @NotNull
  public PsParsedDependencies getParsedDependencies() {
    if (myParsedDependencies == null) {
      myParsedDependencies = new PsParsedDependencies(getParsedModel());
    }
    return myParsedDependencies;
  }

  @NotNull
  private PsVariantCollection getOrCreateVariantCollection() {
    if (myVariantCollection == null) {
      myVariantCollection = new PsVariantCollection(this);
    }
    return myVariantCollection;
  }

  @Override
  @NotNull
  public AndroidGradleModel getAndroidGradleModel() {
    return myGradleModel;
  }

  @Override
  public Icon getModuleIcon() {
    if (myGradleModel.getAndroidProject().isLibrary()) {
      return AndroidIcons.LibraryModule;
    }
    return AndroidIcons.AppModule;
  }
}

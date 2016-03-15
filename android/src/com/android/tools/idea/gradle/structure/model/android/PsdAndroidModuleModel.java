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
import com.android.tools.idea.gradle.structure.model.PsdArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsdModuleModel;
import com.android.tools.idea.gradle.structure.model.PsdParsedDependencyModels;
import com.android.tools.idea.gradle.structure.model.PsdProjectModel;
import com.intellij.openapi.module.Module;
import com.intellij.util.containers.Predicate;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public class PsdAndroidModuleModel extends PsdModuleModel implements PsdAndroidModel {
  @NotNull private final AndroidGradleModel myGradleModel;

  private PsdParsedDependencyModels myParsedDependencyModels;
  private PsdProductFlavorModelCollection myProductFlavorModelCollection;
  private PsdVariantModelCollection myVariantModelCollection;
  private PsdAndroidDependencyModelCollection myDependencyModelCollection;

  public PsdAndroidModuleModel(@NotNull PsdProjectModel parent,
                               @NotNull Module module,
                               @NotNull String gradlePath,
                               @NotNull AndroidGradleModel gradleModel) {
    super(parent, module, gradlePath);
    myGradleModel = gradleModel;
  }

  @NotNull
  public List<PsdProductFlavorModel> getProductFlavors() {
    return getOrCreateProductFlavorModelCollection().getElements();
  }

  @Nullable
  public PsdProductFlavorModel findProductFlavorModel(@NotNull String name) {
    return getOrCreateProductFlavorModelCollection().findElement(name, PsdProductFlavorModel.class);
  }

  @NotNull
  private PsdProductFlavorModelCollection getOrCreateProductFlavorModelCollection() {
    if (myProductFlavorModelCollection == null) {
      myProductFlavorModelCollection = new PsdProductFlavorModelCollection(this);
    }
    return myProductFlavorModelCollection;
  }

  @NotNull
  public List<PsdVariantModel> getVariants() {
    return getOrCreateVariantModelCollection().getElements();
  }

  @Nullable
  public PsdVariantModel findVariantModel(@NotNull String variantName) {
    return getOrCreateVariantModelCollection().find(variantName);
  }

  @NotNull
  public List<PsdAndroidDependencyModel> getDeclaredDependencies() {
    return getOrCreateDependencyModelCollection().getDeclaredDependencies();
  }

  @NotNull
  public List<PsdAndroidDependencyModel> getDependencies() {
    return getOrCreateDependencyModelCollection().getElements();
  }

  @NotNull
  public Collection<PsdModuleDependencyModel> getModuleDependencies() {
    return getOrCreateDependencyModelCollection().getModuleDependencies();
  }

  @Nullable
  public PsdLibraryDependencyModel findLibraryDependency(@NotNull String compactNotation) {
    return getOrCreateDependencyModelCollection().findElement(compactNotation, PsdLibraryDependencyModel.class);
  }

  @Nullable
  public PsdLibraryDependencyModel findLibraryDependency(@NotNull PsdArtifactDependencySpec dependency) {
    return getOrCreateDependencyModelCollection().findElement(dependency.toString(), PsdLibraryDependencyModel.class);
  }

  @NotNull
  private PsdAndroidDependencyModelCollection getOrCreateDependencyModelCollection() {
    if (myDependencyModelCollection == null) {
      myDependencyModelCollection = new PsdAndroidDependencyModelCollection(this);
    }
    return myDependencyModelCollection;
  }

  @NotNull
  public PsdParsedDependencyModels getParsedDependencyModels() {
    if (myParsedDependencyModels == null) {
      myParsedDependencyModels = new PsdParsedDependencyModels(getParsedModel());
    }
    return myParsedDependencyModels;
  }

  @NotNull
  private PsdVariantModelCollection getOrCreateVariantModelCollection() {
    if (myVariantModelCollection == null) {
      myVariantModelCollection = new PsdVariantModelCollection(this);
    }
    return myVariantModelCollection;
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

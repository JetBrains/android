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
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public class PsdAndroidModuleModel extends PsdModuleModel {
  @NotNull private final AndroidGradleModel myGradleModel;

  private PsdProductFlavorModels myProductFlavorModels;
  private PsdVariantModels myVariantModels;
  private PsdParsedDependencyModels myParsedDependencyModels;
  private PsdAndroidDependencyModels myDependencyModels;

  public PsdAndroidModuleModel(@NotNull PsdProjectModel parent,
                               @NotNull Module module,
                               @NotNull String gradlePath,
                               @NotNull AndroidGradleModel gradleModel) {
    super(parent, module, gradlePath);
    myGradleModel = gradleModel;
  }

  @NotNull
  public Collection<PsdProductFlavorModel> getProductFlavorModels() {
    return getOrCreateProductFlavorModels().getValues();
  }

  @Nullable
  public PsdProductFlavorModel findProductFlavorModel(@NotNull String productFlavorName) {
    return getOrCreateProductFlavorModels().findProductFlavorModel(productFlavorName);
  }

  @NotNull
  private PsdProductFlavorModels getOrCreateProductFlavorModels() {
    if (myProductFlavorModels == null) {
      myProductFlavorModels = new PsdProductFlavorModels(this);
    }
    return myProductFlavorModels;
  }

  @NotNull
  public Collection<PsdVariantModel> getVariantModels() {
    return getOrCreateVariantModels().getValues();
  }

  @Nullable
  public PsdVariantModel findVariantModel(@NotNull String variantName) {
    return getOrCreateVariantModels().find(variantName);
  }

  @NotNull
  public List<PsdAndroidDependencyModel> getDeclaredDependencies() {
    return getOrCreateDependencyModels().getDeclaredDependencies();
  }

  @NotNull
  public List<PsdAndroidDependencyModel> getDependencies() {
    return getOrCreateDependencyModels().getDependencies();
  }

  @NotNull
  public Collection<PsdModuleDependencyModel> getModuleDependencies() {
    return getOrCreateDependencyModels().getModuleDependencies();
  }

  @Nullable
  public PsdModuleDependencyModel findModuleDependency(@NotNull String dependency) {
    return getOrCreateDependencyModels().findModuleDependency(dependency);
  }

  @Nullable
  public PsdLibraryDependencyModel findLibraryDependency(@NotNull String compactNotation) {
    PsdArtifactDependencySpec spec = PsdArtifactDependencySpec.create(compactNotation);
    assert spec != null;
    return findLibraryDependency(spec);
  }

  @Nullable
  public PsdLibraryDependencyModel findLibraryDependency(@NotNull PsdArtifactDependencySpec dependency) {
    return getOrCreateDependencyModels().findLibraryDependency(dependency);
  }

  @NotNull
  private PsdAndroidDependencyModels getOrCreateDependencyModels() {
    if (myDependencyModels == null) {
      myDependencyModels = new PsdAndroidDependencyModels(this);
    }
    return myDependencyModels;
  }

  @NotNull
  public PsdParsedDependencyModels getParsedDependencyModels() {
    if (myParsedDependencyModels == null) {
      myParsedDependencyModels = new PsdParsedDependencyModels(getParsedModel());
    }
    return myParsedDependencyModels;
  }

  @NotNull
  private PsdVariantModels getOrCreateVariantModels() {
    if (myVariantModels == null) {
      myVariantModels = new PsdVariantModels(this);
    }
    return myVariantModels;
  }

  @NotNull
  public AndroidGradleModel getGradleModel() {
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

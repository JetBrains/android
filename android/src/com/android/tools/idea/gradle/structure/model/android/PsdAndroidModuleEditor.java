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
import com.android.tools.idea.gradle.structure.model.PsdModuleEditor;
import com.android.tools.idea.gradle.structure.model.PsdParsedDependencies;
import com.android.tools.idea.gradle.structure.model.PsdProjectEditor;
import com.intellij.openapi.module.Module;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public class PsdAndroidModuleEditor extends PsdModuleEditor {
  @NotNull private final AndroidGradleModel myGradleModel;

  private PsdProductFlavorEditors myProductFlavorEditors;
  private PsdVariantEditors myVariantEditors;
  private PsdParsedDependencies myParsedDependencies;
  private PsdAndroidDependencyEditors myDependencyEditors;

  public PsdAndroidModuleEditor(@NotNull PsdProjectEditor parent,
                                @NotNull Module module,
                                @NotNull String gradlePath,
                                @NotNull AndroidGradleModel gradleModel) {
    super(parent, module, gradlePath);
    myGradleModel = gradleModel;
  }

  @NotNull
  public Collection<PsdProductFlavorEditor> getProductFlavorEditors() {
    return getOrCreateProductFlavorEditors().getValues();
  }

  @Nullable
  public PsdProductFlavorEditor findProductFlavorEditor(@NotNull String productFlavorName) {
    return getOrCreateProductFlavorEditors().findProductFlavorEditor(productFlavorName);
  }

  @NotNull
  private PsdProductFlavorEditors getOrCreateProductFlavorEditors() {
    if (myProductFlavorEditors == null) {
      myProductFlavorEditors = new PsdProductFlavorEditors(this);
    }
    return myProductFlavorEditors;
  }

  @NotNull
  public Collection<PsdVariantEditor> getVariantEditors() {
    return getOrCreateVariantEditors().getValues();
  }

  @Nullable
  public PsdVariantEditor findVariantEditor(@NotNull String variantName) {
    return getOrCreateVariantEditors().find(variantName);
  }

  @NotNull
  public List<PsdAndroidDependencyEditor> getDeclaredDependencies() {
    return getOrCreateDependencyEditors().getDeclaredDependencies();
  }

  @NotNull
  private PsdAndroidDependencyEditors getOrCreateDependencyEditors() {
    if (myDependencyEditors == null) {
      myDependencyEditors = new PsdAndroidDependencyEditors(this);
    }
    return myDependencyEditors;
  }

  @NotNull
  public PsdParsedDependencies getParsedDependencies() {
    if (myParsedDependencies == null) {
      myParsedDependencies = new PsdParsedDependencies(getParsedModel());
    }
    return myParsedDependencies;
  }

  @NotNull
  private PsdVariantEditors getOrCreateVariantEditors() {
    if (myVariantEditors == null) {
      myVariantEditors = new PsdVariantEditors(this);
    }
    return myVariantEditors;
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

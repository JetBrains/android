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
import java.util.function.Consumer;

public class PsAndroidModule extends PsModule implements PsAndroidModel {
  @NotNull private final AndroidGradleModel myGradleModel;

  private PsParsedDependencies myParsedDependencies;
  private PsVariantCollection myVariantCollection;
  private PsBuildTypeCollection myBuildTypeCollection;
  private PsProductFlavorCollection myProductFlavorCollection;
  private PsAndroidDependencyCollection myDependencyCollection;

  public PsAndroidModule(@NotNull PsProject parent,
                         @NotNull Module resolvedModel,
                         @NotNull String gradlePath,
                         @NotNull AndroidGradleModel gradleModel) {
    super(parent, resolvedModel, gradlePath);
    myGradleModel = gradleModel;
  }

  @Nullable
  public PsBuildType findBuildType(@NotNull String buildType) {
    return getOrCreateBuildTypeCollection().findElement(buildType, PsBuildType.class);
  }

  @NotNull
  private PsBuildTypeCollection getOrCreateBuildTypeCollection() {
    return myBuildTypeCollection == null ? myBuildTypeCollection = new PsBuildTypeCollection(this) : myBuildTypeCollection;
  }

  public void forEachProductFlavor(@NotNull Consumer<PsProductFlavor> consumer) {
    getOrCreateProductFlavorCollection().forEach(consumer);
  }

  @Nullable
  public PsProductFlavor findProductFlavor(@NotNull String name) {
    return getOrCreateProductFlavorCollection().findElement(name, PsProductFlavor.class);
  }

  @NotNull
  private PsProductFlavorCollection getOrCreateProductFlavorCollection() {
    return myProductFlavorCollection == null ? myProductFlavorCollection = new PsProductFlavorCollection(this) : myProductFlavorCollection;
  }

  public void forEachVariant(@NotNull Consumer<PsVariant> con) {
    getOrCreateVariantCollection().forEach(con);
  }

  @Nullable
  public PsVariant findVariant(@NotNull String name) {
    return getOrCreateVariantCollection().findElement(name, PsVariant.class);
  }

  @NotNull
  private PsVariantCollection getOrCreateVariantCollection() {
    return myVariantCollection == null ? myVariantCollection = new PsVariantCollection(this) : myVariantCollection;
  }

  public void forEachDeclaredDependency(@NotNull Consumer<PsAndroidDependency> consumer) {
    getOrCreateDependencyCollection().forEachDeclaredDependency(consumer);
  }

  public void forEachDependency(@NotNull Consumer<PsAndroidDependency> consumer) {
    getOrCreateDependencyCollection().forEach(consumer);
  }

  public void forEachModuleDependency(@NotNull Consumer<PsModuleDependency> consumer) {
    getOrCreateDependencyCollection().forEachModuleDependency(consumer);
  }

  @Nullable
  public PsLibraryDependency findLibraryDependency(@NotNull String compactNotation) {
    return getOrCreateDependencyCollection().findElement(compactNotation, PsLibraryDependency.class);
  }

  @Nullable
  public PsLibraryDependency findLibraryDependency(@NotNull PsArtifactDependencySpec spec) {
    return getOrCreateDependencyCollection().findElement(spec.toString(), PsLibraryDependency.class);
  }

  @NotNull
  private PsAndroidDependencyCollection getOrCreateDependencyCollection() {
    return myDependencyCollection == null ? myDependencyCollection = new PsAndroidDependencyCollection(this) : myDependencyCollection;
  }

  @NotNull
  public PsParsedDependencies getParsedDependencies() {
    return myParsedDependencies == null ? myParsedDependencies = new PsParsedDependencies(getParsedModel()) : myParsedDependencies;
  }

  @Override
  @NotNull
  public AndroidGradleModel getGradleModel() {
    return myGradleModel;
  }

  @Override
  public Icon getIcon() {
    return myGradleModel.getAndroidProject().isLibrary() ?  AndroidIcons.LibraryModule : AndroidIcons.AppModule;
  }

  @Override
  @NotNull
  public String getGradlePath() {
    String gradlePath = super.getGradlePath();
    assert gradlePath != null;
    return gradlePath;
  }

  @Override
  @NotNull
  public Module getResolvedModel() {
    Module model = super.getResolvedModel();
    assert model != null;
    return model;
  }
}

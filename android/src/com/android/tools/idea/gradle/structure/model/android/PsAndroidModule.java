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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsParsedDependencies;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.repositories.search.AndroidSdkRepositories;
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.tools.idea.gradle.util.GradleUtil.getAndroidModuleIcon;

public class PsAndroidModule extends PsModule implements PsAndroidModel {
  @NotNull private final AndroidModuleModel myGradleModel;

  private PsBuildTypeCollection myBuildTypeCollection;
  private PsProductFlavorCollection myProductFlavorCollection;
  private PsVariantCollection myVariantCollection;
  private PsAndroidDependencyCollection myDependencyCollection;
  private PsSigningConfigCollection mySigningConfigCollection;

  public PsAndroidModule(@NotNull PsProject parent,
                         @NotNull Module resolvedModel,
                         @NotNull String gradlePath,
                         @NotNull AndroidModuleModel gradleModel,
                         @NotNull GradleBuildModel parsedModel) {
    super(parent, resolvedModel, gradlePath, parsedModel);
    myGradleModel = gradleModel;
  }

  @Override
  public boolean canDependOn(@NotNull PsModule module) {
    if (module instanceof PsAndroidModule) {
      PsAndroidModule androidModule = (PsAndroidModule)module;
      return androidModule.isLibrary();
    }
    // 'module' is either a Java library or an AAR module.
    return true;
  }

  public boolean isLibrary() {
    return myGradleModel.getAndroidProject().getProjectType() != PROJECT_TYPE_APP;
  }

  @Nullable
  public PsBuildType findBuildType(@NotNull String buildType) {
    return getOrCreateBuildTypeCollection().findElement(buildType, PsBuildType.class);
  }

  public void forEachBuildType(@NotNull Consumer<PsBuildType> consumer) {
    getOrCreateBuildTypeCollection().forEach(consumer);
  }

  @NotNull
  private PsBuildTypeCollection getOrCreateBuildTypeCollection() {
    return myBuildTypeCollection == null ? myBuildTypeCollection = new PsBuildTypeCollection(this) : myBuildTypeCollection;
  }

  public Collection<String> getFlavorDimensions() {
    LinkedHashSet<String> result = Sets.newLinkedHashSet();
    GradleBuildModel parsedModel = getParsedModel();
    AndroidModel parsedAndroidModel = parsedModel != null ? parsedModel.android() : null;
    result.addAll(getGradleModel().getAndroidProject().getFlavorDimensions());
    List<GradlePropertyModel> parsedFlavorDimensions = (parsedAndroidModel != null) ?
                                                       parsedAndroidModel.flavorDimensions().toList() : null;
    if (parsedFlavorDimensions != null) {
      result.addAll(parsedFlavorDimensions.stream().map(v -> v.toString()).collect(Collectors.toList()));
    }
    return result;
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

  public void forEachModuleDependency(@NotNull Consumer<PsModuleAndroidDependency> consumer) {
    getOrCreateDependencyCollection().forEachModuleDependency(consumer);
  }

  @Nullable
  public PsLibraryAndroidDependency findLibraryDependency(@NotNull String compactNotation) {
    return getOrCreateDependencyCollection().findElement(compactNotation, PsLibraryAndroidDependency.class);
  }

  @Nullable
  public PsLibraryAndroidDependency findLibraryDependency(@NotNull PsArtifactDependencySpec spec) {
    return getOrCreateDependencyCollection().findElement(spec);
  }

  @Nullable
  public PsModuleAndroidDependency findModuleDependency(@NotNull String modulePath) {
    return getOrCreateDependencyCollection().findElement(modulePath, PsModuleAndroidDependency.class);
  }

  @NotNull
  private PsAndroidDependencyCollection getOrCreateDependencyCollection() {
    return myDependencyCollection == null ? myDependencyCollection = new PsAndroidDependencyCollection(this) : myDependencyCollection;
  }

  @Nullable
  public PsSigningConfig findSigningConfig(@NotNull String signingConfig) {
    return getOrCreateSigningConfigCollection().findElement(signingConfig, PsSigningConfig.class);
  }

  public void forEachSigningConfig(@NotNull Consumer<PsSigningConfig> consumer) {
    getOrCreateSigningConfigCollection().forEach(consumer);
  }

  @NotNull
  private PsSigningConfigCollection getOrCreateSigningConfigCollection() {
    return mySigningConfigCollection == null ? mySigningConfigCollection = new PsSigningConfigCollection(this) : mySigningConfigCollection;
  }

  @Override
  @NotNull
  public AndroidModuleModel getGradleModel() {
    return myGradleModel;
  }

  @Override
  public Icon getIcon() {
    return getAndroidModuleIcon(myGradleModel);
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

  @Override
  @NotNull
  public List<ArtifactRepository> getArtifactRepositories() {
    List<ArtifactRepository> repositories = Lists.newArrayList();
    populateRepositories(repositories);
    ArtifactRepository repository = AndroidSdkRepositories.getAndroidRepository();
    if (repository != null) {
      repositories.add(repository);
    }
    repository = AndroidSdkRepositories.getGoogleRepository();
    if (repository != null) {
      repositories.add(repository);
    }
    return repositories;
  }

  @Override
  public void addLibraryDependency(@NotNull String library, @NotNull List<String> scopesNames) {
    // Update/reset the "parsed" model.
    addLibraryDependencyToParsedModel(scopesNames, library);

    // Reset dependencies.
    myDependencyCollection = null;
    PsAndroidDependencyCollection dependencyCollection = getOrCreateDependencyCollection();

    Set<String> configurationNames = Sets.newHashSet(scopesNames);
    List<PsAndroidArtifact> targetArtifacts = Lists.newArrayList();
    forEachVariant(variant -> variant.forEachArtifact(artifact -> {
      if (artifact.containsAnyConfigurationName(configurationNames)) {
        targetArtifacts.add(artifact);
      }
    }));
    assert !targetArtifacts.isEmpty();

    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create(library);
    assert spec != null;

    PsParsedDependencies parsedDependencies = getParsedDependencies();
    for (PsAndroidArtifact artifact : targetArtifacts) {
      List<ArtifactDependencyModel> matchingParsedDependencies = parsedDependencies.findLibraryDependencies(spec, artifact::contains);
      for (ArtifactDependencyModel parsedDependency : matchingParsedDependencies) {
        dependencyCollection.addLibraryDependency(spec, artifact, parsedDependency);
      }
    }

    fireLibraryDependencyAddedEvent(spec);
    setModified(true);
  }

  @NotNull
  public PsBuildType addNewBuildType(@NotNull String name) {
    return getOrCreateBuildTypeCollection().addNew(name);
  }

  public void removeBuildType(@NotNull PsBuildType buildType) {
    getOrCreateBuildTypeCollection().remove(buildType.getName());
  }

  public void addNewFlavorDimension(@NotNull String newName) {
    assert getParsedModel() != null;
    AndroidModel androidModel = getParsedModel().android();
    assert androidModel != null;
    androidModel.flavorDimensions().addListValue().setValue(newName);
    setModified(true);
  }

  public void removeFlavorDimension(@NotNull String flavorDimension) {
    assert getParsedModel() != null;
    AndroidModel androidModel = getParsedModel().android();
    assert androidModel != null;

    GradlePropertyModel model = androidModel.flavorDimensions().getListValue(flavorDimension);
    if (model != null) {
      model.delete();
      setModified(true);
    }
  }

  @NotNull
  public PsProductFlavor addNewProductFlavor(@NotNull String name) {
    return getOrCreateProductFlavorCollection().addNew(name);
  }

  public void removeProductFlavor(@NotNull PsProductFlavor productFlavor) {
    getOrCreateProductFlavorCollection().remove(productFlavor.getName());
  }

  @NotNull
  public PsSigningConfig addNewSigningConfig(@NotNull String name) {
    return getOrCreateSigningConfigCollection().addNew(name);
  }

  public void removeSigningConfig(@NotNull PsSigningConfig signingConfig) {
    getOrCreateSigningConfigCollection().remove(signingConfig.getName());
  }

  private final PsAndroidModuleDefaultConfig myDefaultConfig = new PsAndroidModuleDefaultConfig(this);

  @NotNull
  public PsAndroidModuleDefaultConfig getDefaultConfig() {
    return myDefaultConfig;
  }
}

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
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.repositories.search.AndroidSdkRepositories;
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
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
    return getOrCreateBuildTypeCollection().findElement(buildType);
  }

  public List<PsBuildType> getBuildTypes() {
    return getOrCreateBuildTypeCollection().items();
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

  public List<PsProductFlavor> getProductFlavors() {
    return getOrCreateProductFlavorCollection().items();
  }

  @Nullable
  public PsProductFlavor findProductFlavor(@NotNull String name) {
    return getOrCreateProductFlavorCollection().findElement(name);
  }

  @NotNull
  private PsProductFlavorCollection getOrCreateProductFlavorCollection() {
    return myProductFlavorCollection == null ? myProductFlavorCollection = new PsProductFlavorCollection(this) : myProductFlavorCollection;
  }

  public List<PsVariant> getVariants() {
    return getOrCreateVariantCollection().items();
  }

  @Nullable
  public PsVariant findVariant(@NotNull String name) {
    return getOrCreateVariantCollection().findElement(name);
  }

  @NotNull
  private PsVariantCollection getOrCreateVariantCollection() {
    return myVariantCollection == null ? myVariantCollection = new PsVariantCollection(this) : myVariantCollection;
  }

  @NotNull
  private PsAndroidDependencyCollection getOrCreateDependencyCollection() {
    return myDependencyCollection == null ? myDependencyCollection = new PsAndroidModuleDependencyCollection(this) : myDependencyCollection;
  }

  public @NotNull PsAndroidDependencyCollection getDependencies() {
    return getOrCreateDependencyCollection();
  }

  @Nullable
  public PsSigningConfig findSigningConfig(@NotNull String signingConfig) {
    return getOrCreateSigningConfigCollection().findElement(signingConfig);
  }

  public List<PsSigningConfig> getSigningConfigs() {
    return getOrCreateSigningConfigCollection().items();
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

  @NotNull
  @Override
  public List<String> getConfigurations() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addLibraryDependency(@NotNull String library, @NotNull List<String> scopesNames) {
    // Update/reset the "parsed" model.
    addLibraryDependencyToParsedModel(scopesNames, library);

    resetDependencies();

    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create(library);
    assert spec != null;
    fireLibraryDependencyAddedEvent(spec);
    setModified(true);
  }

  @Override
  public void addModuleDependency(@NotNull String modulePath, @NotNull List<String> scopesNames) {
    // Update/reset the "parsed" model.
    addModuleDependencyToParsedModel(scopesNames, modulePath);

    resetDependencies();

    fireModuleDependencyAddedEvent(modulePath);
    setModified(true);
  }

  @Override
  public void setLibraryDependencyVersion(@NotNull PsArtifactDependencySpec spec,
                                          @NotNull String configurationName,
                                          @NotNull String newVersion) {
    boolean modified = false;
    List<PsLibraryAndroidDependency> matchingDependencies =
      getDependencies()
        .findLibraryDependencies(spec.getGroup(), spec.getName())
        .stream()
        .filter(it -> it.getSpec().equals(spec) && it.getConfigurationNames().contains(configurationName))
        .collect(Collectors.toList());
    // Usually there should be only one item in the matchingDependencies list. However, if there are duplicate entries in the config file
    // it might differ. We update all of them.

    for (PsLibraryAndroidDependency dependency : matchingDependencies) {
      assert dependency.getParsedModels().size() == 1;
      for (DependencyModel parsedDependency : dependency.getParsedModels()) {
        assert parsedDependency instanceof ArtifactDependencyModel;
        ArtifactDependencyModel artifactDependencyModel = (ArtifactDependencyModel)parsedDependency;
        artifactDependencyModel.setVersion(newVersion);
        modified = true;
      }
    }
    if (modified) {
      resetDependencies();
      for (PsLibraryAndroidDependency dependency : matchingDependencies) {
        fireDependencyModifiedEvent(dependency);
      }
      setModified(true);
    }
  }

  private void resetDependencies() {
    myDependencyCollection = null;
    getVariants().forEach(variant -> variant.forEachArtifact(artifact -> {
      artifact.resetDependencies();
    }));
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

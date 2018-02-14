/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.stubs.android;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.model.*;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

import static org.mockito.Mockito.mock;

public class AndroidProjectStub implements IdeAndroidProject {
  private static final Collection<String> NO_UNRESOLVED_DEPENDENCIES = ImmutableList.of();

  @NotNull private final Map<String, BuildTypeContainer> myBuildTypes = Maps.newHashMap();
  @NotNull private final Map<String, ProductFlavorContainer> myProductFlavors = Maps.newHashMap();
  @NotNull private final Map<String, IdeVariant> myVariants = Maps.newHashMap();
  @NotNull private final List<SigningConfig> mySigningConfigs = new ArrayList<>();
  @NotNull private final List<String> myFlavorDimensions = new ArrayList<>();

  @NotNull private final String myName;
  @NotNull private final FileStructure myFileStructure;
  @NotNull private final ProductFlavorContainerStub myDefaultConfig;
  @NotNull private File myBuildFolder;
  @NotNull private final File myBuildFile;

  @NotNull private final JavaCompileOptionsStub myJavaCompileOptions = new JavaCompileOptionsStub();

  @NotNull private String myModelVersion = SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION + "-SNAPSHOT";
  @Nullable private VariantStub myFirstVariant;
  private int myProjectType = PROJECT_TYPE_APP;
  private int myPluginGeneration;

  public AndroidProjectStub(@NotNull String name) {
    this(name, new FileStructure(name));
  }

  public AndroidProjectStub(@NotNull File parentDir, @NotNull String name) {
    this(name, new FileStructure(parentDir, name));
  }

  private AndroidProjectStub(@NotNull String name, @NotNull FileStructure fileStructure) {
    this.myName = name;
    myFileStructure = fileStructure;
    myBuildFolder = myFileStructure.createProjectDir("build");
    myDefaultConfig = new ProductFlavorContainerStub("main", myFileStructure, null);
    myBuildFile = myFileStructure.createProjectFile(SdkConstants.FN_BUILD_GRADLE);
  }

  @Override
  @NotNull
  public String getModelVersion() {
    return myModelVersion;
  }

  public void setModelVersion(@NotNull String modelVersion) {
    myModelVersion = modelVersion;
  }

  @Nullable
  @Override
  public GradleVersion getParsedModelVersion() {
    return null;
  }

  @Override
  public void forEachVariant(@NotNull Consumer<IdeVariant> action) {
    for (IdeVariant next: myVariants.values()) {
      action.accept(next);
    }
  }

  @Override
  public int getApiVersion() {
    return 3;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  public boolean isLibrary() {
    return myProjectType == PROJECT_TYPE_LIBRARY;
  }

  public void setProjectType(int projectType) {
    myProjectType = projectType;
  }

  @Override
  public int getProjectType() {
    return myProjectType;
  }

  @Override
  @NotNull
  public ProductFlavorContainerStub getDefaultConfig() {
    return myDefaultConfig;
  }

  public BuildTypeContainerStub addBuildType(@NotNull String buildTypeName) {
    BuildTypeContainerStub buildType = new BuildTypeContainerStub(buildTypeName, myFileStructure);
    myBuildTypes.put(buildTypeName, buildType);
    return buildType;
  }

  @Override
  @NotNull
  public Collection<BuildTypeContainer> getBuildTypes() {
    return myBuildTypes.values();
  }

  @Nullable
  public BuildTypeContainer findBuildType(@NotNull String name) {
    return myBuildTypes.get(name);
  }

  @NotNull
  public ProductFlavorContainerStub addProductFlavor(@NotNull String flavorName, String flavorDimension) {
    if (!myFlavorDimensions.contains(flavorDimension)) {
      myFlavorDimensions.add(flavorDimension);
    }
    ProductFlavorContainerStub flavor = new ProductFlavorContainerStub(flavorName, myFileStructure, flavorDimension);
    myProductFlavors.put(flavorName, flavor);
    return flavor;
  }

  @Override
  @NotNull
  public Collection<ProductFlavorContainer> getProductFlavors() {
    return myProductFlavors.values();
  }

  @Nullable
  public ProductFlavorContainerStub findProductFlavor(@NotNull String name) {
    ProductFlavorContainer flavorContainer = myProductFlavors.get(name);
    return (ProductFlavorContainerStub)flavorContainer;
  }

  @NotNull
  public VariantStub addVariant(@NotNull String variantName) {
    return addVariant(variantName, variantName);
  }

  @NotNull
  public VariantStub addVariant(@NotNull String variantName, @NotNull String buildTypeName) {
    VariantStub variant = new VariantStub(variantName, buildTypeName, myFileStructure);
    addVariant(variant);
    return variant;
  }

  private void addVariant(@NotNull VariantStub variant) {
    if (myFirstVariant == null) {
      myFirstVariant = variant;
    }
    myVariants.put(variant.getName(), variant);
  }

  @Override
  @NotNull
  public Collection<Variant> getVariants() {
    List<Variant> result = new ArrayList<>();
    result.addAll(myVariants.values());
    return result;
  }

  @Override
  @NotNull
  public Collection<String> getVariantNames() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Collection<String> getFlavorDimensions() {
    return myFlavorDimensions;
  }

  @Override
  @NotNull
  public Collection<ArtifactMetaData> getExtraArtifacts() {
    return Collections.emptyList();
  }

  @Nullable
  public VariantStub getFirstVariant() {
    return myFirstVariant;
  }

  @Override
  @NotNull
  public String getCompileTarget() {
    return "test";
  }

  @Override
  @NotNull
  public List<String> getBootClasspath() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Collection<File> getFrameworkSources() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Collection<NativeToolchain> getNativeToolchains() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Collection<SigningConfig> getSigningConfigs() {
    return mySigningConfigs;
  }

  @Override
  @NotNull
  public AaptOptions getAaptOptions() {
    return new AaptOptions() {
      @Nullable
      @Override
      public String getIgnoreAssets() {
        return null;
      }

      @Nullable
      @Override
      public Collection<String> getNoCompress() {
        return null;
      }

      @Override
      public boolean getFailOnMissingConfigEntry() {
        return false;
      }

      @NotNull
      @Override
      public List<String> getAdditionalParameters() {
        return Collections.emptyList();
      }

      @NotNull
      @Override
      public Namespacing getNamespacing() {
        return Namespacing.DISABLED;
      }

      @Nullable
      @Override
      public String getPrivateRDotJavaPackage() {
        return null;
      }
    };
  }

  @Override
  @NotNull
  public LintOptions getLintOptions() {
    return mock(LintOptions.class);
  }

  @Override
  @NotNull
  public Collection<String> getUnresolvedDependencies() {
    return NO_UNRESOLVED_DEPENDENCIES;
  }

  @Override
  @NotNull
  public Collection<SyncIssue> getSyncIssues() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public JavaCompileOptionsStub getJavaCompileOptions() {
    return myJavaCompileOptions;
  }

  @Override
  @NotNull
  public File getBuildFolder() {
    return myBuildFolder;
  }

  @Override
  @Nullable
  public String getResourcePrefix() {
    return null;
  }

  @Override
  @NotNull
  public String getBuildToolsVersion() {
    return "25";
  }

  @Override
  public int getPluginGeneration() {
    return myPluginGeneration;
  }

  @Override
  public boolean isBaseSplit() {
    return false;
  }

  @NonNull
  @Override
  public Collection<String> getDynamicFeatures() {
    return ImmutableList.of();
  }

  public AndroidProjectStub setPluginGeneration(int pluginGeneration) {
    myPluginGeneration = pluginGeneration;
    return this;
  }

  /**
   * Deletes this project's directory structure.
   */
  public void dispose() {
    myFileStructure.dispose();
  }

  /**
   * @return this project's root directory.
   */
  @NotNull
  public File getRootDir() {
    return myFileStructure.getRootFolderPath();
  }

  /**
   * @return this project's build.gradle file.
   */
  @NotNull
  public File getBuildFile() {
    return myBuildFile;
  }
}

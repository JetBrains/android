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

import static com.intellij.openapi.util.text.StringUtil.capitalize;
import static com.intellij.util.containers.ContainerUtil.map;
import static org.mockito.Mockito.mock;

import com.android.AndroidProjectTypes;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidGradlePluginProjectFlags;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.DependenciesInfo;
import com.android.builder.model.LintOptions;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.builder.model.VariantBuildInformation;
import com.android.builder.model.ViewBindingOptions;
import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl;
import com.android.tools.idea.gradle.project.sync.ModelCache;
import com.android.tools.idea.gradle.model.stubs.AndroidGradlePluginProjectFlagsStub;
import com.android.tools.idea.gradle.model.stubs.VariantBuildInformationStub;
import com.android.tools.idea.gradle.model.stubs.ViewBindingOptionsStub;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidProjectStub implements AndroidProject {
  private static final Collection<String> NO_UNRESOLVED_DEPENDENCIES = ImmutableList.of();

  @NotNull private final Map<String, BuildTypeContainer> myBuildTypes = Maps.newHashMap();
  @NotNull private final Map<String, ProductFlavorContainer> myProductFlavors = Maps.newHashMap();
  @NotNull private final Map<String, Variant> myVariants = Maps.newHashMap();
  @NotNull private final List<VariantBuildInformation> myVariantsBuiltInformation = new ArrayList<>();
  @NotNull private final List<SigningConfig> mySigningConfigs = new ArrayList<>();
  @NotNull private final List<String> myFlavorDimensions = new ArrayList<>();
  @NotNull private final List<String> myVariantNames = new ArrayList<>();
  @NotNull private final List<SyncIssue> mySyncIssues = new ArrayList<>();

  @NotNull private final String myName;
  @NotNull private final FileStructure myFileStructure;
  @NotNull private final ProductFlavorContainerStub myDefaultConfig;
  @NotNull private final File myBuildFolder;
  @NotNull private final JavaCompileOptionsStub myJavaCompileOptions = new JavaCompileOptionsStub();
  @NotNull private final ViewBindingOptionsStub myViewBindingOptions = new ViewBindingOptionsStub();

  @NotNull private String myModelVersion = SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION + "-SNAPSHOT";
  @Nullable private VariantStub myFirstVariant;
  private int myProjectType = AndroidProjectTypes.PROJECT_TYPE_APP;

  public AndroidProjectStub(@NotNull String name) {
    this(name, new FileStructure(name));
  }

  public AndroidProjectStub(@NotNull File parentDir, @NotNull String name) {
    this(name, new FileStructure(parentDir, name));
  }

  public AndroidProjectStub(@NotNull String name, @NotNull FileStructure fileStructure) {
    this.myName = name;
    myFileStructure = fileStructure;
    myBuildFolder = myFileStructure.createProjectDir("build");
    myDefaultConfig = new ProductFlavorContainerStub("main", myFileStructure, null);
  }

  @Override
  @NotNull
  public String getModelVersion() {
    return myModelVersion;
  }

  public void setModelVersion(@NotNull String modelVersion) {
    myModelVersion = modelVersion;
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

  @Nullable
  @Override
  public String getGroupId() {
    return null;
  }

  @NotNull
  @Override
  public String getNamespace() {
    return "com.example.myapp";
  }

  @Nullable
  @Override
  public String getAndroidTestNamespace() {
    return "com.example.myapp.test";
  }

  @Override
  public boolean isLibrary() {
    return myProjectType == AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
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

  @NotNull
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

  public void addVariant(@NotNull VariantStub variant) {
    if (myFirstVariant == null) {
      myFirstVariant = variant;
    }
    myVariants.put(variant.getName(), variant);

    myVariantsBuiltInformation.add(new VariantBuildInformationStub(
      variant.getName(),
      "assemble" + capitalize(variant.getName()),
      new File(myFileStructure.getRootFolderPath(), "build/output/apk/" + variant.getName() + "/output.json").getAbsolutePath(),
      null,
      new File(myFileStructure.getRootFolderPath(),
               "build/intermediates/bundle_ide_model/" + variant.getName() + "/output.json").getAbsolutePath(),
      null,
      new File(myFileStructure.getRootFolderPath(),
               "build/intermediates/apk_from_bundle_ide_model/" + variant.getName() + "/output.json").getAbsolutePath()
    ));
  }

  @Override
  @NotNull
  public Collection<Variant> getVariants() {
    return new ArrayList<>(myVariants.values());
  }

  @NonNull
  @Override
  public Collection<VariantBuildInformation> getVariantsBuildInformation() {
    return new ArrayList<>(myVariantsBuiltInformation);
  }

  public void clearVariants() {
    myVariants.clear();
  }

  @Override
  @NotNull
  public Collection<String> getVariantNames() {
    return myVariantNames;
  }

  @Nullable
  @Override
  public String getDefaultVariant() {
    return null;
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
      @NotNull
      @Override
      public Namespacing getNamespacing() {
        return Namespacing.DISABLED;
      }
    };
  }

  @Override
  @NotNull
  public LintOptions getLintOptions() {
    return mock(LintOptions.class);
  }

  @NonNull
  @Override
  public List<File> getLintRuleJars() {
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public Collection<String> getUnresolvedDependencies() {
    return NO_UNRESOLVED_DEPENDENCIES;
  }

  @Override
  @NotNull
  public Collection<SyncIssue> getSyncIssues() {
    return mySyncIssues;
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
  @NotNull
  public String getNdkVersion() {
    return "21.0.0";
  }

  @Override
  public int getPluginGeneration() {
    return 0;
  }

  @Override
  public boolean isBaseSplit() {
    return false;
  }

  @NonNull
  @Override
  public AndroidGradlePluginProjectFlags getFlags() {
    return new AndroidGradlePluginProjectFlagsStub(ImmutableMap.of());
  }

  @NonNull
  @Override
  public Collection<String> getDynamicFeatures() {
    return ImmutableList.of();
  }

  @NonNull
  @Override
  public ViewBindingOptions getViewBindingOptions() {
    return myViewBindingOptions;
  }

  @Nullable
  @Override
  public DependenciesInfo getDependenciesInfo() {
    return null;
  }

  public void setVariantNames(@NotNull String... variantNames) {
    myVariantNames.clear();
    myVariantNames.addAll(Arrays.asList(variantNames));
  }

  @NotNull
  public static IdeAndroidProject toIdeAndroidProject(AndroidProjectStub androidProject) {
    return ModelCache.create().androidProjectFrom(androidProject);
  }

  @NotNull
  public static List<IdeVariant> toIdeVariants(AndroidProjectStub androidProject) {
    ModelCache modelCache = ModelCache.create();
    GradleVersion modelVersion = GradleVersion.tryParseAndroidGradlePluginVersion(androidProject.getModelVersion());
    IdeAndroidProjectImpl ideAndroidProject = modelCache.androidProjectFrom(androidProject);
    return map(androidProject.getVariants(), it -> modelCache.variantFrom(ideAndroidProject, it, modelVersion));
  }
}

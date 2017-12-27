/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model.ide.android.stubs;

import com.android.builder.model.*;
import com.android.tools.idea.gradle.project.model.ide.android.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class AndroidProjectStub extends BaseStub implements AndroidProject {
  @NotNull private final String myModelVersion;
  @NotNull private final String myName;
  @NotNull private final ProductFlavorContainer myDefaultConfig;
  @NotNull private final Collection<BuildTypeContainer> myBuildTypes;
  @NotNull private final Collection<ProductFlavorContainer> myProductFlavors;
  @NotNull private final String myBuildToolsVersion;
  @NotNull private final Collection<SyncIssue> mySyncIssues;
  @NotNull private final Collection<Variant> myVariants;
  @NotNull private final Collection<String> myFlavorDimensions;
  @NotNull private final String myCompileTarget;
  @NotNull private final Collection<String> myBootClasspath;
  @NotNull private final Collection<NativeToolchain> myNativeToolchains;
  @NotNull private final Collection<SigningConfig> mySigningConfigs;
  @NotNull private final LintOptions myLintOptions;
  @NotNull private final Collection<String> myUnresolvedDependencies;
  @NotNull private final JavaCompileOptions myJavaCompileOptions;
  @NotNull private final File myBuildFolder;
  @Nullable private final String myResourcePrefix;
  private final int myApiVersion;
  private final boolean myLibrary;
  private final int myProjectType;
  private final int myPluginGeneration;
  private final boolean myBaseSplit;

  public AndroidProjectStub(@NotNull String modelVersion) {
    this(modelVersion, "name", new ProductFlavorContainerStub(), Lists.newArrayList(new BuildTypeContainerStub()),
         Lists.newArrayList(new ProductFlavorContainerStub()), "buildToolsVersion", Lists.newArrayList(new SyncIssueStub()),
         Lists.newArrayList(new VariantStub()), Lists.newArrayList("flavorDimension"), "compileTarget", Lists.newArrayList("bootClasspath"),
         Lists.newArrayList(new NativeToolchainStub()), Lists.newArrayList(new SigningConfigStub()), new LintOptionsStub(),
         Sets.newHashSet("unresolvedDependency"), new JavaCompileOptionsStub(), new File("buildFolder"), "resourcePrefix", 1, true, 2, 3,
         true);
  }

  public AndroidProjectStub(@NotNull String modelVersion,
                            @NotNull String name,
                            @NotNull ProductFlavorContainer defaultConfig,
                            @NotNull Collection<BuildTypeContainer> buildTypes,
                            @NotNull Collection<ProductFlavorContainer> productFlavors,
                            @NotNull String buildToolsVersion,
                            @NotNull Collection<SyncIssue> syncIssues,
                            @NotNull Collection<Variant> variants,
                            @NotNull Collection<String> flavorDimensions,
                            @NotNull String compileTarget,
                            @NotNull Collection<String> bootClasspath,
                            @NotNull Collection<NativeToolchain> nativeToolchains,
                            @NotNull Collection<SigningConfig> signingConfigs,
                            @NotNull LintOptions lintOptions,
                            @NotNull Collection<String> unresolvedDependencies,
                            @NotNull JavaCompileOptions javaCompileOptions,
                            @NotNull File buildFolder,
                            @Nullable String resourcePrefix,
                            int apiVersion,
                            boolean library,
                            int projectType,
                            int pluginGeneration,
                            boolean baseSplit) {
    myModelVersion = modelVersion;
    myName = name;
    myDefaultConfig = defaultConfig;
    myBuildTypes = buildTypes;
    myProductFlavors = productFlavors;
    myBuildToolsVersion = buildToolsVersion;
    mySyncIssues = syncIssues;
    myVariants = variants;
    myFlavorDimensions = flavorDimensions;
    myCompileTarget = compileTarget;
    myBootClasspath = bootClasspath;
    myNativeToolchains = nativeToolchains;
    mySigningConfigs = signingConfigs;
    myLintOptions = lintOptions;
    myUnresolvedDependencies = unresolvedDependencies;
    myJavaCompileOptions = javaCompileOptions;
    myBuildFolder = buildFolder;
    myResourcePrefix = resourcePrefix;
    myApiVersion = apiVersion;
    myLibrary = library;
    myProjectType = projectType;
    myPluginGeneration = pluginGeneration;
    myBaseSplit = baseSplit;
  }

  @Override
  @NotNull
  public String getModelVersion() {
    return myModelVersion;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public ProductFlavorContainer getDefaultConfig() {
    return myDefaultConfig;
  }

  @Override
  @NotNull
  public Collection<BuildTypeContainer> getBuildTypes() {
    return myBuildTypes;
  }

  @Override
  @NotNull
  public Collection<ProductFlavorContainer> getProductFlavors() {
    return myProductFlavors;
  }

  @Override
  @NotNull
  public String getBuildToolsVersion() {
    return myBuildToolsVersion;
  }

  @Override
  @NotNull
  public Collection<SyncIssue> getSyncIssues() {
    return mySyncIssues;
  }

  @Override
  @NotNull
  public Collection<Variant> getVariants() {
    return myVariants;
  }

  @Override
  @NotNull
  public Collection<String> getFlavorDimensions() {
    return myFlavorDimensions;
  }

  @Override
  @NotNull
  public Collection<ArtifactMetaData> getExtraArtifacts() {
    throw new UnusedModelMethodException("getExtraArtifacts");
  }

  @Override
  @NotNull
  public String getCompileTarget() {
    return myCompileTarget;
  }

  @Override
  @NotNull
  public Collection<String> getBootClasspath() {
    return myBootClasspath;
  }

  @Override
  @NotNull
  public Collection<File> getFrameworkSources() {
    throw new UnusedModelMethodException("getFrameworkSources");
  }

  @Override
  @NotNull
  public Collection<NativeToolchain> getNativeToolchains() {
    return myNativeToolchains;
  }

  @Override
  @NotNull
  public AaptOptions getAaptOptions() {
    throw new UnusedModelMethodException("getAaptOptions");
  }

  @Override
  @NotNull
  public Collection<SigningConfig> getSigningConfigs() {
    return mySigningConfigs;
  }

  @Override
  @NotNull
  public LintOptions getLintOptions() {
    return myLintOptions;
  }

  @Override
  @NotNull
  public Collection<String> getUnresolvedDependencies() {
    return myUnresolvedDependencies;
  }

  @Override
  @NotNull
  public JavaCompileOptions getJavaCompileOptions() {
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
    return myResourcePrefix;
  }

  @Override
  public int getApiVersion() {
    return myApiVersion;
  }

  @Deprecated
  @Override
  public boolean isLibrary() {
    return myLibrary;
  }

  @Override
  public int getProjectType() {
    return myProjectType;
  }

  @Override
  public int getPluginGeneration() {
    return myPluginGeneration;
  }

  @Override
  public boolean isBaseSplit() {
    return myBaseSplit;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AndroidProject)) {
      return false;
    }
    AndroidProject stub = (AndroidProject)o;
    return getApiVersion() == stub.getApiVersion() &&
           isLibrary() == stub.isLibrary() &&
           getProjectType() == stub.getProjectType() &&
           getPluginGeneration() == stub.getPluginGeneration() &&
           isBaseSplit() == stub.isBaseSplit() &&
           Objects.equals(getModelVersion(), stub.getModelVersion()) &&
           Objects.equals(getName(), stub.getName()) &&
           Objects.equals(getDefaultConfig(), stub.getDefaultConfig()) &&
           Objects.equals(getBuildTypes(), stub.getBuildTypes()) &&
           Objects.equals(getProductFlavors(), stub.getProductFlavors()) &&
           Objects.equals(getBuildToolsVersion(), stub.getBuildToolsVersion()) &&
           Objects.equals(getSyncIssues(), stub.getSyncIssues()) &&
           Objects.equals(getVariants(), stub.getVariants()) &&
           Objects.equals(getFlavorDimensions(), stub.getFlavorDimensions()) &&
           Objects.equals(getCompileTarget(), stub.getCompileTarget()) &&
           Objects.equals(getBootClasspath(), stub.getBootClasspath()) &&
           Objects.equals(getNativeToolchains(), stub.getNativeToolchains()) &&
           Objects.equals(getSigningConfigs(), stub.getSigningConfigs()) &&
           Objects.equals(getLintOptions(), stub.getLintOptions()) &&
           Objects.equals(getUnresolvedDependencies(), stub.getUnresolvedDependencies()) &&
           Objects.equals(getJavaCompileOptions(), stub.getJavaCompileOptions()) &&
           Objects.equals(getBuildFolder(), stub.getBuildFolder()) &&
           Objects.equals(getResourcePrefix(), stub.getResourcePrefix());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getModelVersion(), getName(), getDefaultConfig(), getBuildTypes(), getProductFlavors(), getBuildToolsVersion(),
                        getSyncIssues(), getVariants(), getFlavorDimensions(), getCompileTarget(), getBootClasspath(),
                        getNativeToolchains(), getSigningConfigs(), getLintOptions(), getUnresolvedDependencies(), getJavaCompileOptions(),
                        getBuildFolder(), getResourcePrefix(), getApiVersion(), isLibrary(), getProjectType(), getPluginGeneration(),
                        isBaseSplit());
  }

  @Override
  public String toString() {
    return "AndroidProjectStub{" +
           "myModelVersion='" + myModelVersion + '\'' +
           ", myName='" + myName + '\'' +
           ", myDefaultConfig=" + myDefaultConfig +
           ", myBuildTypes=" + myBuildTypes +
           ", myProductFlavors=" + myProductFlavors +
           ", myBuildToolsVersion='" + myBuildToolsVersion + '\'' +
           ", mySyncIssues=" + mySyncIssues +
           ", myVariants=" + myVariants +
           ", myFlavorDimensions=" + myFlavorDimensions +
           ", myCompileTarget='" + myCompileTarget + '\'' +
           ", myBootClasspath=" + myBootClasspath +
           ", myNativeToolchains=" + myNativeToolchains +
           ", mySigningConfigs=" + mySigningConfigs +
           ", myLintOptions=" + myLintOptions +
           ", myUnresolvedDependencies=" + myUnresolvedDependencies +
           ", myJavaCompileOptions=" + myJavaCompileOptions +
           ", myBuildFolder=" + myBuildFolder +
           ", myResourcePrefix='" + myResourcePrefix + '\'' +
           ", myApiVersion=" + myApiVersion +
           ", myLibrary=" + myLibrary +
           ", myProjectType=" + myProjectType +
           ", myPluginGeneration=" + myPluginGeneration +
           ", myBaseSplit=" + myBaseSplit +
           "}";
  }
}

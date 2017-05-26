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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Creates a deep copy of an {@link AndroidProject}.
 */
public final class IdeAndroidProjectImpl extends IdeModel implements IdeAndroidProject {
  private static final long serialVersionUID = 2L;

  private final int myHashCode;

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
  @NotNull private final Collection<String> myBootClassPath;
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

  public IdeAndroidProjectImpl(@NotNull AndroidProject project) {
    this(project, new ModelCache());
  }

  @VisibleForTesting
  IdeAndroidProjectImpl(@NotNull AndroidProject project, @NotNull ModelCache modelCache) {
    super(project, modelCache);
    myModelVersion = project.getModelVersion();
    GradleVersion modelVersion = GradleVersion.parse(myModelVersion);

    myName = project.getName();
    myDefaultConfig = modelCache.computeIfAbsent(project.getDefaultConfig(),
                                                 container -> new IdeProductFlavorContainer(container, modelCache));
    myBuildTypes = copy(project.getBuildTypes(), modelCache, container -> new IdeBuildTypeContainer(container, modelCache));
    myProductFlavors = copy(project.getProductFlavors(), modelCache, container -> new IdeProductFlavorContainer(container, modelCache));
    myBuildToolsVersion = project.getBuildToolsVersion();
    mySyncIssues = copy(project.getSyncIssues(), modelCache, issue -> new IdeSyncIssue(issue, modelCache));
    myVariants = copy(project.getVariants(), modelCache, variant -> new IdeVariant(variant, modelCache, modelVersion));
    myFlavorDimensions = copyNewProperty(() -> new ArrayList<>(project.getFlavorDimensions()), Collections.emptyList());
    myCompileTarget = project.getCompileTarget();
    myBootClassPath = new ArrayList<>(project.getBootClasspath());
    myNativeToolchains = copy(project.getNativeToolchains(), modelCache, toolchain -> new IdeNativeToolchain(toolchain, modelCache));
    mySigningConfigs = copy(project.getSigningConfigs(), modelCache, config -> new IdeSigningConfig(config, modelCache));
    myLintOptions = modelCache.computeIfAbsent(project.getLintOptions(), options -> new IdeLintOptions(options, modelCache, modelVersion));
    myUnresolvedDependencies = new HashSet<>(project.getUnresolvedDependencies());
    myJavaCompileOptions = modelCache.computeIfAbsent(project.getJavaCompileOptions(),
                                                      options -> new IdeJavaCompileOptions(options, modelCache));
    myBuildFolder = project.getBuildFolder();
    myResourcePrefix = project.getResourcePrefix();
    myApiVersion = project.getApiVersion();
    myLibrary = project.isLibrary();
    myProjectType = getProjectType(project, modelVersion);
    myPluginGeneration = project.getPluginGeneration();
    myBaseSplit = copyNewProperty(project::isBaseSplit, false);

    myHashCode = calculateHashCode();
  }

  private static int getProjectType(@NotNull AndroidProject project, @NotNull GradleVersion modelVersion) {
    if (modelVersion.isAtLeast(2, 3, 0)) {
      return project.getProjectType();
    }
    return project.isLibrary() ? PROJECT_TYPE_LIBRARY : PROJECT_TYPE_APP;
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
    return myBootClassPath;
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

  @Deprecated
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
    if (!(o instanceof IdeAndroidProjectImpl)) {
      return false;
    }
    IdeAndroidProjectImpl project = (IdeAndroidProjectImpl)o;
    return myApiVersion == project.myApiVersion &&
           myLibrary == project.myLibrary &&
           myProjectType == project.myProjectType &&
           myPluginGeneration == project.myPluginGeneration &&
           myBaseSplit == project.myBaseSplit &&
           Objects.equals(myModelVersion, project.myModelVersion) &&
           Objects.equals(myName, project.myName) &&
           Objects.equals(myDefaultConfig, project.myDefaultConfig) &&
           Objects.equals(myBuildTypes, project.myBuildTypes) &&
           Objects.equals(myProductFlavors, project.myProductFlavors) &&
           Objects.equals(myBuildToolsVersion, project.myBuildToolsVersion) &&
           Objects.equals(mySyncIssues, project.mySyncIssues) &&
           Objects.equals(myVariants, project.myVariants) &&
           Objects.equals(myFlavorDimensions, project.myFlavorDimensions) &&
           Objects.equals(myCompileTarget, project.myCompileTarget) &&
           Objects.equals(myBootClassPath, project.myBootClassPath) &&
           Objects.equals(myNativeToolchains, project.myNativeToolchains) &&
           Objects.equals(mySigningConfigs, project.mySigningConfigs) &&
           Objects.equals(myLintOptions, project.myLintOptions) &&
           Objects.equals(myUnresolvedDependencies, project.myUnresolvedDependencies) &&
           Objects.equals(myJavaCompileOptions, project.myJavaCompileOptions) &&
           Objects.equals(myBuildFolder, project.myBuildFolder) &&
           Objects.equals(myResourcePrefix, project.myResourcePrefix);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myModelVersion, myName, myDefaultConfig, myBuildTypes, myProductFlavors, myBuildToolsVersion, mySyncIssues,
                        myVariants, myFlavorDimensions, myCompileTarget, myBootClassPath, myNativeToolchains, mySigningConfigs,
                        myLintOptions, myUnresolvedDependencies, myJavaCompileOptions, myBuildFolder, myResourcePrefix, myApiVersion,
                        myLibrary, myProjectType, myPluginGeneration, myBaseSplit);
  }

  @Override
  public String toString() {
    return "IdeAndroidProject{" +
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
           ", myBootClassPath=" + myBootClassPath +
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

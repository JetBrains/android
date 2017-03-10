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
package com.android.tools.idea.model;

import com.android.annotations.Nullable;
import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * Creates a deep copy of {@link AndroidProject}.
 *
 * Holds copy object of the Android-Gradle project, maintained for persisting the Android model data.
 */
final public class IdeAndroidProject implements AndroidProject, Serializable {

  @NotNull private final String myModelVersion;
  @NotNull private final GradleVersion myModelGradleVersion;
  @NotNull private final String myName;
  @NotNull private final ProductFlavorContainer myDefaultConfig;
  @NotNull private final Collection<BuildTypeContainer> myBuildTypes;
  @NotNull private final Collection<ProductFlavorContainer> myProductFlavors;
  @NotNull private final String myBuildToolsVersion;
  @NotNull private final Collection<SyncIssue> mySyncIssues;
  @NotNull private final Collection<Variant> myVariants;
  @NotNull private final Collection<String> myFlavorDimensions;
  @NotNull private final Collection<ArtifactMetaData> myExtraArtifacts;
  @NotNull private final String myCompileTarget;
  @NotNull private final Collection<String> myBootClasspath;
  @NotNull private final Collection<File> myFrameworkSources;
  @NotNull private final Collection<NativeToolchain> myNativeToolchains;
  @NotNull private final AaptOptions myAaptOptions;
  @NotNull private final Collection<SigningConfig> mySigningConfigs;
  @NotNull private final LintOptions myLintOptions;
  @NotNull private final Collection<String> myUnresolvedDependencies;
  @NotNull private final JavaCompileOptions myJavaCompileOptions;
  @NotNull private final File myBuildFolder;
  @Nullable private final String myResourcePrefix;
  private final int myApiVersion;
  private final boolean myIsLibrary;
  private final int myProjectType;
  private final int myPluginGeneration;

  public IdeAndroidProject(@NotNull AndroidProject project) {
    this(project, new HashMap<>());
  }

  public IdeAndroidProject(@NotNull AndroidProject project, @NotNull Map<Library, Library> seenDependencies) {

    myModelVersion = project.getModelVersion();
    myModelGradleVersion = GradleVersion.parse(myModelVersion);

    myName = project.getName();

    myDefaultConfig = new IdeProductFlavorContainer(project.getDefaultConfig());

    myBuildTypes = new ArrayList<>();
    for (BuildTypeContainer container : project.getBuildTypes()) {
      myBuildTypes.add(new IdeBuildTypeContainer(container));
    }

    myProductFlavors = new ArrayList<>();
    for (ProductFlavorContainer container : project.getProductFlavors()) {
      myProductFlavors.add(new IdeProductFlavorContainer(container));
    }

    myBuildToolsVersion = project.getBuildToolsVersion();

    mySyncIssues = new ArrayList<>();
    for (SyncIssue issue : project.getSyncIssues()) {
      mySyncIssues.add(new IdeSyncIssue(issue));
    }

    myVariants = new ArrayList<>();
    for (Variant variant : project.getVariants()) {
      myVariants.add(new IdeVariant(variant, seenDependencies, myModelGradleVersion));
    }

    myFlavorDimensions = new ArrayList<>(project.getFlavorDimensions());

    myExtraArtifacts = new ArrayList<>();
    for (ArtifactMetaData artifact : project.getExtraArtifacts()) {
      myExtraArtifacts.add(new IdeArtifactMetaData(artifact));
    }

    myCompileTarget = project.getCompileTarget();
    myBootClasspath = new ArrayList<>(project.getBootClasspath());
    myFrameworkSources = new ArrayList<>(project.getFrameworkSources());

    myNativeToolchains = new ArrayList<>();
    for (NativeToolchain toolchain : project.getNativeToolchains()) {
      myNativeToolchains.add(new IdeNativeToolchain(toolchain));
    }

    myAaptOptions = new IdeAaptOptions(project.getAaptOptions());

    mySigningConfigs = new ArrayList<>();
    for (SigningConfig config : project.getSigningConfigs()) {
      mySigningConfigs.add(new IdeSigningConfig(config));
    }

    myLintOptions = new IdeLintOptions(project.getLintOptions(), myModelGradleVersion);
    myUnresolvedDependencies = new HashSet<>(project.getUnresolvedDependencies());
    myJavaCompileOptions = new IdeJavaCompileOptions(project.getJavaCompileOptions());
    myBuildFolder = project.getBuildFolder();
    myResourcePrefix = project.getResourcePrefix();
    myApiVersion = project.getApiVersion();
    myIsLibrary = project.isLibrary();

    if (myModelGradleVersion.isAtLeast(2,3,0)) {
      myProjectType = project.getProjectType();
    } else {
      myProjectType = myIsLibrary ? PROJECT_TYPE_LIBRARY : PROJECT_TYPE_APP;
    }

    myPluginGeneration = project.getPluginGeneration();
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
    return myExtraArtifacts;
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
    return myFrameworkSources;
  }

  @Override
  @NotNull
  public Collection<NativeToolchain> getNativeToolchains() {
    return myNativeToolchains;
  }

  @Override
  @NotNull
  public AaptOptions getAaptOptions() {
    return myAaptOptions;
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

  @Override
  public boolean isLibrary() {
    return myIsLibrary;
  }

  @Override
  public int getProjectType() {
    return myProjectType;
  }

  @Override
  public int getPluginGeneration() {
    return myPluginGeneration;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AndroidProject)) return false;
    AndroidProject project = (AndroidProject)o;

    return isLibrary() == project.isLibrary() &&
           getProjectType() == project.getProjectType() &&
           getApiVersion() == project.getApiVersion() &&
           getPluginGeneration() == project.getPluginGeneration() &&
           Objects.equals(getModelVersion(), project.getModelVersion()) &&
           Objects.equals(getName(), project.getName()) &&
           Objects.equals(getDefaultConfig(), project.getDefaultConfig()) &&

           getBuildTypes().containsAll(project.getBuildTypes()) &&
           project.getBuildTypes().containsAll(getBuildTypes()) &&

           Objects.equals(getProductFlavors(), project.getProductFlavors()) &&
           Objects.equals(getBuildToolsVersion(), project.getBuildToolsVersion()) &&
           Objects.equals(getResourcePrefix(), project.getResourcePrefix()) &&
           Objects.equals(getSyncIssues(), project.getSyncIssues()) &&
           Objects.equals(getVariants(), project.getVariants()) &&
           Objects.equals(getFlavorDimensions(), project.getFlavorDimensions()) &&
           Objects.equals(getExtraArtifacts(), project.getExtraArtifacts()) &&
           Objects.equals(getCompileTarget(), project.getCompileTarget()) &&
           Objects.equals(getBootClasspath(), project.getBootClasspath()) &&
           Objects.equals(getFrameworkSources(), project.getFrameworkSources()) &&
           Objects.equals(getNativeToolchains(), project.getNativeToolchains()) &&
           Objects.equals(getAaptOptions(), project.getAaptOptions()) &&
           Objects.equals(getSigningConfigs(), project.getSigningConfigs()) &&
           Objects.equals(getLintOptions(), project.getLintOptions()) &&

           getUnresolvedDependencies().containsAll(project.getUnresolvedDependencies()) &&
           project.getUnresolvedDependencies().containsAll(getUnresolvedDependencies()) &&

           Objects.equals(getJavaCompileOptions(), project.getJavaCompileOptions()) &&
           Objects.equals(getBuildFolder(), project.getBuildFolder());
  }

  @Override
  public int hashCode() {
    return Objects.hash(isLibrary(), getProjectType(), getApiVersion(), getPluginGeneration(), getModelVersion(), getName(), getDefaultConfig(),
                        getBuildTypes(), getProductFlavors(), getBuildToolsVersion(), getResourcePrefix(), getSyncIssues(), getVariants(),
                        getFlavorDimensions(), getExtraArtifacts(), getCompileTarget(), getBootClasspath(), getFrameworkSources(),
                        getNativeToolchains(), getAaptOptions(), getSigningConfigs(), getLintOptions(), getUnresolvedDependencies(),
                        getJavaCompileOptions(), getBuildFolder());
  }
}

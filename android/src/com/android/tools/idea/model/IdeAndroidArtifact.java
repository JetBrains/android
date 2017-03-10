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
import com.android.builder.model.level2.DependencyGraphs;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * Creates a deep copy of {@link AndroidArtifact}.
 *
 * @see IdeAndroidProject
 */
final public class IdeAndroidArtifact implements AndroidArtifact, Serializable {

  @NotNull private final String myName;
  @NotNull private final String myCompileTaskName;
  @NotNull private final String myAssembleTaskName;
  @NotNull private final File myClassesFolder;
  @NotNull private final File myJavaResourcesFolder;
  @NotNull private final Dependencies myDependencies;
  @NotNull private final Dependencies myCompileDependencies;
  @NotNull private final DependencyGraphs myDependencyGraphs;
  @Nullable private final SourceProvider myVariantSourceProvider;
  @Nullable private final SourceProvider myMultiFlavorSourceProvider;
  @NotNull private final Set<String> myIdeSetupTaskNames;
  @NotNull private final Collection<File> myGeneratedSourceFolders;
  @NotNull private final GradleVersion myGradleVersion;

  @NotNull private final Collection<AndroidArtifactOutput> myOutputs;
  @NotNull private final String myApplicationId;
  @NotNull private final String mySourceGenTaskName;
  @NotNull private final Collection<File> myGeneratedResourceFolders;
  @NotNull private final Map<String,ClassField> myBuildConfigFields;
  @NotNull private final Map<String,ClassField> myResValues;
  @NotNull private final InstantRun myInstantRun;
  @Nullable private final String mySigningConfigName;
  @Nullable private final Set<String> myAbiFilters;
  @Nullable private final Collection<NativeLibrary> myNativeLibraries;
  private final boolean mySigned;

  public IdeAndroidArtifact(@NotNull AndroidArtifact artifact, @NotNull Map<Library, Library> seen, @NotNull GradleVersion gradleVersion) {
    myGradleVersion = gradleVersion;

    myName = artifact.getName();
    myCompileTaskName = artifact.getCompileTaskName();
    myAssembleTaskName = artifact.getAssembleTaskName();
    myClassesFolder = artifact.getClassesFolder();
    myJavaResourcesFolder = artifact.getJavaResourcesFolder();
    myDependencies = new IdeDependencies(artifact.getDependencies(), seen, myGradleVersion);
    myCompileDependencies = new IdeDependencies(artifact.getCompileDependencies(), seen, myGradleVersion);
    if (myGradleVersion.isAtLeast(2,3,0)) {
      myDependencyGraphs = new IdeDependencyGraphs(artifact.getDependencyGraphs());
    } else {
      myDependencyGraphs = null;
    }

    SourceProvider arVariantSourceProvider = artifact.getVariantSourceProvider();
    myVariantSourceProvider = arVariantSourceProvider == null ? null : new IdeSourceProvider(arVariantSourceProvider);

    SourceProvider arMultiFlavorSourceProvider = artifact.getMultiFlavorSourceProvider();
    myMultiFlavorSourceProvider = arMultiFlavorSourceProvider == null ? null : new IdeSourceProvider(arMultiFlavorSourceProvider);

    myIdeSetupTaskNames = new HashSet<>(artifact.getIdeSetupTaskNames());
    myGeneratedSourceFolders = new ArrayList<>(artifact.getGeneratedSourceFolders());

    myOutputs = new ArrayList<>();
    for (AndroidArtifactOutput output : artifact.getOutputs()) {
      myOutputs.add(new IdeAndroidArtifactOutput(output));
    }

    myApplicationId = artifact.getApplicationId();
    mySourceGenTaskName = artifact.getSourceGenTaskName();
    myGeneratedResourceFolders = new ArrayList<>(artifact.getGeneratedResourceFolders());

    Map<String, ClassField> arBuildConfigFields = artifact.getBuildConfigFields();
    myBuildConfigFields = new HashMap<>();
    for (String key : arBuildConfigFields.keySet()) {
      myBuildConfigFields.put(key, new IdeClassField(arBuildConfigFields.get(key)));
    }

    Map<String, ClassField> arResValues = artifact.getResValues();
    myResValues = new HashMap<>();
    for (String key : arResValues.keySet()) {
      myResValues.put(key, new IdeClassField(arResValues.get(key)));
    }

    myInstantRun = new IdeInstantRun(artifact.getInstantRun());
    mySigningConfigName = artifact.getSigningConfigName();

    Set<String> arAbiFilters = artifact.getAbiFilters();
    myAbiFilters = arAbiFilters == null ? null : new HashSet<>(arAbiFilters);

    Collection<NativeLibrary> arNativeLibraries =artifact.getNativeLibraries();
    if (arNativeLibraries != null) {
      myNativeLibraries = new ArrayList<>();
      for (NativeLibrary library : arNativeLibraries) {
        myNativeLibraries.add(new IdeNativeLibrary(library));
      }
    }
    else {
      myNativeLibraries = null;
    }

    mySigned = artifact.isSigned();
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public String getCompileTaskName() {
    return myCompileTaskName;
  }

  @Override
  @NotNull
  public String getAssembleTaskName() {
    return myAssembleTaskName;
  }

  @Override
  @NotNull
  public File getClassesFolder() {
    return myClassesFolder;
  }

  @Override
  @NotNull
  public File getJavaResourcesFolder() {
    return myJavaResourcesFolder;
  }

  @Override
  @NotNull
  public Dependencies getDependencies() {
    return myDependencies;
  }

  @Override
  @NotNull
  public Dependencies getCompileDependencies() {
    return myCompileDependencies;
  }

  @Override
  @NotNull
  public DependencyGraphs getDependencyGraphs() {
    return myDependencyGraphs;
  }

  @Nullable
  @Override
  public SourceProvider getVariantSourceProvider() {
    return myVariantSourceProvider;
  }

  @Nullable
  @Override
  public SourceProvider getMultiFlavorSourceProvider() {
    return myMultiFlavorSourceProvider;
  }

  @Override
  @NotNull
  public Set<String> getIdeSetupTaskNames() {
    return myIdeSetupTaskNames;
  }

  @Override
  @NotNull
  public Collection<File> getGeneratedSourceFolders() {
    return myGeneratedSourceFolders;
  }

  @Override
  @NotNull
  public Collection<AndroidArtifactOutput> getOutputs() {
    return myOutputs;
  }

  @Override
  @NotNull
  public String getApplicationId() {
    return myApplicationId;
  }

  @Override
  @NotNull
  public String getSourceGenTaskName() {
    return mySourceGenTaskName;
  }

  @Override
  @NotNull
  public Collection<File> getGeneratedResourceFolders() {
    return myGeneratedResourceFolders;
  }

  @Override
  @NotNull
  public Map<String, ClassField> getBuildConfigFields() {
    return myBuildConfigFields;
  }

  @Override
  @NotNull
  public Map<String, ClassField> getResValues() {
    return myResValues;
  }

  @Override
  @NotNull
  public InstantRun getInstantRun() {
    return myInstantRun;
  }

  @Override
  @Nullable
  public String getSigningConfigName() {
    return mySigningConfigName;
  }

  @Override
  @Nullable
  public Set<String> getAbiFilters() {
    return myAbiFilters;
  }

  @Override
  @Nullable
  public Collection<NativeLibrary> getNativeLibraries() {
    return myNativeLibraries;
  }

  @Override
  public boolean isSigned() {
    return mySigned;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AndroidArtifact)) return false;
    AndroidArtifact artifact = (AndroidArtifact)o;

    if (getAbiFilters() == null) {
      if (artifact.getAbiFilters() != null) return false;
    }
    else {
      if ((artifact.getAbiFilters() == null) ||
          (getAbiFilters().size() != artifact.getAbiFilters().size()) ||
          (!getAbiFilters().containsAll(artifact.getAbiFilters()))) {
        return false;
      }
    }

    if (getNativeLibraries() == null) {
      if (artifact.getNativeLibraries() != null) return false;
    }
    else {
      if ((artifact.getNativeLibraries() == null) ||
          !getNativeLibraries().containsAll(artifact.getNativeLibraries()) ||
          !artifact.getNativeLibraries().containsAll(getNativeLibraries())) {
        return false;
      }
    }

    if (myGradleVersion.isAtLeast(2,3,0)) {
      // Only check if version is available
      if (!Objects.equals(getDependencyGraphs(), artifact.getDependencyGraphs())) {
        return false;
      }
    }

    return Objects.equals(getName(), artifact.getName()) &&
           Objects.equals(getCompileTaskName(), artifact.getCompileTaskName()) &&
           Objects.equals(getAssembleTaskName(), artifact.getAssembleTaskName()) &&
           Objects.equals(getClassesFolder(), artifact.getClassesFolder()) &&
           Objects.equals(getJavaResourcesFolder(), artifact.getJavaResourcesFolder()) &&
           Objects.equals(getDependencies(), artifact.getDependencies()) &&
           Objects.equals(getCompileDependencies(), artifact.getCompileDependencies()) &&
           Objects.equals(getVariantSourceProvider(), artifact.getVariantSourceProvider()) &&
           Objects.equals(getMultiFlavorSourceProvider(), artifact.getMultiFlavorSourceProvider()) &&
           Objects.equals(getIdeSetupTaskNames(), artifact.getIdeSetupTaskNames()) &&
           Objects.equals(getGeneratedSourceFolders(), artifact.getGeneratedSourceFolders()) &&
           isSigned() == artifact.isSigned() &&
           Objects.equals(getSigningConfigName(), artifact.getSigningConfigName()) &&
           Objects.equals(getApplicationId(), artifact.getApplicationId()) &&
           Objects.equals(getSourceGenTaskName(), artifact.getSourceGenTaskName()) &&
           Objects.equals(getInstantRun(), artifact.getInstantRun()) &&
           Objects.equals(getBuildConfigFields(), artifact.getBuildConfigFields()) &&
           Objects.equals(getResValues(), artifact.getResValues()) &&

           // TODO Improve equals as this is currently quadratic on a hash set
           getOutputs().containsAll(artifact.getOutputs()) &&
           artifact.getOutputs().containsAll(getOutputs()) &&

           getGeneratedResourceFolders().containsAll(artifact.getGeneratedResourceFolders()) &&
           artifact.getGeneratedResourceFolders().containsAll(getGeneratedResourceFolders());
  }

  @Override
  public int hashCode() {
    return Objects.
      hash(getName(), getCompileTaskName(), getAssembleTaskName(), getClassesFolder(), getJavaResourcesFolder(), getDependencies(),
           getCompileDependencies(), myGradleVersion.isAtLeast(2,3,0) ? getDependencyGraphs():0, getVariantSourceProvider(), getMultiFlavorSourceProvider(),
           getIdeSetupTaskNames(), getGeneratedSourceFolders(), getAbiFilters(), getNativeLibraries(), isSigned(), getSigningConfigName(),
           getApplicationId(), getSourceGenTaskName(), getInstantRun(), getBuildConfigFields(), getResValues(), getOutputs(),
           getGeneratedResourceFolders());
  }
}

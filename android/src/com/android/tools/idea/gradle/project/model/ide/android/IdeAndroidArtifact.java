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

import com.android.annotations.Nullable;
import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

/**
 * Creates a deep copy of {@link AndroidArtifact}.
 */
public class IdeAndroidArtifact extends IdeBaseArtifact implements AndroidArtifact {
  @NotNull private final Collection<AndroidArtifactOutput> myOutputs;
  @NotNull private final String myApplicationId;
  @NotNull private final String mySourceGenTaskName;
  @NotNull private final Collection<File> myGeneratedResourceFolders = new ArrayList<>();
  @NotNull private final Map<String, ClassField> myBuildConfigFields = new HashMap<>();
  @NotNull private final Map<String, ClassField> myResValues = new HashMap<>();
  @NotNull private final InstantRun myInstantRun;
  @Nullable private final String mySigningConfigName;
  @Nullable private final Set<String> myAbiFilters;
  @Nullable private final Collection<NativeLibrary> myNativeLibraries;
  private final boolean mySigned;

  public IdeAndroidArtifact(@NotNull AndroidArtifact artifact, @NotNull ModelCache modelCache, @NotNull GradleVersion gradleVersion) {
    super(artifact, modelCache, gradleVersion);

    myOutputs = copy(artifact.getOutputs(), modelCache, IdeAndroidArtifactOutput::new);

    myApplicationId = artifact.getApplicationId();
    mySourceGenTaskName = artifact.getSourceGenTaskName();
    myGeneratedResourceFolders.addAll(artifact.getGeneratedResourceFolders());

    Map<String, ClassField> buildConfigFields = artifact.getBuildConfigFields();
    buildConfigFields.forEach((name, classField) -> myBuildConfigFields.put(name, new IdeClassField(classField)));

    Map<String, ClassField> resValues = artifact.getResValues();
    resValues.forEach((name, classField) -> myResValues.put(name, new IdeClassField(classField)));

    myInstantRun = new IdeInstantRun(artifact.getInstantRun());
    mySigningConfigName = artifact.getSigningConfigName();

    Set<String> abiFilters = artifact.getAbiFilters();
    myAbiFilters = abiFilters != null ? new HashSet<>(abiFilters) : null;

    Collection<NativeLibrary> nativeLibraries = artifact.getNativeLibraries();
    if (nativeLibraries != null) {
      myNativeLibraries = copy(nativeLibraries, modelCache, IdeNativeLibrary::new);
    }
    else {
      myNativeLibraries = null;
    }

    mySigned = artifact.isSigned();
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IdeAndroidArtifact artifact = (IdeAndroidArtifact)o;
    return mySigned == artifact.mySigned &&
           Objects.equals(myOutputs, artifact.myOutputs) &&
           Objects.equals(myApplicationId, artifact.myApplicationId) &&
           Objects.equals(mySourceGenTaskName, artifact.mySourceGenTaskName) &&
           Objects.equals(myGeneratedResourceFolders, artifact.myGeneratedResourceFolders) &&
           Objects.equals(myBuildConfigFields, artifact.myBuildConfigFields) &&
           Objects.equals(myResValues, artifact.myResValues) &&
           Objects.equals(myInstantRun, artifact.myInstantRun) &&
           Objects.equals(mySigningConfigName, artifact.mySigningConfigName) &&
           Objects.equals(myAbiFilters, artifact.myAbiFilters) &&
           Objects.equals(myNativeLibraries, artifact.myNativeLibraries);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myOutputs, myApplicationId, mySourceGenTaskName, myGeneratedResourceFolders, myBuildConfigFields, myResValues,
                        myInstantRun, mySigningConfigName, myAbiFilters, myNativeLibraries, mySigned);
  }

  @Override
  public String toString() {
    return "IdeAndroidArtifact{" +
           "myOutputs=" + myOutputs +
           ", myApplicationId='" + myApplicationId + '\'' +
           ", mySourceGenTaskName='" + mySourceGenTaskName + '\'' +
           ", myGeneratedResourceFolders=" + myGeneratedResourceFolders +
           ", myBuildConfigFields=" + myBuildConfigFields +
           ", myResValues=" + myResValues +
           ", myInstantRun=" + myInstantRun +
           ", mySigningConfigName='" + mySigningConfigName + '\'' +
           ", myAbiFilters=" + myAbiFilters +
           ", myNativeLibraries=" + myNativeLibraries +
           ", mySigned=" + mySigned +
           '}';
  }
}

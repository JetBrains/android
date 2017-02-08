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
 * Creates a deep copy of {@link AndroidArtifact}.
 *
 * @see IdeAndroidProject
 */
public class IdeAndroidArtifact extends IdeBaseArtifact implements AndroidArtifact, Serializable {

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
    super(artifact, seen, gradleVersion);

    myOutputs = new HashSet<>();
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
}

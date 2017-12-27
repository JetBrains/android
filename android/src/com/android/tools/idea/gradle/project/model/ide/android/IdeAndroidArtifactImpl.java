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

import com.android.annotations.NonNull;
import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.ide.android.level2.IdeDependenciesFactory;
import com.google.common.collect.ImmutableList;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Creates a deep copy of {@link AndroidArtifact}.
 */
public final class IdeAndroidArtifactImpl extends IdeBaseArtifactImpl implements IdeAndroidArtifact {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 3L;

  @NotNull private final Collection<AndroidArtifactOutput> myOutputs;
  @NotNull private final String myApplicationId;
  @NotNull private final String mySourceGenTaskName;
  @NotNull private final Collection<File> myGeneratedResourceFolders;
  @NotNull private final Collection<File> myAdditionalRuntimeApks;
  @NotNull private final Map<String, ClassField> myBuildConfigFields;
  @NotNull private final Map<String, ClassField> myResValues;
  @Nullable private final IdeInstantRun myInstantRun;
  @Nullable private final String mySigningConfigName;
  @Nullable private final Set<String> myAbiFilters;
  @Nullable private final Collection<NativeLibrary> myNativeLibraries;
  @Nullable private final IdeTestOptions myTestOptions;
  private final boolean mySigned;
  private final int myHashCode;

  public IdeAndroidArtifactImpl(@NotNull AndroidArtifact artifact,
                                @NotNull ModelCache modelCache,
                                @NotNull IdeDependenciesFactory dependenciesFactory,
                                @Nullable GradleVersion gradleVersion) {
    super(artifact, modelCache, dependenciesFactory, gradleVersion);
    myOutputs = copyOutputs(artifact, modelCache);
    myApplicationId = artifact.getApplicationId();
    mySourceGenTaskName = artifact.getSourceGenTaskName();
    myGeneratedResourceFolders = ImmutableList.copyOf(artifact.getGeneratedResourceFolders());
    myBuildConfigFields = copy(artifact.getBuildConfigFields(), modelCache, classField -> new IdeClassField(classField, modelCache));
    myResValues = copy(artifact.getResValues(), modelCache, classField -> new IdeClassField(classField, modelCache));
    myInstantRun = copyNewProperty(modelCache, artifact::getInstantRun, instantRun -> new IdeInstantRun(instantRun, modelCache), null);
    mySigningConfigName = artifact.getSigningConfigName();
    myAbiFilters = copy(artifact.getAbiFilters());
    myNativeLibraries = copy(modelCache, artifact.getNativeLibraries());
    mySigned = artifact.isSigned();
    myAdditionalRuntimeApks = copyNewProperty(artifact::getAdditionalRuntimeApks, Collections.emptySet());
    myTestOptions = copyNewProperty(modelCache, artifact::getTestOptions, testOptions -> new IdeTestOptions(testOptions, modelCache), null);

    myHashCode = calculateHashCode();
  }

  @NotNull
  private static Collection<AndroidArtifactOutput> copyOutputs(@NotNull AndroidArtifact artifact, @NotNull ModelCache modelCache) {
    Collection<AndroidArtifactOutput> outputs;
    try {
      outputs = artifact.getOutputs();
      return copy(outputs, modelCache, output -> new IdeAndroidArtifactOutput(output, modelCache));
    }
    catch (RuntimeException e) {
      // See http://b/64305584
      return Collections.emptyList();
    }
  }

  @Nullable
  private static Collection<NativeLibrary> copy(@NotNull ModelCache modelCache, @Nullable Collection<NativeLibrary> original) {
    return original != null ? copy(original, modelCache, library -> new IdeNativeLibrary(library, modelCache)) : null;
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
    if (myInstantRun != null) {
      return myInstantRun;
    }
    throw new UnsupportedMethodException("Unsupported method: AndroidArtifact.getInstantRun()");
  }

  @NonNull
  @Override
  public Collection<File> getAdditionalRuntimeApks() {
    return myAdditionalRuntimeApks;
  }

  @Override
  @Nullable
  public TestOptions getTestOptions() {
    return myTestOptions;
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
    if (!(o instanceof IdeAndroidArtifactImpl)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    IdeAndroidArtifactImpl artifact = (IdeAndroidArtifactImpl)o;
    return artifact.canEquals(this) &&
           mySigned == artifact.mySigned &&
           Objects.equals(myOutputs, artifact.myOutputs) &&
           Objects.equals(myApplicationId, artifact.myApplicationId) &&
           Objects.equals(mySourceGenTaskName, artifact.mySourceGenTaskName) &&
           Objects.equals(myGeneratedResourceFolders, artifact.myGeneratedResourceFolders) &&
           Objects.equals(myBuildConfigFields, artifact.myBuildConfigFields) &&
           Objects.equals(myResValues, artifact.myResValues) &&
           Objects.equals(myInstantRun, artifact.myInstantRun) &&
           Objects.equals(mySigningConfigName, artifact.mySigningConfigName) &&
           Objects.equals(myAbiFilters, artifact.myAbiFilters) &&
           Objects.equals(myAdditionalRuntimeApks, artifact.myAdditionalRuntimeApks) &&
           Objects.equals(myNativeLibraries, artifact.myNativeLibraries) &&
           Objects.equals(myTestOptions, artifact.myTestOptions);
  }

  @Override
  protected boolean canEquals(Object other) {
    return other instanceof IdeAndroidArtifactImpl;
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  @Override
  protected int calculateHashCode() {
    return Objects.hash(super.calculateHashCode(), myOutputs, myApplicationId, mySourceGenTaskName,
                        myGeneratedResourceFolders, myBuildConfigFields, myResValues, myInstantRun,
                        mySigningConfigName, myAbiFilters, myNativeLibraries, mySigned, myAdditionalRuntimeApks, myTestOptions);
  }

  @Override
  public String toString() {
    return "IdeAndroidArtifact{" +
           super.toString() +
           ", myOutputs=" + myOutputs +
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
           ", myTestOptions=" + myTestOptions +
           "}";
  }
}

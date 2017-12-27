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

import com.android.annotations.NonNull;
import com.android.builder.model.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class AndroidArtifactStub extends BaseArtifactStub implements AndroidArtifact {
  @NotNull private final Collection<AndroidArtifactOutput> myOutputs;
  @NotNull private final String myApplicationId;
  @NotNull private final String mySourceGenTaskName;
  @NotNull private final Collection<File> myGeneratedResourceFolders = new ArrayList<>();
  @NotNull private final Collection<File> myAdditionalRuntimeApks;
  @NotNull private final Map<String, ClassField> myBuildConfigFields;
  @NotNull private final Map<String, ClassField> myResValues;
  @NotNull private final InstantRun myInstantRun;
  @Nullable private final TestOptions myTestOptions;
  @Nullable private final String mySigningConfigName;
  @Nullable private final Set<String> myAbiFilters;
  @Nullable private final Collection<NativeLibrary> myNativeLibraries;
  private final boolean mySigned;

  public AndroidArtifactStub() {
    this(Lists.newArrayList(new AndroidArtifactOutputStub()), "applicationId", "sourceGenTaskName",
         ImmutableMap.of("buildConfigField", new ClassFieldStub()), ImmutableMap.of("resValue", new ClassFieldStub()), new InstantRunStub(),
         "signingConfigName", Sets.newHashSet("filter"), Lists.newArrayList(new NativeLibraryStub()),
         Collections.emptyList(), new TestOptionsStub(), true);
  }

  public AndroidArtifactStub(@NotNull Collection<AndroidArtifactOutput> outputs,
                             @NotNull String applicationId,
                             @NotNull String sourceGenTaskName,
                             @NotNull Map<String, ClassField> buildConfigFields,
                             @NotNull Map<String, ClassField> resValues,
                             @NotNull InstantRun run,
                             @Nullable String signingConfigName,
                             @Nullable Set<String> filters,
                             @Nullable Collection<NativeLibrary> libraries,
                             @NotNull Collection<File> apks,
                             @Nullable TestOptions testOptions,
                             boolean signed) {
    myOutputs = outputs;
    myApplicationId = applicationId;
    mySourceGenTaskName = sourceGenTaskName;

    myBuildConfigFields = buildConfigFields;
    myResValues = resValues;
    myInstantRun = run;
    mySigningConfigName = signingConfigName;
    myAbiFilters = filters;
    myNativeLibraries = libraries;
    myAdditionalRuntimeApks = apks;
    myTestOptions = testOptions;
    mySigned = signed;
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

  @NonNull
  @Override
  public Collection<File> getAdditionalRuntimeApks() {
    return myAdditionalRuntimeApks;
  }

  @Nullable
  @Override
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
    if (!(o instanceof AndroidArtifact)) {
      return false;
    }
    AndroidArtifact artifact = (AndroidArtifact)o;
    return Objects.equals(getName(), artifact.getName()) &&
           Objects.equals(getCompileTaskName(), artifact.getCompileTaskName()) &&
           Objects.equals(getAssembleTaskName(), artifact.getAssembleTaskName()) &&
           Objects.equals(getClassesFolder(), artifact.getClassesFolder()) &&
           Objects.equals(getJavaResourcesFolder(), artifact.getJavaResourcesFolder()) &&
           Objects.equals(getDependencies(), artifact.getDependencies()) &&
           Objects.equals(getCompileDependencies(), artifact.getCompileDependencies()) &&
           Objects.equals(getDependencyGraphs(), artifact.getDependencyGraphs()) &&
           Objects.equals(getIdeSetupTaskNames(), artifact.getIdeSetupTaskNames()) &&
           Objects.equals(getGeneratedSourceFolders(), artifact.getGeneratedSourceFolders()) &&
           Objects.equals(getVariantSourceProvider(), artifact.getVariantSourceProvider()) &&
           Objects.equals(getMultiFlavorSourceProvider(), artifact.getMultiFlavorSourceProvider()) &&
           isSigned() == artifact.isSigned() &&
           Objects.equals(getOutputs(), artifact.getOutputs()) &&
           Objects.equals(getApplicationId(), artifact.getApplicationId()) &&
           Objects.equals(getSourceGenTaskName(), artifact.getSourceGenTaskName()) &&
           Objects.equals(getGeneratedResourceFolders(), artifact.getGeneratedResourceFolders()) &&
           Objects.equals(getBuildConfigFields(), artifact.getBuildConfigFields()) &&
           Objects.equals(getResValues(), artifact.getResValues()) &&
           equals(artifact, AndroidArtifact::getInstantRun) &&
           Objects.equals(getSigningConfigName(), artifact.getSigningConfigName()) &&
           Objects.equals(getAbiFilters(), artifact.getAbiFilters()) &&
           Objects.equals(getAdditionalRuntimeApks(), artifact.getAdditionalRuntimeApks()) &&
           Objects.equals(getTestOptions(), artifact.getTestOptions()) &&
           Objects.equals(getNativeLibraries(), artifact.getNativeLibraries());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getCompileTaskName(), getAssembleTaskName(), getClassesFolder(), getJavaResourcesFolder(),
            getDependencies(), getCompileDependencies(), getDependencyGraphs(), getIdeSetupTaskNames(),
            getGeneratedSourceFolders(), getVariantSourceProvider(), getMultiFlavorSourceProvider(), getOutputs(), getApplicationId(),
            getSourceGenTaskName(), getGeneratedResourceFolders(), getBuildConfigFields(), getResValues(), getInstantRun(),
            getSigningConfigName(), getAbiFilters(), getNativeLibraries(), isSigned(), getAdditionalRuntimeApks(), getTestOptions());
  }

  @Override
  public String toString() {
    return "AndroidArtifactStub{" +
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
           "} " + super.toString();
  }
}

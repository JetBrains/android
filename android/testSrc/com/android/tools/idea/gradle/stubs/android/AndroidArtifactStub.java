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

import com.android.build.OutputFile;
import com.android.builder.model.*;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.capitalize;

public class AndroidArtifactStub extends BaseArtifactStub implements AndroidArtifact {
  @NotNull private final List<File> myGeneratedResourceFolders = Lists.newArrayList();
  @NotNull private final Collection<AndroidArtifactOutput> myOutputs;
  @NotNull private final Collection<NativeLibrary> myNativeLibraries = Lists.newArrayList();
  @NotNull String myApplicationId;

  private InstantRun myInstantRun;

  AndroidArtifactStub(@NotNull String name, String dirName, @NotNull String buildType, @NotNull FileStructure fileStructure) {
    super(name, dirName, new DependenciesStub(), buildType, fileStructure);
    myApplicationId = "app." + buildType.toLowerCase();
    myOutputs = Arrays.<AndroidArtifactOutput>asList(
      new AndroidArtifactOutputStub(name + "-" + buildType, Arrays.<OutputFile>asList(
        new OutputFileStub(new File(name + "-" + buildType + ".apk")))));
    myInstantRun = new InstantRunStub(new File(name + "-" + buildType + "-infoFile"), false, 0);
  }

  @Override
  @NotNull
  public Collection<AndroidArtifactOutput> getOutputs() {
    return myOutputs;
  }

  @Override
  public boolean isSigned() {
    return false;
  }

  @Override
  @Nullable
  public String getSigningConfigName() {
    return null;
  }

  @Override
  @NotNull
  public String getApplicationId() {
    return myApplicationId;
  }

  public AndroidArtifactStub setApplicationId(String applicationId) {
    myApplicationId = applicationId;
    return this;
  }

  @Override
  @NotNull
  public String getSourceGenTaskName() {
    return "generate" + capitalize(myBuildType) + "Sources";
  }

  @Override
  @NotNull
  public List<File> getGeneratedResourceFolders() {
    return myGeneratedResourceFolders;
  }

  @Override
  @Nullable
  public Set<String> getAbiFilters() {
    return null;
  }

  @Override
  @Nullable
  public Collection<NativeLibrary> getNativeLibraries() {
    return myNativeLibraries;
  }

  @Override
  @NotNull
  public Map<String, ClassField> getBuildConfigFields() {
    return Collections.emptyMap();
  }

  @Override
  @NotNull
  public Map<String, ClassField> getResValues() {
    return Collections.emptyMap();
  }

  @Override
  public InstantRun getInstantRun() {
    return myInstantRun;
  }

  public AndroidArtifactStub setInstantRun(InstantRun instantRun) {
    myInstantRun = instantRun;
    return this;
  }

  /**
   * Adds the given path to the list of generated resource directories. It also creates the directory in the file system.
   *
   * @param path path of the generated resource directory to add, relative to the root directory of the Android project.
   */
  public void addGeneratedResourceFolder(@NotNull String path) {
    File directory = myFileStructure.createProjectDir(path);
    myGeneratedResourceFolders.add(directory);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    // Should be the same if it is an stub
    if ((o instanceof AndroidArtifactStub)) return false;
    //Use other object equals
    if (!(o instanceof AndroidArtifact)) return false;
    AndroidArtifact artifact = (AndroidArtifact)o;
    return artifact.equals(this);
  }
}

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
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.ClassField;
import com.android.builder.model.NativeLibrary;
import com.android.builder.model.InstantRun;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.capitalize;

public class AndroidArtifactStub extends BaseArtifactStub implements AndroidArtifact {
  @NotNull private final List<File> myGeneratedResourceFolders = Lists.newArrayList();
  @NotNull private final String myApplicationId;
  @NotNull private final Collection<AndroidArtifactOutput> myOutputs;

  AndroidArtifactStub(@NotNull String name, String dirName, @NotNull String buildType, @NotNull FileStructure fileStructure) {
    super(name, dirName, new DependenciesStub(), buildType, fileStructure);
    myApplicationId = "app." + buildType.toLowerCase();
    myOutputs = Arrays.<AndroidArtifactOutput>asList(
      new AndroidArtifactOutputStub(Arrays.<OutputFile>asList(
        new OutputFileStub(new File(name + "-" + buildType + ".apk")))));
  }

  @Override
  @NotNull
  public Collection<AndroidArtifactOutput> getOutputs() {
    return myOutputs;
  }

  @Override
  public boolean isSigned() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Nullable
  public String getSigningConfigName() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public String getApplicationId() {
    return myApplicationId;
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
    return null;
  }

  @Override
  @NotNull
  public Map<String, ClassField> getBuildConfigFields() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public Map<String, ClassField> getResValues() {
    throw new UnsupportedOperationException();
  }

  @Override
  public InstantRun getInstantRun() {
    throw new UnsupportedOperationException();
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
}

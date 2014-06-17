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

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class AndroidArtifactStub implements AndroidArtifact {
  @NotNull private final List<File> myGeneratedResourceFolders = Lists.newArrayList();
  @NotNull private final List<File> myGeneratedSourceFolders = Lists.newArrayList();

  @NotNull private final String myName;
  @NotNull private final DependenciesStub myDependencies;
  @NotNull private final String myBuildType;
  @NotNull private final FileStructure myFileStructure;

  AndroidArtifactStub(@NotNull String name, @NotNull String buildType, @NotNull FileStructure fileStructure) {
    myName = name;
    myDependencies = new DependenciesStub();
    myBuildType = buildType;
    myFileStructure = fileStructure;
  }

  @NonNull
  @Override
  public Collection<AndroidArtifactOutput> getOutputs() {
    throw new UnsupportedOperationException();
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
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public String getSourceGenTaskName() {
    return "generate" + StringUtil.capitalize(myBuildType) + "Sources";
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public String getJavaCompileTaskName() {
    return "compile" + StringUtil.capitalize(myBuildType);
  }

  @Override
  @NotNull
  public String getAssembleTaskName() {
    return "assemble" + StringUtil.capitalize(myBuildType);
  }

  @Override
  @NotNull
  public List<File> getGeneratedSourceFolders() {
    return myGeneratedSourceFolders;
  }

  @Override
  @NotNull
  public List<File> getGeneratedResourceFolders() {
    return myGeneratedResourceFolders;
  }

  @Override
  @NotNull
  public File getClassesFolder() {
    String path = "build/classes/" + myBuildType;
    return new File(myFileStructure.getRootDir(), path);
  }

  @Override
  @NotNull
  public DependenciesStub getDependencies() {
    return myDependencies;
  }

  @Override
  @Nullable
  public SourceProvider getVariantSourceProvider() {
    return null;
  }

  @Override
  @Nullable
  public SourceProvider getMultiFlavorSourceProvider() {
    return null;
  }

  /**
   * Adds the given path to the list of generated source directories. It also creates the directory in the file system.
   *
   * @param path path of the generated source directory to add, relative to the root directory of the Android project.
   */
  public void addGeneratedSourceFolder(@NotNull String path) {
    File directory = myFileStructure.createProjectDir(path);
    myGeneratedSourceFolders.add(directory);
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

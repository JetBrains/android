/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Dependencies;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.capitalize;

public class BaseArtifactStub implements BaseArtifact {
  @NotNull protected final String myName;
  @NotNull protected final String myDirName;
  @NotNull protected final DependenciesStub myDependencies;
  @NotNull protected final String myBuildType;
  @NotNull protected final FileStructure myFileStructure;
  @NotNull private final List<File> myGeneratedSourceFolders = Lists.newArrayList();

  public BaseArtifactStub(@NotNull String name, @NotNull String dirName, @NotNull DependenciesStub dependencies, @NotNull String buildType,
                          @NotNull FileStructure fileStructure) {
    myName = name;
    myDirName = dirName;
    myDependencies = dependencies;
    myBuildType = buildType;
    myFileStructure = fileStructure;
  }


  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public String getCompileTaskName() {
    return "compile" + capitalize(myBuildType);
  }

  @Override
  @NotNull
  public String getAssembleTaskName() {
    return "assemble" + capitalize(myBuildType);
  }

  @Override
  @NotNull
  public File getClassesFolder() {
    String path = "build/intermediates/classes/" + myDirName;
    return new File(myFileStructure.getRootDir(), path);
  }

  @NonNull
  @Override
  public File getJavaResourcesFolder() {
    String path = "build/intermediates/javaResources/" + myDirName;
    return new File(myFileStructure.getRootDir(), path);
  }

  @Override
  @NotNull
  public DependenciesStub getDependencies() {
    return myDependencies;
  }

  @Override
  @NotNull
  public Dependencies getCompileDependencies() {
    return myDependencies;
  }

  @Override
  @NotNull
  public Dependencies getPackageDependencies() {
    throw new UnsupportedOperationException();
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

  @Override@NotNull
  public Set<String> getIdeSetupTaskNames() {
    return Collections.emptySet();
  }

  @Override
  @NotNull
  public List<File> getGeneratedSourceFolders() {
    return myGeneratedSourceFolders;
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
}

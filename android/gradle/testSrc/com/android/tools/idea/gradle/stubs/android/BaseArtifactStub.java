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
import com.android.builder.model.SourceProvider;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.tools.idea.gradle.stubs.FileStructure;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.capitalize;

public class BaseArtifactStub implements BaseArtifact {
  @NotNull protected final String myName;
  @NotNull protected final String myFolderName;
  @NotNull protected final DependenciesStub myDependencies;
  @NotNull protected final String myBuildType;
  @NotNull protected final FileStructure myFileStructure;
  @NotNull private final List<File> myGeneratedSourceFolders = new ArrayList<>();
  @NotNull private final Set<File> myAdditionalClassesFolders = new HashSet<>();

  public BaseArtifactStub(@NotNull String name,
                          @NotNull String folderName,
                          @NotNull DependenciesStub dependencies,
                          @NotNull String buildType,
                          @NotNull FileStructure fileStructure) {
    myName = name;
    myFolderName = folderName;
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
    String path = "build/intermediates/classes/" + myFolderName;
    return new File(myFileStructure.getRootFolderPath(), path);
  }

  @NotNull
  @Override
  public Set<File> getAdditionalClassesFolders() {
    return myAdditionalClassesFolders;
  }

  @Override
  @NotNull
  public File getJavaResourcesFolder() {
    String path = "build/intermediates/javaResources/" + myFolderName;
    return new File(myFileStructure.getRootFolderPath(), path);
  }

  @Override
  @NotNull
  public DependenciesStub getDependencies() {
    return myDependencies;
  }

  @Override
  @NotNull
  @Deprecated
  public DependenciesStub getCompileDependencies() {
    return getDependencies();
  }

  @Override
  @NotNull
  public DependencyGraphs getDependencyGraphs() {
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

  @Override
  @NotNull
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
    addGeneratedSourceFolder(directory);
  }

  public void addGeneratedSourceFolder(@NotNull File folderPath) {
    myGeneratedSourceFolders.add(folderPath);
  }

  public void addAdditionalClassesFolder(@NotNull File folder) {
    myAdditionalClassesFolders.add(folder);
  }

  @NonNull
  @Override
  public String getAssembleTaskOutputListingFile() {
    return new File(myFileStructure.getRootFolderPath(), "build/output/apk/" + myBuildType + "/output.json").getAbsolutePath();
  }
}

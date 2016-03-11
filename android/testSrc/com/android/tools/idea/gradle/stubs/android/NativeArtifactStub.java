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

import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.android.builder.model.NativeFolder;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class NativeArtifactStub implements NativeArtifact {
  @NotNull private final String myName;
  @NotNull private final File myOutputFile;
  @NotNull private final FileStructure myFileStructure;
  @NotNull private final List<NativeFolder> myNativeFolders = Lists.newArrayList();
  @NotNull private final List<NativeFile> myNativeFiles = Lists.newArrayList();
  @NotNull private final List<File> myExportedHeaders = Lists.newArrayList();

  public NativeArtifactStub(@NotNull String name, @NotNull File outputFile, @NotNull FileStructure fileStructure) {
    myName = name;
    myOutputFile = outputFile;
    myFileStructure = fileStructure;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getToolChain() {
    return "";
  }

  @NotNull
  @Override
  public String getGroupName() {
    return myName;
  }

  @Override
  @NotNull
  public String getAssembleTaskName() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Collection<NativeFolder> getSourceFolders() {
    return myNativeFolders;
  }

  public void addSourceFolder(@NotNull String path) {
    File directory = myFileStructure.createProjectDir(path);
    myNativeFolders.add(new NativeFolderStub(directory));
  }

  @NotNull
  @Override
  public Collection<NativeFile> getSourceFiles() {
    return myNativeFiles;
  }

  public void addSourceFile(@NotNull String path) {
    File file = myFileStructure.createProjectFile(path);
    myNativeFiles.add(new NativeFileStub(file));
  }

  @NotNull
  @Override
  public Collection<File> getExportedHeaders() {
    return myExportedHeaders;
  }

  public void addExportedHeaders(@NotNull String dirPath) {
    File directory = myFileStructure.createProjectDir(dirPath);
    myExportedHeaders.add(directory);
  }

  @NotNull
  @Override
  public File getOutputFile() {
    return myOutputFile;
  }
}

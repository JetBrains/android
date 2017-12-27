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

import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.android.builder.model.NativeFolder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class NativeArtifactStub extends BaseStub implements NativeArtifact {
  @NotNull private final String myName;
  @NotNull private final String myToolChain;
  @NotNull private final String myGroupName;
  @NotNull private final String myAssembleTaskName;
  @NotNull private final Collection<NativeFolder> mySourceFolders;
  @NotNull private final Collection<NativeFile> mySourceFiles;
  @NotNull private final Collection<File> myExportedHeaders;
  @NotNull private final String myAbi;
  @NotNull private final String myTargetName;
  @NotNull private final File myOutputFile;
  @NotNull private final Collection<File> myRuntimeFiles;

  public NativeArtifactStub() {
    this("name", "toolChain", "groupName", "assembleTaskName", Collections.singletonList(new NativeFolderStub()),
         Collections.singletonList(new NativeFileStub()), Collections.singletonList(new File("exportHeadher")), "abi",
         "targetName", new File("outputFile"), Collections.singletonList(new File("runtimeFile")));
  }

  public NativeArtifactStub(@NotNull String name,
                            @NotNull String toolChain,
                            @NotNull String groupName,
                            @NotNull String assembleTaskName,
                            @NotNull Collection<NativeFolder> folders,
                            @NotNull Collection<NativeFile> files,
                            @NotNull Collection<File> exportedHeaders,
                            @NotNull String abi,
                            @NotNull String targetName,
                            @NotNull File outputFile,
                            @NotNull Collection<File> runtimeFiles) {
    myName = name;
    myToolChain = toolChain;
    myGroupName = groupName;
    myAssembleTaskName = assembleTaskName;
    mySourceFolders = folders;
    mySourceFiles = files;
    myExportedHeaders = exportedHeaders;
    myAbi = abi;
    myTargetName = targetName;
    myOutputFile = outputFile;
    myRuntimeFiles = runtimeFiles;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public String getToolChain() {
    return myToolChain;
  }

  @Override
  @NotNull
  public String getGroupName() {
    return myGroupName;
  }

  @Override
  @NotNull
  public String getAssembleTaskName() {
    return myAssembleTaskName;
  }

  @Override
  @NotNull
  public Collection<NativeFolder> getSourceFolders() {
    return mySourceFolders;
  }

  @Override
  @NotNull
  public Collection<NativeFile> getSourceFiles() {
    return mySourceFiles;
  }

  @Override
  @NotNull
  public Collection<File> getExportedHeaders() {
    return myExportedHeaders;
  }

  @Override
  @NotNull
  public String getAbi() {
    return myAbi;
  }

  @Override
  @NotNull
  public String getTargetName() {
    return myTargetName;
  }

  @Override
  @NotNull
  public File getOutputFile() {
    return myOutputFile;
  }

  @Override
  @NotNull
  public Collection<File> getRuntimeFiles() {
    return myRuntimeFiles;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NativeArtifact)) {
      return false;
    }
    NativeArtifact artifact = (NativeArtifact)o;
    return Objects.equals(getName(), artifact.getName()) &&
           Objects.equals(getToolChain(), artifact.getToolChain()) &&
           Objects.equals(getGroupName(), artifact.getGroupName()) &&
           equals(artifact, NativeArtifact::getAssembleTaskName) &&
           Objects.equals(getSourceFolders(), artifact.getSourceFolders()) &&
           Objects.equals(getSourceFiles(), artifact.getSourceFiles()) &&
           Objects.equals(getExportedHeaders(), artifact.getExportedHeaders()) &&
           equals(artifact, NativeArtifact::getAbi) &&
           equals(artifact, NativeArtifact::getTargetName) &&
           Objects.equals(getOutputFile(), artifact.getOutputFile()) &&
           equals(artifact, NativeArtifact::getRuntimeFiles);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getToolChain(), getGroupName(), getAssembleTaskName(), getSourceFolders(), getSourceFiles(),
                        getExportedHeaders(), getAbi(), getTargetName(), getOutputFile(), getRuntimeFiles());
  }
}

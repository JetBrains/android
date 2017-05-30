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

import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.android.builder.model.NativeFolder;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

public final class IdeNativeArtifact extends IdeModel implements NativeArtifact {
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
  private final int myHashCode;

  public IdeNativeArtifact(@NotNull NativeArtifact artifact, @NotNull ModelCache modelCache) {
    super(artifact, modelCache);
    myName = artifact.getName();
    myToolChain = artifact.getToolChain();
    myGroupName = artifact.getGroupName();
    myAssembleTaskName = artifact.getAssembleTaskName();
    mySourceFolders = copy(artifact.getSourceFolders(), modelCache, folder -> new IdeNativeFolder(folder, modelCache));
    mySourceFiles = copy(artifact.getSourceFiles(), modelCache, file -> new IdeNativeFile(file, modelCache));
    myExportedHeaders = new ArrayList<>(artifact.getExportedHeaders());
    myAbi = artifact.getAbi();
    myTargetName = artifact.getTargetName();
    myOutputFile = artifact.getOutputFile();
    myRuntimeFiles = new ArrayList<>(artifact.getRuntimeFiles());
    myHashCode = calculateHashCode();
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
    if (!(o instanceof IdeNativeArtifact)) {
      return false;
    }
    IdeNativeArtifact artifact = (IdeNativeArtifact)o;
    return Objects.equals(myName, artifact.myName) &&
           Objects.equals(myToolChain, artifact.myToolChain) &&
           Objects.equals(myGroupName, artifact.myGroupName) &&
           Objects.equals(myAssembleTaskName, artifact.myAssembleTaskName) &&
           Objects.equals(mySourceFolders, artifact.mySourceFolders) &&
           Objects.equals(mySourceFiles, artifact.mySourceFiles) &&
           Objects.equals(myExportedHeaders, artifact.myExportedHeaders) &&
           Objects.equals(myAbi, artifact.myAbi) &&
           Objects.equals(myTargetName, artifact.myTargetName) &&
           Objects.equals(myOutputFile, artifact.myOutputFile) &&
           Objects.equals(myRuntimeFiles, artifact.myRuntimeFiles);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myName, myToolChain, myGroupName, myAssembleTaskName, mySourceFolders, mySourceFiles, myExportedHeaders, myAbi,
                        myTargetName, myOutputFile, myRuntimeFiles);
  }

  @Override
  public String toString() {
    return "IdeNativeArtifact{" +
           "myName='" + myName + '\'' +
           ", myToolChain='" + myToolChain + '\'' +
           ", myGroupName='" + myGroupName + '\'' +
           ", myAssembleTaskName='" + myAssembleTaskName + '\'' +
           ", mySourceFolders=" + mySourceFolders +
           ", mySourceFiles=" + mySourceFiles +
           ", myExportedHeaders=" + myExportedHeaders +
           ", myAbi='" + myAbi + '\'' +
           ", myTargetName='" + myTargetName + '\'' +
           ", myOutputFile=" + myOutputFile +
           ", myRuntimeFiles=" + myRuntimeFiles +
           "}";
  }
}

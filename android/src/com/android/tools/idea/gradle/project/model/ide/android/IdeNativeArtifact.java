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
import com.google.common.collect.ImmutableList;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Objects;

public final class IdeNativeArtifact extends IdeModel implements NativeArtifact {
  @NotNull private final String myName;
  @NotNull private final String myToolChain;
  @NotNull private final String myGroupName;
  @NotNull private final Collection<NativeFolder> mySourceFolders;
  @NotNull private final Collection<NativeFile> mySourceFiles;
  @NotNull private final Collection<File> myExportedHeaders;
  @NotNull private final File myOutputFile;
  @Nullable private final String myAbi;
  @Nullable private final String myTargetName;
  private final int myHashCode;

  public IdeNativeArtifact(@NotNull NativeArtifact artifact, @NotNull ModelCache modelCache) {
    super(artifact, modelCache);
    myName = artifact.getName();
    myToolChain = artifact.getToolChain();
    myGroupName = artifact.getGroupName();
    mySourceFolders = copy(artifact.getSourceFolders(), modelCache, folder -> new IdeNativeFolder(folder, modelCache));
    mySourceFiles = copy(artifact.getSourceFiles(), modelCache, file -> new IdeNativeFile(file, modelCache));
    myExportedHeaders = ImmutableList.copyOf(artifact.getExportedHeaders());
    myAbi = copyNewProperty(artifact::getAbi, null);
    myTargetName = copyNewProperty(artifact::getTargetName, null);
    myOutputFile = artifact.getOutputFile();
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
    throw new UnusedModelMethodException("getAssembleTaskName");
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
    if (myAbi != null) {
      return myAbi;
    }
    throw new UnsupportedMethodException("Unsupported method: NativeArtifact.getAbi()");
  }

  @Override
  @NotNull
  public String getTargetName() {
    if (myTargetName != null) {
      return myTargetName;
    }
    throw new UnsupportedMethodException("Unsupported method: NativeArtifact.getTargetName()");
  }

  @Override
  @NotNull
  public File getOutputFile() {
    return myOutputFile;
  }

  @Override
  @NotNull
  public Collection<File> getRuntimeFiles() {
    throw new UnusedModelMethodException("getRuntimeFiles");
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
           Objects.equals(mySourceFolders, artifact.mySourceFolders) &&
           Objects.equals(mySourceFiles, artifact.mySourceFiles) &&
           Objects.equals(myExportedHeaders, artifact.myExportedHeaders) &&
           Objects.equals(myAbi, artifact.myAbi) &&
           Objects.equals(myTargetName, artifact.myTargetName) &&
           Objects.equals(myOutputFile, artifact.myOutputFile);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myName, myToolChain, myGroupName, mySourceFolders, mySourceFiles, myExportedHeaders, myAbi, myTargetName,
                        myOutputFile);
  }

  @Override
  public String toString() {
    return "IdeNativeArtifact{" +
           "myName='" + myName + '\'' +
           ", myToolChain='" + myToolChain + '\'' +
           ", myGroupName='" + myGroupName + '\'' +
           ", mySourceFolders=" + mySourceFolders +
           ", mySourceFiles=" + mySourceFiles +
           ", myExportedHeaders=" + myExportedHeaders +
           ", myAbi='" + myAbi + '\'' +
           ", myTargetName='" + myTargetName + '\'' +
           ", myOutputFile=" + myOutputFile +
           "}";
  }
}

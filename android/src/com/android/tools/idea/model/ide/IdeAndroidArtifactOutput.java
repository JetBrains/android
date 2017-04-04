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
package com.android.tools.idea.model.ide;

import com.android.build.OutputFile;
import com.android.builder.model.AndroidArtifactOutput;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

/**
 * Creates a deep copy of {@link AndroidArtifactOutput}.
 *
 * @see IdeAndroidProject
 */
final public class IdeAndroidArtifactOutput implements AndroidArtifactOutput, Serializable {
  @NotNull private final OutputFile myMainOutputFile;
  @NotNull private final Collection<IdeOutputFile> myOutputs;
  @NotNull private final File mySplitFolder;
  @NotNull private final String myAssembleTaskName;
  @NotNull private final File myGeneratedManifest;
  private final int myVersionCode;

  public IdeAndroidArtifactOutput(@NotNull AndroidArtifactOutput output) {
    myMainOutputFile = new IdeOutputFile(output.getMainOutputFile());

    myOutputs = new ArrayList<>();
    for (OutputFile file : output.getOutputs()) {
      myOutputs.add(new IdeOutputFile(file));
    }

    mySplitFolder = output.getSplitFolder();
    myAssembleTaskName = output.getAssembleTaskName();
    myGeneratedManifest = output.getGeneratedManifest();
    myVersionCode = output.getVersionCode();
  }

  @Override
  @NotNull
  public OutputFile getMainOutputFile() {
    return myMainOutputFile;
  }

  @Override
  @NotNull
  public Collection<? extends OutputFile> getOutputs() {
    return myOutputs;
  }

  @Override
  @NotNull
  public File getSplitFolder() {
    return mySplitFolder;
  }
  @Override
  @NotNull
  public String getAssembleTaskName() {
    return myAssembleTaskName;
  }

  @Override
  @NotNull
  public File getGeneratedManifest() {
    return myGeneratedManifest;
  }


  @Override
  public int getVersionCode() {
    return myVersionCode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AndroidArtifactOutput)) return false;
    AndroidArtifactOutput output = (AndroidArtifactOutput)o;
    return Objects.equals(getMainOutputFile(), output.getMainOutputFile()) &&

           getOutputs().containsAll(output.getOutputs()) &&
           output.getOutputs().containsAll(getOutputs()) &&

           Objects.equals(getSplitFolder(), output.getSplitFolder()) &&
           Objects.equals(getAssembleTaskName(), output.getAssembleTaskName()) &&
           Objects.equals(getGeneratedManifest(), output.getGeneratedManifest()) &&
           getVersionCode() == output.getVersionCode();
  }

  @Override
  public int hashCode() {
    return Objects
      .hash(getMainOutputFile(), getOutputs(), getSplitFolder(), getAssembleTaskName(), getGeneratedManifest(), getVersionCode());
  }
}

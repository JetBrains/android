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

import com.android.build.FilterData;
import com.android.build.OutputFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import static com.intellij.openapi.util.io.FileUtil.fileHashCode;

/**
 * Creates a deep copy of an {@link OutputFile}.
 */
public final class IdeOutputFile extends IdeModel implements OutputFile {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final String myOutputType;
  @NotNull private final Collection<String> myFilterTypes;
  @NotNull private final Collection<FilterData> myFilters;
  @NotNull private final File myOutputFile;
  @NotNull private final OutputFile myMainOutputFile;
  @NotNull private final Collection<? extends OutputFile> myOutputs;
  private final int myVersionCode;

  public IdeOutputFile(@NotNull OutputFile file, @NotNull ModelCache modelCache) {
    super(file, modelCache);
    myOutputType = file.getOutputType();
    myFilterTypes = new ArrayList<>(file.getFilterTypes());
    myFilters = copy(file.getFilters(), modelCache, data -> new IdeFilterData(data, modelCache));
    myOutputFile = file.getOutputFile();
    myMainOutputFile = modelCache.computeIfAbsent(file.getMainOutputFile(), outputFile -> new IdeOutputFile(outputFile, modelCache));
    //noinspection deprecation
    myOutputs = copy(file.getOutputs(), modelCache, outputFile -> new IdeOutputFile(outputFile, modelCache));
    myVersionCode = file.getVersionCode();
  }

  @Override
  @NotNull
  public String getOutputType() {
    return myOutputType;
  }

  @Override
  @NotNull
  public Collection<String> getFilterTypes() {
    return myFilterTypes;
  }

  @Override
  @NotNull
  public Collection<FilterData> getFilters() {
    return myFilters;
  }

  @Override
  @NotNull
  public File getOutputFile() {
    return myOutputFile;
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
  public int getVersionCode() {
    return myVersionCode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeOutputFile)) {
      return false;
    }

    IdeOutputFile file = (IdeOutputFile)o;
    return myVersionCode == file.myVersionCode &&
           Objects.equals(myOutputType, file.myOutputType) &&
           Objects.equals(myFilterTypes, file.myFilterTypes) &&
           Objects.equals(myFilters, file.myFilters) &&
           Objects.equals(myOutputFile, file.myOutputFile) &&
           Objects.equals(myOutputs, file.myOutputs)
           && mainOutputFileEquals(file);
  }

  private boolean mainOutputFileEquals(@NotNull IdeOutputFile file) {
    // Avoid stack overflow.
    return myMainOutputFile == this ? file.myMainOutputFile == file : myMainOutputFile.equals(file.myMainOutputFile);
  }

  @Override
  public int hashCode() {
    int result = myOutputType.hashCode();
    result = 31 * result + myFilterTypes.hashCode();
    result = 31 * result + myFilters.hashCode();
    result = 31 * result + fileHashCode(myOutputFile);
    result = 31 * result + hashCode(myMainOutputFile);
    result = 31 * result + hashCode(myOutputs);
    result = 31 * result + myVersionCode;
    return result;
  }

  private int hashCode(@NotNull Collection<? extends OutputFile> outputFiles) {
    int hashCode = 1;
    for (OutputFile outputFile : outputFiles) {
      hashCode = 31 * hashCode + hashCode(outputFile);
    }
    return hashCode;
  }

  private int hashCode(@NotNull OutputFile outputFile) {
    return outputFile != this ? Objects.hashCode(outputFile) : 1;
  }

  @Override
  public String toString() {
    return "IdeOutputFile{" +
           "myOutputType='" + myOutputType + '\'' +
           ", myFilterTypes=" + myFilterTypes +
           ", myFilters=" + myFilters +
           ", myOutputFile=" + myOutputFile +
           ", myMainOutputFile=" + (myMainOutputFile != this ? myMainOutputFile : "this") + // Avoid stack overflow.
           ", myOutputs=" + myOutputs +
           ", myVersionCode=" + myVersionCode +
           "}";
  }
}

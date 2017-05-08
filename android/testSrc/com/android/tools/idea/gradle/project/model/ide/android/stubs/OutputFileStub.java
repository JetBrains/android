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

import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public final class OutputFileStub extends BaseStub implements OutputFile {
  @NotNull private final String myOutputType;
  @NotNull private final Collection<String> myFilterTypes;
  @NotNull private final Collection<FilterData> myFilters;
  @NotNull private final File myOutputFile;
  @NotNull private final Collection<OutputFile> myOutputs;
  private final int myVersionCode;

  public OutputFileStub() {
    this(Collections.emptyList());
  }

  public OutputFileStub(@NotNull Collection<OutputFile> outputs) {
    this("type", Lists.newArrayList("filterType1"), Lists.newArrayList(new FilterDataStub()), new File("output"), outputs, 1);
  }

  public OutputFileStub(@NotNull String type,
                        @NotNull Collection<String> filterTypes,
                        @NotNull Collection<FilterData> filters,
                        @NotNull File outputFile,
                        @NotNull Collection<OutputFile> outputs,
                        int versionCode) {
    myOutputType = type;
    myFilterTypes = filterTypes;
    myFilters = filters;
    myOutputFile = outputFile;
    myOutputs = new ArrayList<>(outputs);
    myVersionCode = versionCode;
  }

  public void addOutputFile(@NotNull OutputFile file) {
    myOutputs.add(file);
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
    return this;
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
    if (!(o instanceof OutputFile)) {
      return false;
    }
    OutputFile outputFile = (OutputFile)o;
    return getVersionCode() == outputFile.getVersionCode() &&
           Objects.equals(getOutputType(), outputFile.getOutputType()) &&
           Objects.equals(getFilterTypes(), outputFile.getFilterTypes()) &&
           Objects.equals(getFilters(), outputFile.getFilters()) &&
           Objects.equals(getOutputFile(), outputFile.getOutputFile()) &&
           Objects.equals(getOutputs(), outputFile.getOutputs());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getOutputType(), getFilterTypes(), getFilters(), getOutputFile(), hashCode(getOutputs()), getVersionCode());
  }

  private int hashCode(@NotNull Collection<? extends OutputFile> outputFiles) {
    int hashCode = 1;
    for (OutputFile outputFile : outputFiles) {
      hashCode = 31 * hashCode + hashCode(outputFile);
    }
    return hashCode;
  }

  private int hashCode(@Nullable OutputFile outputFile) {
    return outputFile != this ? Objects.hashCode(outputFile) : 1;
  }

  @Override
  public String toString() {
    return "OutputFileStub{" +
           "myOutputType='" + myOutputType + '\'' +
           ", myFilterTypes=" + myFilterTypes +
           ", myFilters=" + myFilters +
           ", myOutputFile=" + myOutputFile +
           ", myOutputs=" + toString(myOutputs) +
           ", myVersionCode=" + myVersionCode +
           "}";
  }

  @NotNull
  private String toString(@NotNull Collection<? extends OutputFile> outputFiles) {
    int max = outputFiles.size() - 1;
    if (max == -1) {
      return "[]";
    }

    StringBuilder b = new StringBuilder();
    b.append('[');
    int i = 0;
    for (OutputFile file : outputFiles) {
      b.append(toString(file));
      if (i++ == max) {
        b.append(']');
        break;
      }
      b.append(", ");
    }
    return b.toString();
  }

  @NotNull
  private String toString(@Nullable OutputFile outputFile) {
    if (outputFile == this) {
      return "this";
    }
    return Objects.toString(outputFile);
  }
}

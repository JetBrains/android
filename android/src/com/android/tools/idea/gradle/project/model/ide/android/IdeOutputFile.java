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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * Creates a deep copy of {@link OutputFile}.
 *
 * @see IdeAndroidProject
 */
public class IdeOutputFile implements OutputFile, Serializable {
  @NotNull private final String myOutputType;
  @NotNull private final Collection<String> myFilterTypes;
  @NotNull private final Collection<FilterData> myFilters;
  @NotNull private final File myOutputFile;
  @NotNull private final OutputFile myMainOutputFile;
  @NotNull private final Collection<IdeOutputFile> myOutputs;
  private final int myVersionCode;

  @SuppressWarnings("deprecation")
  public IdeOutputFile(@NotNull OutputFile file) {
    myOutputType = file.getOutputType();
    myFilterTypes = new HashSet<>(file.getFilterTypes());

    myFilters = new HashSet<>();
    for (FilterData data : file.getFilters()) {
      myFilters.add(new IdeFilterData(data));
    }

    myOutputFile = file.getOutputFile();
    myMainOutputFile = new IdeOutputFile(file.getMainOutputFile());

    myOutputs = new ArrayList<>();
    for (OutputFile output : file.getOutputs()) {
      myOutputs.add(new IdeOutputFile(output));
    }

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
}

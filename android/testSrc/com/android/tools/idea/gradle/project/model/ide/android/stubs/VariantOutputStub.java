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
import com.android.build.VariantOutput;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class VariantOutputStub extends BaseStub implements VariantOutput {
  @NotNull private final OutputFile myMainOutputFile;
  @NotNull private final Collection<? extends OutputFile> myOutputs;
  @NotNull private final String myOutputType;
  @NotNull private final Collection<String> myFilterTypes;
  @NotNull private final Collection<FilterData> myFilters;
  private final int myVersionCode;

  public VariantOutputStub() {
    this(new OutputFileStub(), Collections.singletonList(new OutputFileStub()), "type", Arrays.asList("type1", "type2"),
         Collections.singletonList(new FilterDataStub()), 1);
  }

  public VariantOutputStub(@NotNull OutputFile mainOutputFile,
                           @NotNull Collection<? extends OutputFile> outputs,
                           @NotNull String outputType,
                           @NotNull Collection<String> filterTypes,
                           @NotNull Collection<FilterData> filters,
                           int versionCode) {
    myMainOutputFile = mainOutputFile;
    myOutputs = outputs;
    myOutputType = outputType;
    myFilterTypes = filterTypes;
    myFilters = filters;
    myVersionCode = versionCode;
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
  public int getVersionCode() {
    return myVersionCode;
  }

  @Override
  public String toString() {
    return "VariantOutputStub{" +
           "myMainOutputFile=" + myMainOutputFile +
           ", myOutputs=" + myOutputs +
           ", myOutputType='" + myOutputType + '\'' +
           ", myFilterTypes=" + myFilterTypes +
           ", myFilters=" + myFilters +
           ", myVersionCode=" + myVersionCode +
           "}";
  }
}

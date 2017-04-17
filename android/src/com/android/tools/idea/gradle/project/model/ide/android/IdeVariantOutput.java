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
import com.android.build.VariantOutput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

/**
 * Creates a deep copy of a {@link VariantOutput}.
 */
public abstract class IdeVariantOutput extends IdeModel implements VariantOutput {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final OutputFile myMainOutputFile;
  @NotNull private final Collection<? extends OutputFile> myOutputs;
  @NotNull private final String myOutputType;
  @NotNull private final Collection<String> myFilterTypes;
  @NotNull private final Collection<FilterData> myFilters;
  private final int myVersionCode;

  public IdeVariantOutput(@NotNull VariantOutput output, @NotNull ModelCache modelCache) {
    super(output, modelCache);
    myMainOutputFile = modelCache.computeIfAbsent(output.getMainOutputFile(), outputFile -> new IdeOutputFile(outputFile, modelCache));
    //noinspection deprecation
    myOutputs = copy(output.getOutputs(), modelCache, outputFile -> new IdeOutputFile(outputFile, modelCache));
    myOutputType = output.getOutputType();
    myFilterTypes = new ArrayList<>(output.getFilterTypes());
    myFilters = copy(output.getFilters(), modelCache, data -> new IdeFilterData(data, modelCache));
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
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeVariantOutput)) {
      return false;
    }
    IdeVariantOutput output = (IdeVariantOutput)o;
    return output.canEquals(this) &&
           myVersionCode == output.myVersionCode &&
           Objects.equals(myMainOutputFile, output.myMainOutputFile) &&
           Objects.equals(myOutputs, output.myOutputs) &&
           Objects.equals(myOutputType, output.myOutputType) &&
           Objects.equals(myFilterTypes, output.myFilterTypes) &&
           Objects.equals(myFilters, output.myFilters);
  }

  protected boolean canEquals(Object other) {
    return other instanceof IdeVariantOutput;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMainOutputFile, myOutputs, myOutputType, myFilterTypes, myFilters, myVersionCode);
  }

  @Override
  public String toString() {
    return "myMainOutputFile=" + myMainOutputFile +
           ", myOutputs=" + myOutputs +
           ", myOutputType='" + myOutputType + '\'' +
           ", myFilterTypes=" + myFilterTypes +
           ", myFilters=" + myFilters +
           ", myVersionCode=" + myVersionCode;
  }
}

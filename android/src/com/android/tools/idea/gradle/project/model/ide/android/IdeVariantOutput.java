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
import com.google.common.collect.ImmutableList;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Creates a deep copy of a {@link VariantOutput}.
 */
public abstract class IdeVariantOutput extends IdeModel implements VariantOutput {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final Collection<? extends OutputFile> myOutputs;
  @NotNull private final Collection<String> myFilterTypes;
  @Nullable private final Collection<FilterData> myFilters;
  @Nullable private final OutputFile myMainOutputFile;
  @Nullable private final String myOutputType;
  private final int myVersionCode;
  private final int myHashCode;

  public IdeVariantOutput(@NotNull VariantOutput output, @NotNull ModelCache modelCache) {
    super(output, modelCache);
    //noinspection deprecation
    myOutputs = copy(output.getOutputs(), modelCache, outputFile -> new IdeOutputFile(outputFile, modelCache));
    myFilterTypes = copyNewProperty(() -> ImmutableList.copyOf(output.getFilterTypes()), Collections.emptyList());
    myFilters = copyFilters(output, modelCache);
    myMainOutputFile = copyNewProperty(modelCache, output::getMainOutputFile, file -> new IdeOutputFile(file, modelCache), null);
    myOutputType = copyNewProperty(output::getOutputType, null);
    myVersionCode = output.getVersionCode();

    myHashCode = calculateHashCode();
  }

  @Nullable
  private static Collection<FilterData> copyFilters(@NotNull VariantOutput output, @NotNull ModelCache modelCache) {
    try {
      return copy(output.getFilters(), modelCache, data -> new IdeFilterData(data, modelCache));
    }
    catch (UnsupportedMethodException ignored) {
      return null;
    }
  }

  @Override
  @NotNull
  public OutputFile getMainOutputFile() {
    if (myMainOutputFile != null) {
      return myMainOutputFile;
    }
    throw new UnsupportedMethodException("getMainOutputFile()");
  }

  @Override
  @NotNull
  public Collection<? extends OutputFile> getOutputs() {
    return myOutputs;
  }

  @Override
  @NotNull
  public String getOutputType() {
    if (myOutputType != null) {
      return myOutputType;
    }
    throw new UnsupportedMethodException("getOutputType");
  }

  @Override
  @NotNull
  public Collection<String> getFilterTypes() {
    return myFilterTypes;
  }

  @Override
  @NotNull
  public Collection<FilterData> getFilters() {
    if (myFilters != null) {
      return myFilters;
    }
    throw new UnsupportedMethodException("getFilters");
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
    return myHashCode;
  }

  protected int calculateHashCode() {
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

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

import com.android.build.FilterData;
import com.android.build.OutputFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

/**
 * Creates a deep copy of {@link OutputFile}.
 *
 * @see IdeAndroidProject
 */
final public class IdeOutputFile implements OutputFile, Serializable {
  @NotNull private final String myOutputType;
  @NotNull private final Collection<String> myFilterTypes;
  @NotNull private final Collection<FilterData> myFilters;
  @NotNull private final File myOutputFile;

  public IdeOutputFile(@NotNull OutputFile file) {
    myOutputType = file.getOutputType();
    myFilterTypes = new HashSet<>(file.getFilterTypes());

    myFilters = new HashSet<>();
    for (FilterData data : file.getFilters()) {
      myFilters.add(new IdeFilterData(data));
    }

    myOutputFile = file.getOutputFile();
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OutputFile)) return false;
    OutputFile file = (OutputFile)o;
    return Objects.equals(getOutputType(), file.getOutputType()) &&
           Objects.equals(getOutputFile(), file.getOutputFile()) &&

           getFilterTypes().containsAll(file.getFilterTypes()) &&
           file.getFilterTypes().containsAll(getFilterTypes()) &&

           getFilters().containsAll(file.getFilters()) &&
           file.getFilters().containsAll(getFilters());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getOutputType(), getFilterTypes(), getFilters(), getOutputFile());
  }
}

/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.stubs.android;

import com.android.annotations.NonNull;
import com.android.build.FilterData;
import com.android.build.OutputFile;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class OutputFileStub implements OutputFile {

  private final File myOutputFile;
  private String myOutputType = "outputType";

  public OutputFileStub(File outputFile) {
    myOutputFile = outputFile;
  }

  @NonNull
  @Override
  public String getOutputType() {
    return myOutputType;
  }

  @NonNull
  @Override
  public Collection<String> getFilterTypes() {
    return Collections.emptyList();
  }

  @NonNull
  @Override
  public Collection<FilterData> getFilters() {
    return Collections.emptyList();
  }

  @NonNull
  @Override
  public File getOutputFile() {
    return myOutputFile;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OutputFile)) return false;
    OutputFile file = (OutputFile)o;
    return Objects.equals(getOutputFile(), file.getOutputFile()) &&
           Objects.equals(getOutputType(), file.getOutputType());
  }
}

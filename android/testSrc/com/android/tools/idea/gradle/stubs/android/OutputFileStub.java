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

public class OutputFileStub implements OutputFile {

  private final File myOutputFile;

  public OutputFileStub(File outputFile) {
    myOutputFile = outputFile;
  }

  @NonNull
  @Override
  public String getOutputType() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public Collection<String> getFilterTypes() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public Collection<FilterData> getFilters() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  @Override
  public File getOutputFile() {
    return myOutputFile;
  }
}

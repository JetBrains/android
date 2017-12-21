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

import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.android.builder.model.AndroidArtifactOutput;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public class AndroidArtifactOutputStubOld implements AndroidArtifactOutput {
  @NotNull
  private final Collection<OutputFile> myOutputs;

  public AndroidArtifactOutputStubOld(@NotNull OutputFile output) {
    this(Collections.singletonList(output));
  }

  public AndroidArtifactOutputStubOld(@NotNull Collection<OutputFile> outputs) {
    myOutputs = outputs;
  }

  @Override
  @NotNull
  public String getAssembleTaskName() {
    return "assemble";
  }

  @Override
  @NotNull
  public File getGeneratedManifest() {
    return new File("test");
  }

  @Override
  @NotNull
  public String getOutputType() {
    return getMainOutputFile().getOutputType();
  }

  @Override
  @NotNull
  public Collection<String> getFilterTypes() {
    return getMainOutputFile().getFilterTypes();
  }

  @Override
  @NotNull
  public Collection<FilterData> getFilters() {
    return getMainOutputFile().getFilters();
  }

  @Override
  @NotNull
  public OutputFile getMainOutputFile() {
    return myOutputs.iterator().next();
  }

  @Override
  @NotNull
  public Collection<? extends OutputFile> getOutputs() {
    return myOutputs;
  }

  @Override
  public int getVersionCode() {
    return getMainOutputFile().getVersionCode();
  }

  @Override
  @NotNull
  public File getOutputFile() {
    return getMainOutputFile().getOutputFile();
  }
}

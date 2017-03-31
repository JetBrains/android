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
package com.android.tools.idea.model;

import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;

/**
 * Creates a deep copy of {@link VariantOutput}.
 *
 * @see IdeAndroidProject
 */
public class IdeVariantOutput implements VariantOutput, Serializable {
  @NotNull private final OutputFile myMainOutputFile;
  @NotNull private final Collection<IdeOutputFile> myOutputs;
  @NotNull private final File mySplitFolder;
  private final int myVersionCode;

  public IdeVariantOutput(@NotNull VariantOutput output) {
    myMainOutputFile = new IdeOutputFile(output.getMainOutputFile());

    myOutputs = new HashSet<>();
    for (OutputFile file : output.getOutputs()) {
      myOutputs.add(new IdeOutputFile(file));
    }

    mySplitFolder = output.getSplitFolder();
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
  public File getSplitFolder() {
    return mySplitFolder;
  }

  @Override
  public int getVersionCode() {
    return myVersionCode;
  }
}

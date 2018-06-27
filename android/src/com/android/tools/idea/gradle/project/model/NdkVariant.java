/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model;

import com.android.builder.model.NativeArtifact;
import com.android.builder.model.NativeFile;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class NdkVariant {
  @NotNull private final String myVariantName;
  @NotNull private final Map<String, NativeArtifact> myArtifactsByName = new HashMap<>();
  private final boolean myExportedHeadersSupported;

  NdkVariant(@NotNull String variantName, boolean exportedHeadersSupported) {
    myVariantName = variantName;
    myExportedHeadersSupported = exportedHeadersSupported;
  }

  void addArtifact(@NotNull NativeArtifact artifact) {
    myArtifactsByName.put(artifact.getName(), artifact);
  }

  @NotNull
  public String getName() {
    return myVariantName;
  }

  @NotNull
  public Collection<File> getSourceFolders() {
    Set<File> sourceFolders = new LinkedHashSet<>();
    for (NativeArtifact artifact : getArtifacts()) {
      if (myExportedHeadersSupported) {
        sourceFolders.addAll(artifact.getExportedHeaders());
      }
      for (NativeFile sourceFile : artifact.getSourceFiles()) {
        File parentFile = sourceFile.getFilePath().getParentFile();
        if (parentFile != null) {
          sourceFolders.add(parentFile);
        }
      }
    }
    return ImmutableList.copyOf(sourceFolders);
  }

  @NotNull
  public Collection<NativeArtifact> getArtifacts() {
    return myArtifactsByName.values();
  }
}

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

import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeArtifact;
import com.android.tools.idea.gradle.model.ndk.v1.IdeNativeFile;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public class NdkVariant {
  @NotNull private final String myVariantAbi;
  @NotNull private final Map<String, IdeNativeArtifact> myArtifactsByName;
  private final boolean myExportedHeadersSupported;

  // Used for serialization by the IDE.
  @SuppressWarnings("unused")
  public NdkVariant() {
    myVariantAbi = "";
    myArtifactsByName = Collections.emptyMap();
    myExportedHeadersSupported = false;
  }

  NdkVariant(@NotNull String variantAbi, boolean exportedHeadersSupported) {
    myVariantAbi = variantAbi;
    myArtifactsByName = new HashMap<>();
    myExportedHeadersSupported = exportedHeadersSupported;
  }

  void addArtifact(@NotNull IdeNativeArtifact artifact) {
    myArtifactsByName.put(artifact.getName(), artifact);
  }

  @NotNull
  public String getName() {
    return myVariantAbi;
  }

  @NotNull
  public Collection<File> getSourceFolders() {
    Set<File> sourceFolders = new LinkedHashSet<>();
    for (IdeNativeArtifact artifact : getArtifacts()) {
      if (myExportedHeadersSupported) {
        sourceFolders.addAll(artifact.getExportedHeaders());
      }
      for (IdeNativeFile sourceFile : artifact.getSourceFiles()) {
        File parentFile = sourceFile.getFilePath().getParentFile();
        if (parentFile != null) {
          sourceFolders.add(parentFile);
        }
      }
    }
    return ImmutableList.copyOf(sourceFolders);
  }

  @NotNull
  public Collection<IdeNativeArtifact> getArtifacts() {
    return myArtifactsByName.values();
  }
}

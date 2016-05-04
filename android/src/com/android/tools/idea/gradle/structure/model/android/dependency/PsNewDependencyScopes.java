/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android.dependency;

import com.android.tools.idea.gradle.structure.model.android.PsAndroidArtifact;
import com.android.tools.idea.gradle.structure.model.android.PsBuildType;
import com.android.tools.idea.gradle.structure.model.android.PsProductFlavor;
import com.android.tools.idea.gradle.structure.model.android.PsVariant;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PsNewDependencyScopes {
  @NotNull private final List<PsBuildType> myBuildTypes;
  @NotNull private final List<PsProductFlavor> myProductFlavors;
  @NotNull private final List<String> myArtifactNames;

  public PsNewDependencyScopes(@NotNull List<PsBuildType> buildTypes,
                               @NotNull List<PsProductFlavor> productFlavors,
                               @NotNull List<String> artifactNames) {

    myBuildTypes = buildTypes;
    myProductFlavors = productFlavors;
    myArtifactNames = artifactNames;
  }

  public boolean contains(@NotNull PsAndroidArtifact artifact) {
    PsVariant variant = artifact.getParent();
    if (myBuildTypes.contains(variant.getBuildType())) {
      List<PsProductFlavor> productFlavors = Lists.newArrayList();
      variant.forEachProductFlavor(productFlavors::add);

      if (myProductFlavors.containsAll(productFlavors)) {
        String resolvedName = artifact.getResolvedName();
        if (myArtifactNames.contains(resolvedName)) {
          return true;
        }
      }
    }
    return false;
  }
}

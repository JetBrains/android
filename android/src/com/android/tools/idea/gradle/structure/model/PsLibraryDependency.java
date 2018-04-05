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
package com.android.tools.idea.gradle.structure.model;

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import org.jetbrains.annotations.NotNull;

public interface PsLibraryDependency extends PsBaseDependency {
  @NotNull
  PsModule getParent();

  boolean hasPromotedVersion();

  @NotNull
  PsArtifactDependencySpec getSpec();

  @NotNull
  default PsArtifactDependencySpec createSpec(@NotNull ArtifactDependencyModel parsedModel) {
    String compactNotation = parsedModel.compactNotation();
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create(compactNotation);
    assert spec != null;
    return spec;
  }

  void setModified(boolean value);
}

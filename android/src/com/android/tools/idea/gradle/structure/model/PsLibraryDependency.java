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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyModel;
import com.google.common.collect.ImmutableCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Strings.nullToEmpty;

public interface PsLibraryDependency extends PsBaseDependency {
  @NotNull
  PsModule getParent();

  @Nullable
  PsArtifactDependencySpec getDeclaredSpec();

  boolean hasPromotedVersion();

  @NotNull
  ImmutableCollection<PsDependency> getTransitiveDependencies();

  default void setVersion(@NotNull String version) {
    boolean modified = false;
    ArtifactDependencyModel reference = null;
    for (DependencyModel parsedDependency : getParsedModels()) {
      if (parsedDependency instanceof ArtifactDependencyModel) {
        ArtifactDependencyModel dependency = (ArtifactDependencyModel)parsedDependency;
        dependency.setVersion(version);
        if (reference == null) {
          reference = dependency;
        }
        modified = true;
      }
    }
    if (modified) {
      GradleVersion parsedVersion = GradleVersion.parse(version);
      PsArtifactDependencySpec resolvedSpec = getResolvedSpec();
      String resolvedVersion = nullToEmpty(resolvedSpec.version);
      if (parsedVersion.compareTo(resolvedVersion) != 0) {
        // Update the "resolved" spec with the new version
        resolvedSpec = new PsArtifactDependencySpec(resolvedSpec.name, resolvedSpec.group, version);
        setResolvedSpec(resolvedSpec);
      }

      setDeclaredSpec(createSpec(reference));
      setModified(true);
      getParent().fireDependencyModifiedEvent((PsDependency)this);
    }
  }

  @NotNull
  ImmutableCollection<DependencyModel> getParsedModels();

  @NotNull
  PsArtifactDependencySpec getResolvedSpec();

  @NotNull
  default PsArtifactDependencySpec createSpec(@NotNull ArtifactDependencyModel parsedModel) {
    String compactNotation = parsedModel.compactNotation().value();
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create(compactNotation);
    assert spec != null;
    return spec;
  }

  void setResolvedSpec(@NotNull PsArtifactDependencySpec spec);

  void setDeclaredSpec(@NotNull PsArtifactDependencySpec spec);

  void setModified(boolean value);
}

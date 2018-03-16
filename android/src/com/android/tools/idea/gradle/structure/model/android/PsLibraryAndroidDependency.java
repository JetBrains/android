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
package com.android.tools.idea.gradle.structure.model.android;

import com.android.builder.model.level2.Library;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsDependency;
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.structure.model.PsDependency.TextType.PLAIN_TEXT;
import static com.intellij.util.PlatformIcons.LIBRARY_ICON;
import static java.util.stream.Collectors.toList;

public class PsLibraryAndroidDependency extends PsAndroidDependency implements PsLibraryDependency {
  @NotNull private final List<PsArtifactDependencySpec> myPomDependencies = Lists.newArrayList();
  @NotNull private PsArtifactDependencySpec mySpec;

  @Nullable private final Library myResolvedModel;

  PsLibraryAndroidDependency(@NotNull PsAndroidModule parent,
                             @NotNull PsArtifactDependencySpec spec,
                             @NotNull Collection<PsAndroidArtifact> containers,
                             @Nullable Library resolvedModel,
                             @NotNull Collection<ArtifactDependencyModel> parsedModels) {
    super(parent, containers, parsedModels);
    mySpec = spec;
    myResolvedModel = resolvedModel;
  }

  @Override
  @Nullable
  public Library getResolvedModel() {
    return myResolvedModel;
  }

  void setDependenciesFromPomFile(@NotNull List<PsArtifactDependencySpec> pomDependencies) {
    myPomDependencies.clear();
    myPomDependencies.addAll(pomDependencies);
  }

  @NotNull
  public ImmutableCollection<PsLibraryAndroidDependency> getTransitiveDependencies(@NotNull PsAndroidDependencyCollection artifactDependencies) {
    ImmutableSet.Builder<PsLibraryAndroidDependency> transitive = ImmutableSet.builder();
    for (PsArtifactDependencySpec dependency : myPomDependencies) {
      // TODO(b/74948244): Include the requested version as a parsed model so that we see any promotions.
      List<PsLibraryAndroidDependency> found = artifactDependencies.findLibraryDependencies(dependency.getGroup(), dependency.getName());
      transitive.addAll(found);
    }

    return transitive.build();
  }

  @Override
  public void addParsedModel(@NotNull DependencyModel parsedModel) {
    assert parsedModel instanceof ArtifactDependencyModel;
    super.addParsedModel(parsedModel);
  }

  @Override
  @NotNull
  public PsArtifactDependencySpec getSpec() {
    return mySpec;
  }

  @Override
  @NotNull
  public String getName() {
    return mySpec.getName();
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return LIBRARY_ICON;
  }

  @Override
  @NotNull
  public String toText(@NotNull TextType type) {
    switch (type) {
      case PLAIN_TEXT:
        return mySpec.toString();
      case FOR_NAVIGATION:
        return mySpec.toString();
      default:
        return "";
    }
  }

  @Override
  public boolean hasPromotedVersion() {
    // TODO(solodkyy): Review usages in the case of declared dependencies.
    final List<PsArtifactDependencySpec> declaredSpecs =
      getParsedModels().stream().map(v -> PsArtifactDependencySpec.create((ArtifactDependencyModel)v)).collect(toList());
    for (PsArtifactDependencySpec declaredSpec : declaredSpecs) {
      if (mySpec.getVersion() != null && declaredSpec != null && declaredSpec.getVersion() != null) {
        GradleVersion declaredVersion = GradleVersion.tryParse(declaredSpec.getVersion());
        if (declaredVersion != null && declaredVersion.compareTo(mySpec.getVersion()) < 0) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return toText(PLAIN_TEXT);
  }
}

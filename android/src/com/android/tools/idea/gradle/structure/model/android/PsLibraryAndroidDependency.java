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

import com.android.builder.model.Library;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyModel;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsDependency;
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.android.tools.idea.gradle.structure.model.PsDependency.TextType.PLAIN_TEXT;
import static com.intellij.util.PlatformIcons.LIBRARY_ICON;

public class PsLibraryAndroidDependency extends PsAndroidDependency implements PsLibraryDependency {
  @NotNull private final List<PsArtifactDependencySpec> myPomDependencies = Lists.newArrayList();
  @NotNull private final Set<String> myTransitiveDependencies = Sets.newHashSet();
  @NotNull private PsArtifactDependencySpec myResolvedSpec;

  @Nullable private final Library myResolvedModel;
  @Nullable private PsArtifactDependencySpec myDeclaredSpec;

  PsLibraryAndroidDependency(@NotNull PsAndroidModule parent,
                             @NotNull PsArtifactDependencySpec resolvedSpec,
                             @NotNull PsAndroidArtifact container,
                             @Nullable Library resolvedModel,
                             @Nullable ArtifactDependencyModel parsedModel) {
    super(parent, container, parsedModel);
    myResolvedSpec = resolvedSpec;
    myResolvedModel = resolvedModel;
    if (parsedModel != null) {
      setDeclaredSpec(createSpec(parsedModel));
    }
  }

  @Override
  @Nullable
  public Library getResolvedModel() {
    return myResolvedModel;
  }

  void addTransitiveDependency(@NotNull String dependency) {
    myTransitiveDependencies.add(dependency);
  }

  void setDependenciesFromPomFile(@NotNull List<PsArtifactDependencySpec> pomDependencies) {
    myPomDependencies.clear();
    myPomDependencies.addAll(pomDependencies);
  }

  @Override
  @NotNull
  public ImmutableCollection<PsDependency> getTransitiveDependencies() {
    PsAndroidModule module = getParent();

    ImmutableSet.Builder<PsDependency> transitive = ImmutableSet.builder();
    for (String dependency : myTransitiveDependencies) {
      PsAndroidDependency found = module.findLibraryDependency(dependency);
      if (found != null) {
        transitive.add(found);
      }
    }
    for (PsArtifactDependencySpec dependency : myPomDependencies) {
      PsLibraryAndroidDependency found = module.findLibraryDependency(dependency);
      if (found != null) {
        transitive.add(found);
      }
    }

    return transitive.build();
  }

  @Override
  public void addParsedModel(@NotNull DependencyModel parsedModel) {
    assert parsedModel instanceof ArtifactDependencyModel;
    if (getParsedModels().isEmpty()) {
      myDeclaredSpec = PsArtifactDependencySpec.create((ArtifactDependencyModel)parsedModel);
    }
    super.addParsedModel(parsedModel);
  }

  @Override
  @Nullable
  public PsArtifactDependencySpec getDeclaredSpec() {
    return myDeclaredSpec;
  }

  @Override
  @NotNull
  public PsArtifactDependencySpec getResolvedSpec() {
    return myResolvedSpec;
  }

  @Override
  @NotNull
  public String getName() {
    return myResolvedSpec.name;
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
        return myResolvedSpec.toString();
      case FOR_NAVIGATION:
        PsArtifactDependencySpec spec = myDeclaredSpec;
        if (spec == null) {
          spec = myResolvedSpec;
        }
        return spec.toString();
      default:
        return "";
    }
  }

  @Override
  public boolean hasPromotedVersion() {
    if (myResolvedSpec.version != null && myDeclaredSpec != null && myDeclaredSpec.version != null) {
      GradleVersion declaredVersion = GradleVersion.tryParse(myDeclaredSpec.version);
      return declaredVersion != null && declaredVersion.compareTo(myResolvedSpec.version) < 0;
    }
    return false;
  }

  @Override
  public void setResolvedSpec(@NotNull PsArtifactDependencySpec spec) {
    myResolvedSpec = spec;
  }

  @Override
  public void setDeclaredSpec(@NotNull PsArtifactDependencySpec spec) {
    myDeclaredSpec = spec;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PsLibraryAndroidDependency that = (PsLibraryAndroidDependency)o;
    return Objects.equals(myResolvedSpec, that.myResolvedSpec);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myDeclaredSpec);
  }

  @Override
  public String toString() {
    return toText(PLAIN_TEXT);
  }
}

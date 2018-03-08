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
package com.android.tools.idea.gradle.structure.model.java;

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.model.java.JarLibraryDependency;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsDependency;
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.intellij.util.PlatformIcons.LIBRARY_ICON;

public class PsLibraryJavaDependency extends PsJavaDependency implements PsLibraryDependency {
  @NotNull private final List<PsArtifactDependencySpec> myPomDependencies = Lists.newArrayList();
  @NotNull private PsArtifactDependencySpec mySpec;

  @Nullable private final JarLibraryDependency myResolvedModel;
  @Nullable private PsArtifactDependencySpec myDeclaredSpec;

  protected PsLibraryJavaDependency(@NotNull PsJavaModule parent,
                                    @NotNull PsArtifactDependencySpec spec,
                                    @Nullable JarLibraryDependency resolvedModel,
                                    @NotNull Collection<ArtifactDependencyModel> parsedModels) {
    super(parent, parsedModels);
    mySpec = spec;
    myResolvedModel = resolvedModel;
    Optional<ArtifactDependencyModel> firstParsedModel = parsedModels.stream().findFirst();
    if (firstParsedModel.isPresent()) {
      setDeclaredSpec(createSpec(firstParsedModel.get()));
    }
  }

  @Override
  @Nullable
  public PsArtifactDependencySpec getDeclaredSpec() {
    return myDeclaredSpec;
  }

  @Override
  @NotNull
  public PsArtifactDependencySpec getSpec() {
    return mySpec;
  }

  @Override
  public boolean hasPromotedVersion() {
    return false;
  }

  @Override
  @NotNull
  public String toText(@NotNull TextType type) {
    switch (type) {
      case PLAIN_TEXT:
        return mySpec.toString();
      case FOR_NAVIGATION:
        PsArtifactDependencySpec spec = myDeclaredSpec;
        if (spec == null) {
          spec = mySpec;
        }
        return spec.toString();
      default:
        return "";
    }
  }

  void setDependenciesFromPomFile(@NotNull List<PsArtifactDependencySpec> pomDependencies) {
    myPomDependencies.clear();
    myPomDependencies.addAll(pomDependencies);
  }

  @Override
  public void setResolvedSpec(@NotNull PsArtifactDependencySpec spec) {
    mySpec = spec;
  }

  @Override
  public void setDeclaredSpec(@NotNull PsArtifactDependencySpec spec) {
    myDeclaredSpec = spec;
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
  @Nullable
  public JarLibraryDependency getResolvedModel() {
    return myResolvedModel;
  }
}

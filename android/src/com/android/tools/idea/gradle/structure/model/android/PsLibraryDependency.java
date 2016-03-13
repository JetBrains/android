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
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.util.PlatformIcons.LIBRARY_ICON;

public class PsLibraryDependency extends PsAndroidDependency {
  @NotNull private final PsArtifactDependencySpec myResolvedSpec;
  @NotNull private final List<PsArtifactDependencySpec> myPomDependencies = Lists.newArrayList();
  @NotNull private final Set<String> myTransitiveDependencies = Sets.newHashSet();

  @Nullable private final Library myResolvedModel;
  @Nullable private PsArtifactDependencySpec myDeclaredSpec;

  PsLibraryDependency(@NotNull PsAndroidModule parent,
                      @NotNull PsArtifactDependencySpec resolvedSpec,
                      @Nullable Library resolvedModel,
                      @Nullable PsAndroidArtifact container,
                      @Nullable ArtifactDependencyModel parsedModel) {
    super(parent, container, parsedModel);
    myResolvedSpec = resolvedSpec;
    myResolvedModel = resolvedModel;
    setDeclaredSpec(parsedModel);
  }

  @Override
  @Nullable
  public Library getResolvedModel() {
    return myResolvedModel;
  }

  void addTransitiveDependency(@NotNull String dependency) {
    myTransitiveDependencies.add(dependency);
  }

  public void setPomDependencies(@NotNull List<PsArtifactDependencySpec> pomDependencies) {
    myPomDependencies.clear();
    myPomDependencies.addAll(pomDependencies);
  }

  @Override
  protected void setParsedModel(@Nullable DependencyModel parsedModel) {
    super.setParsedModel(parsedModel);
    assert parsedModel instanceof ArtifactDependencyModel;
    setDeclaredSpec((ArtifactDependencyModel)parsedModel);
  }

  private void setDeclaredSpec(@Nullable ArtifactDependencyModel parsedModel) {
    PsArtifactDependencySpec declaredSpec = null;
    if (parsedModel != null) {
      String compactNotation = parsedModel.compactNotation().value();
      declaredSpec = PsArtifactDependencySpec.create(compactNotation);
    }
    myDeclaredSpec = declaredSpec;
  }

  @NotNull
  public Collection<PsAndroidDependency> getTransitiveDependencies() {
    PsAndroidModule module = getParent();

    Set<PsAndroidDependency> transitive = Sets.newHashSet();
    for (String dependency : myTransitiveDependencies) {
      PsAndroidDependency found = module.findLibraryDependency(dependency);
      if (found != null) {
        transitive.add(found);
      }
    }
    for (PsArtifactDependencySpec dependency : myPomDependencies) {
      PsLibraryDependency found = module.findLibraryDependency(dependency);
      if (found != null) {
        transitive.add(found);
      }
    }

    return transitive;
  }

  @NotNull
  public List<String> findRequestingModuleDependencies() {
    Set<String> moduleNames = Sets.newHashSet();
    findRequestingModuleDependencies(getParent(), moduleNames);
    if (moduleNames.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> sorted = Lists.newArrayList(moduleNames);
    Collections.sort(sorted);
    return sorted;
  }

  private void findRequestingModuleDependencies(@NotNull PsAndroidModule module, @NotNull Collection<String> found) {
    PsProject project = module.getParent();
    for (PsModuleDependency moduleDependency : module.getModuleDependencies()) {
      String gradlePath = moduleDependency.getGradlePath();
      PsModule foundModule = project.findModuleByGradlePath(gradlePath);
      if (foundModule instanceof PsAndroidModule) {
        PsAndroidModule androidModule = (PsAndroidModule)foundModule;

        PsLibraryDependency libraryDependency = androidModule.findLibraryDependency(myResolvedSpec);
        if (libraryDependency != null && libraryDependency.isEditable()) {
          found.add(androidModule.getName());
        }

        findRequestingModuleDependencies(androidModule, found);
      }
    }
  }

  @Nullable
  public PsArtifactDependencySpec getDeclaredSpec() {
    return myDeclaredSpec;
  }

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
  public String getValueAsText() {
    return myResolvedSpec.toString();
  }

  public boolean hasPromotedVersion() {
    if (myResolvedSpec.version != null && myDeclaredSpec != null && myDeclaredSpec.version != null) {
      GradleVersion declaredVersion = GradleVersion.tryParse(myDeclaredSpec.version);
      return declaredVersion != null && declaredVersion.compareTo(myResolvedSpec.version) < 0;
    }
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PsLibraryDependency that = (PsLibraryDependency)o;
    return Objects.equal(myResolvedSpec, that.myResolvedSpec);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myDeclaredSpec);
  }

  @Override
  public String toString() {
    return getValueAsText();
  }
}

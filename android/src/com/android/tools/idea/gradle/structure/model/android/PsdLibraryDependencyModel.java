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
import com.android.tools.idea.gradle.structure.model.PsdArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsdModuleModel;
import com.android.tools.idea.gradle.structure.model.PsdProjectModel;
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

public class PsdLibraryDependencyModel extends PsdAndroidDependencyModel {
  @NotNull private final PsdArtifactDependencySpec myResolvedSpec;
  @NotNull private final List<PsdArtifactDependencySpec> myPomDependencies = Lists.newArrayList();
  @NotNull private final Set<String> myTransitiveDependencies = Sets.newHashSet();

  @Nullable private final Library myResolvedModel;
  @Nullable private PsdArtifactDependencySpec myDeclaredSpec;

  PsdLibraryDependencyModel(@NotNull PsdAndroidModuleModel parent,
                            @NotNull PsdArtifactDependencySpec resolvedSpec,
                            @Nullable Library resolvedModel,
                            @Nullable PsdAndroidArtifactModel container,
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

  public void setPomDependencies(@NotNull List<PsdArtifactDependencySpec> pomDependencies) {
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
    PsdArtifactDependencySpec declaredSpec = null;
    if (parsedModel != null) {
      String compactNotation = parsedModel.compactNotation().value();
      declaredSpec = PsdArtifactDependencySpec.create(compactNotation);
    }
    myDeclaredSpec = declaredSpec;
  }

  @NotNull
  public Collection<PsdAndroidDependencyModel> getTransitiveDependencies() {
    PsdAndroidModuleModel moduleModel = getParent();

    Set<PsdAndroidDependencyModel> transitive = Sets.newHashSet();
    for (String dependency : myTransitiveDependencies) {
      PsdAndroidDependencyModel found = moduleModel.findLibraryDependency(dependency);
      if (found != null) {
        transitive.add(found);
      }
    }
    for (PsdArtifactDependencySpec dependency : myPomDependencies) {
      PsdLibraryDependencyModel found = moduleModel.findLibraryDependency(dependency);
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

  private void findRequestingModuleDependencies(@NotNull PsdAndroidModuleModel module, @NotNull Collection<String> found) {
    PsdProjectModel project = module.getParent();
    for (PsdModuleDependencyModel moduleDependency : module.getModuleDependencies()) {
      String gradlePath = moduleDependency.getGradlePath();
      PsdModuleModel foundModule = project.findModelByGradlePath(gradlePath);
      if (foundModule instanceof PsdAndroidModuleModel) {
        PsdAndroidModuleModel androidModule = (PsdAndroidModuleModel)foundModule;

        PsdLibraryDependencyModel libraryDependency = androidModule.findLibraryDependency(myResolvedSpec);
        if (libraryDependency != null && libraryDependency.isEditable()) {
          found.add(androidModule.getName());
        }

        findRequestingModuleDependencies(androidModule, found);
      }
    }
  }

  @Nullable
  public PsdArtifactDependencySpec getDeclaredSpec() {
    return myDeclaredSpec;
  }

  @NotNull
  public PsdArtifactDependencySpec getResolvedSpec() {
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
    PsdLibraryDependencyModel that = (PsdLibraryDependencyModel)o;
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

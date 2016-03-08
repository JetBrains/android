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
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyModel;
import com.android.tools.idea.gradle.structure.model.PsdArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsdIssue;
import com.android.tools.idea.gradle.structure.model.PsdIssue.Type;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.structure.model.PsdIssue.Type.WARNING;
import static com.intellij.util.PlatformIcons.LIBRARY_ICON;
import static icons.AndroidIcons.ProjectStructure.LibraryWarning;

public class PsdLibraryDependencyModel extends PsdAndroidDependencyModel {
  @NotNull private final PsdArtifactDependencySpec myResolvedSpec;

  @Nullable private final Library myGradleModel;
  @Nullable private final PsdArtifactDependencySpec myMismatchingRequestedSpec;
  @Nullable private final PsdIssue myProblem;

  @NotNull private final List<PsdArtifactDependencySpec> myPomDependencies = Lists.newArrayList();
  @NotNull private final Set<String> myTransitiveDependencies = Sets.newHashSet();

  @Nullable private PsdArtifactDependencySpec myDeclaredSpec;

  PsdLibraryDependencyModel(@NotNull PsdAndroidModuleModel parent,
                            @NotNull PsdArtifactDependencySpec resolvedSpec,
                            @Nullable Library gradleModel,
                            @Nullable PsdAndroidArtifactModel artifactModel,
                            @Nullable ArtifactDependencyModel parsedModel) {
    super(parent, artifactModel, parsedModel);
    myResolvedSpec = resolvedSpec;
    myGradleModel = gradleModel;
    setDeclaredSpec(parsedModel);
    myMismatchingRequestedSpec = findMismatchingSpec();

    PsdIssue problem = null;
    if (myMismatchingRequestedSpec != null) {
      String requestedVersion = myMismatchingRequestedSpec.version;
      String msg = String.format("Version requested: '%1$s'. Version resolved: '%2$s'", requestedVersion, myResolvedSpec.version);
      problem = new PsdIssue(msg, WARNING);
    }
    myProblem = problem;
  }

  @Nullable
  private PsdArtifactDependencySpec findMismatchingSpec() {
    DependencyModel parsedModel = getParsedModel();
    if (parsedModel instanceof ArtifactDependencyModel) {
      PsdArtifactDependencySpec requestedSpec = PsdArtifactDependencySpec.create((ArtifactDependencyModel)parsedModel);
      if (!requestedSpec.equals(myDeclaredSpec)) {
        // Version mismatch. This can happen when the project specifies an artifact version but Gradle uses a different version
        // from a transitive dependency.
        // Example:
        // 1. Module 'app' depends on module 'lib'
        // 2. Module 'app' depends on Guava 18.0
        // 3. Module 'lib' depends on Guava 19.0
        // Gradle will force module 'app' to use Guava 19.0
        return requestedSpec;
      }
    }
    return null;
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

  @Nullable
  public PsdArtifactDependencySpec getDeclaredSpec() {
    return myDeclaredSpec;
  }

  @NotNull
  public PsdArtifactDependencySpec getResolvedSpec() {
    return myResolvedSpec;
  }

  @Nullable
  public PsdArtifactDependencySpec getMismatchingRequestedSpec() {
    return myMismatchingRequestedSpec;
  }

  @Override
  @NotNull
  public String getName() {
    return myResolvedSpec.name;
  }

  @Override
  @NotNull
  public Icon getIcon() {
    Icon icon = LIBRARY_ICON;
    if (myProblem != null) {
      Type type = myProblem.getType();
      if (type == WARNING) {
        icon = LibraryWarning;
      }
    }
    return icon;
  }

  @Override
  @NotNull
  public String getValueAsText() {
    return myMismatchingRequestedSpec != null ? myMismatchingRequestedSpec.toString() : myResolvedSpec.toString();
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

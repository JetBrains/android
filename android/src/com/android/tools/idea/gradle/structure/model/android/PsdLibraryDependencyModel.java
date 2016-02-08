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
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsdProblem;
import com.android.tools.idea.gradle.structure.model.PsdProblem.Severity;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.structure.model.PsdProblem.Severity.WARNING;
import static com.intellij.util.PlatformIcons.LIBRARY_ICON;
import static icons.AndroidIcons.ProjectStructure.LibraryWarning;

public class PsdLibraryDependencyModel extends PsdAndroidDependencyModel {
  @NotNull private final ArtifactDependencySpec myResolvedSpec;

  @Nullable private final Library myGradleModel;
  @Nullable private final ArtifactDependencySpec myMismatchingRequestedSpec;
  @Nullable private ArtifactDependencyModel myParsedModel;
  @Nullable private final PsdProblem myProblem;

  @NotNull private final Set<String> myTransitiveDependencies = Sets.newHashSet();

  PsdLibraryDependencyModel(@NotNull PsdAndroidModuleModel parent,
                            @NotNull ArtifactDependencySpec resolvedSpec,
                            @Nullable Library gradleModel,
                            @Nullable ArtifactDependencyModel parsedModel) {
    super(parent);
    myResolvedSpec = resolvedSpec;
    myGradleModel = gradleModel;
    myParsedModel = parsedModel;
    myMismatchingRequestedSpec = findMismatchingSpec();

    PsdProblem problem = null;
    if (myMismatchingRequestedSpec != null) {
      String requestedVersion = myMismatchingRequestedSpec.version;
      String msg = String.format("Version requested: '%1$s'. Version resolved: '%2$s'", requestedVersion, myResolvedSpec.version);
      problem = new PsdProblem(msg, WARNING);
    }
    myProblem = problem;
  }

  @Nullable
  private ArtifactDependencySpec findMismatchingSpec() {
    if (myParsedModel != null) {
      ArtifactDependencySpec requestedSpec = myParsedModel.getSpec();
      if (!requestedSpec.equals(myResolvedSpec)) {
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

  @NotNull
  public List<PsdAndroidDependencyModel> getTransitiveDependencies() {
    List<PsdAndroidDependencyModel> transitive = Lists.newArrayList();
    for (String dependency : myTransitiveDependencies) {
      PsdAndroidDependencyModel found = getParent().findDependency(dependency);
      if (found != null) {
        transitive.add(found);
      }
    }
    return transitive;
  }

  @NotNull
  public ArtifactDependencySpec getResolvedSpec() {
    return myResolvedSpec;
  }

  @Nullable
  public ArtifactDependencySpec getMismatchingRequestedSpec() {
    return myMismatchingRequestedSpec;
  }

  @Override
  @NotNull
  public Icon getIcon() {
    Icon icon = LIBRARY_ICON;
    if (myProblem != null) {
      Severity severity = myProblem.getSeverity();
      if (severity == WARNING) {
        icon = LibraryWarning;
      }
    }
    return icon;
  }

  @Override
  @Nullable
  public PsdProblem getProblem() {
    return myProblem;
  }

  @Override
  @Nullable
  public String getConfigurationName() {
    return myParsedModel != null ? myParsedModel.configurationName() : null;
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
    return Objects.equal(myMismatchingRequestedSpec, that.myMismatchingRequestedSpec) && Objects.equal(myResolvedSpec, that.myResolvedSpec);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myResolvedSpec);
  }

  @Override
  public boolean isEditable() {
    return myParsedModel != null;
  }

  @Override
  public String toString() {
    return getValueAsText();
  }
}

/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.prototype.model;

import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.ide.common.repository.GradleCoordinate.parseCoordinateString;
import static com.android.tools.idea.gradle.structure.prototype.model.Coordinates.areEqual;
import static com.android.tools.idea.gradle.structure.prototype.model.Coordinates.convert;
import static com.intellij.util.PlatformIcons.LIBRARY_ICON;

public class ArtifactDependencyMergedModel extends DependencyMergedModel {
  @NotNull private final List<GradleArtifactDependency> myGradleArtifactDependencies;
  @NotNull private final GradleCoordinate myCoordinate;

  @Nullable private final ArtifactDependencyModel myParsedModel;

  @Nullable
  static ArtifactDependencyMergedModel create(@NotNull ModuleMergedModel parent, @NotNull ArtifactDependencyModel parsedModel) {
    GradleCoordinate coordinate = parseCoordinateString(parsedModel.getSpec().compactNotation());
    if (coordinate != null) {
      return new ArtifactDependencyMergedModel(parent, Collections.<GradleArtifactDependency>emptyList(), coordinate, parsedModel);
    }
    return null;
  }

  @NotNull
  static ArtifactDependencyMergedModel create(@NotNull ModuleMergedModel parent,
                                              @NotNull GradleArtifactDependency artifactDependency) {
    GradleCoordinate coordinate = artifactDependency.coordinate;
    return new ArtifactDependencyMergedModel(parent, Collections.singletonList(artifactDependency), coordinate, null);
  }

  @Nullable
  static ArtifactDependencyMergedModel create(@NotNull ModuleMergedModel parent,
                                              @NotNull List<GradleArtifactDependency> artifactDependencies,
                                              @Nullable ArtifactDependencyModel parsedModel) {
    GradleCoordinate coordinate;
    if (parsedModel != null) {
      coordinate = parseCoordinateString(parsedModel.getSpec().compactNotation());
    }
    else {
      if (artifactDependencies.isEmpty()) {
        return null;
      }
      GradleArtifactDependency dependency = artifactDependencies.get(0);
      coordinate = dependency.coordinate;
    }
    if (coordinate != null) {
      return new ArtifactDependencyMergedModel(parent, artifactDependencies, coordinate, parsedModel);
    }
    return null;
  }

  private ArtifactDependencyMergedModel(@NotNull ModuleMergedModel parent,
                                        @NotNull Collection<GradleArtifactDependency> artifactDependencies,
                                        @NotNull GradleCoordinate coordinate,
                                        @Nullable ArtifactDependencyModel parsedModel) {
    super(parent, parsedModel != null ? parsedModel.configurationName() : null);
    myGradleArtifactDependencies = Lists.newArrayList(artifactDependencies);
    myCoordinate = coordinate;
    myParsedModel = parsedModel;
  }

  @Override
  public boolean isInAndroidProject() {
    return !myGradleArtifactDependencies.isEmpty();
  }

  @Override
  public boolean matches(@NotNull Library library) {
    for (GradleArtifactDependency dependency : myGradleArtifactDependencies) {
      if (dependency.dependency == library) {
        return true;
      }
      MavenCoordinates resolved = library.getResolvedCoordinates();
      if (resolved != null && areEqual(myCoordinate, convert(resolved))) {
        return true;
      }
    }
    return false;
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return LIBRARY_ICON;
  }

  @Override
  public boolean isInBuildFile() {
    return myParsedModel != null;
  }

  @NotNull
  public GradleCoordinate getCoordinate() {
    return myCoordinate;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ArtifactDependencyMergedModel that = (ArtifactDependencyMergedModel)o;

    return Objects.equal(myCoordinate.toString(), that.myCoordinate.toString());
  }

  @Override
  public int hashCode() {
    return myCoordinate.toString().hashCode();
  }

  @Override
  public String toString() {
    return myCoordinate.toString();
  }
}

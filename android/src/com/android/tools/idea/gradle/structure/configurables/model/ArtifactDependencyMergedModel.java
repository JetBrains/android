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
package com.android.tools.idea.gradle.structure.configurables.model;

import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.structure.configurables.model.ModuleMergedModel.LogicalArtifactDependency;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.structure.configurables.model.Coordinates.areEqual;
import static com.android.tools.idea.gradle.structure.configurables.model.Coordinates.convert;
import static com.intellij.util.PlatformIcons.LIBRARY_ICON;

public class ArtifactDependencyMergedModel extends DependencyMergedModel {
  @NotNull private final List<LogicalArtifactDependency> myLogicalModels;
  @NotNull private final ArtifactDependencyModel myParsedModel;
  @NotNull private final GradleCoordinate myCoordinate;

  @Nullable
  static ArtifactDependencyMergedModel create(@NotNull ModuleMergedModel parent, @NotNull ArtifactDependencyModel parsedModel) {
    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(parsedModel.getSpec().compactNotation());
    if (coordinate != null) {
      return new ArtifactDependencyMergedModel(parent, Collections.<LogicalArtifactDependency>emptyList(), coordinate, parsedModel);
    }
    return null;
  }

  @Nullable
  static ArtifactDependencyMergedModel create(@NotNull ModuleMergedModel parent,
                                              @NotNull Collection<LogicalArtifactDependency> logicalModels,
                                              @NotNull ArtifactDependencyModel parsedModel) {
    GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(parsedModel.getSpec().compactNotation());
    if (coordinate != null) {
      return new ArtifactDependencyMergedModel(parent, logicalModels, coordinate, parsedModel);
    }
    return null;
  }

  private ArtifactDependencyMergedModel(@NotNull ModuleMergedModel parent,
                                        @NotNull Collection<LogicalArtifactDependency> logicalModels,
                                        @NotNull GradleCoordinate coordinate,
                                        @NotNull ArtifactDependencyModel parsedModel) {
    super(parent, parsedModel.configurationName());
    myLogicalModels = Lists.newArrayList(logicalModels);
    myCoordinate = coordinate;
    myParsedModel = parsedModel;
  }

  @Override
  public boolean isInAndroidProject() {
    return !myLogicalModels.isEmpty();
  }

  public boolean matches(@NotNull Library library) {
    for (LogicalArtifactDependency model : myLogicalModels) {
      if (model.dependency == library) {
        return true;
      }
      MavenCoordinates resolved = library.getResolvedCoordinates();
      if (resolved != null) {
        if (areEqual(myCoordinate, convert(resolved))) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return LIBRARY_ICON;
  }

  @NotNull
  public GradleCoordinate getCoordinate() {
    return myCoordinate;
  }

  @Override
  public String toString() {
    return myParsedModel.getSpec().compactNotation();
  }
}

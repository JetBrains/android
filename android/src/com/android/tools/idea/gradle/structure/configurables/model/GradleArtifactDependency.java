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
package com.android.tools.idea.gradle.structure.configurables.model;

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.Variant;
import com.android.ide.common.repository.GradleCoordinate;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * An artifact dependency obtained from the Gradle model.
 */
class GradleArtifactDependency {
  @NotNull final GradleCoordinate coordinate;
  @NotNull final Library dependency;
  @NotNull final List<Variant> containers = Lists.newArrayList();

  @Nullable
  static GradleArtifactDependency create(@NotNull Library dependency) {
    MavenCoordinates resolved = dependency.getResolvedCoordinates();
    if (resolved != null) {
      if (dependency instanceof AndroidLibrary) {
        AndroidLibrary androidLibrary = (AndroidLibrary)dependency;
        if (isNotEmpty(androidLibrary.getProject())) {
          return null;
        }
      }
      return new GradleArtifactDependency(Coordinates.convert(resolved), dependency);
    }
    return null;
  }

  private GradleArtifactDependency(@NotNull GradleCoordinate coordinate, @NotNull Library dependency) {
    this.coordinate = coordinate;
    this.dependency = dependency;
  }

  void addContainer(@NotNull Variant container) {
    containers.add(container);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GradleArtifactDependency that = (GradleArtifactDependency)o;
    return Objects.equal(coordinate.toString(), that.coordinate.toString());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(coordinate.toString());
  }

  @Override
  public String toString() {
    return coordinate.toString();
  }
}

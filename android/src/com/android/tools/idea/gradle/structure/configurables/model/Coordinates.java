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

import com.android.builder.model.MavenCoordinates;
import com.android.ide.common.repository.GradleCoordinate;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class Coordinates {
  private Coordinates() {
  }

  @NotNull
  public static GradleCoordinate convert(@NotNull MavenCoordinates coordinates) {
    GradleCoordinate.RevisionComponent version = new GradleCoordinate.StringComponent(coordinates.getVersion());
    List<GradleCoordinate.RevisionComponent> components = Lists.newArrayList(version);
    GradleCoordinate.ArtifactType artifactType = GradleCoordinate.ArtifactType.getArtifactType(coordinates.getPackaging());
    return new GradleCoordinate(coordinates.getGroupId(), coordinates.getArtifactId(), components, artifactType);
  }

  public static boolean areEqual(@NotNull GradleCoordinate c1, @NotNull GradleCoordinate c2) {
    return c1.isSameArtifact(c2) && c1.getRevision().equals(c2.getRevision());
  }
}

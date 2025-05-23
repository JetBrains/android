/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.common.repository;

import com.google.idea.blaze.base.model.primitives.Label;

/** Helper class for GoogleMavenArtifactId. */
public final class GoogleMavenArtifactIdHelper {
  public static final GradleCoordinate CONSTRAINT_LAYOUT_COORDINATE =
      GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+");
  public static final GradleCoordinate APP_COMPAT_V7 =
      GoogleMavenArtifactId.SUPPORT_APPCOMPAT_V7.getCoordinate("+");

  public static Label getLabelForGoogleMavenArtifactId(GradleCoordinate coordinate) {
    switch (GoogleMavenArtifactId.find(coordinate.getGroupId(), coordinate.getArtifactId())) {
      case SUPPORT_RECYCLERVIEW_V7:
        return Label.create("//third_party/recyclerview:recyclerview");
      case CONSTRAINT_LAYOUT:
        return Label.create("//third_party/constraint_layout:constraint_layout");
      default:
        return null;
    }
  }

  private GoogleMavenArtifactIdHelper() {}
}

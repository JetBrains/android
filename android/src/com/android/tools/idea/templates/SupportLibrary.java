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
package com.android.tools.idea.templates;

import com.android.ide.common.repository.GradleCoordinate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Known support libraries.
 */
public enum SupportLibrary {
  // Android repo:
  SUPPORT_ANNOTATIONS("com.android.support", "support-annotations"),
  SUPPORT_V4("com.android.support", "support-v4"),
  SUPPORT_V13("com.android.support", "support-v13"),
  APP_COMPAT_V7("com.android.support", "appcompat-v7"),
  SUPPORT_VECTOR_DRAWABLE("com.android.support", "support-vector-drawable"),
  DESIGN("com.android.support", "design"),
  GRID_LAYOUT_V7("com.android.support", "gridlayout-v7"),
  MEDIA_ROUTER_V7("com.android.support", "mediarouter-v7"),
  CARDVIEW_V7("com.android.support", "cardview-v7"),
  PALETTE_V7("com.android.support", "palette-v7"),
  LEANBACK_V17("com.android.support", "leanback-v17"),
  RECYCLERVIEW_V7("com.android.support", "recyclerview-v7"),
  TEST_RUNNER("com.android.support.test", "runner"),
  ESPRESSO_CORE("com.android.support.test.espresso", "espresso-core"),

  // Google repo:
  PLAY_SERVICES("com.google.android.gms", "play-services"),
  PLAY_SERVICES_ADS("com.google.android.gms", "play-services-ads"),
  PLAY_SERVICES_WEARABLE("com.google.android.gms", "play-services-wearable"),
  PLAY_SERVICES_MAPS("com.google.android.gms", "play-services-maps"),
  WEARABLE("com.google.android.support", "wearable"),
  ;

  @Nullable
  public static SupportLibrary forGradleCoordinate(@NotNull GradleCoordinate coordinate) {
    if (coordinate.getGroupId() == null || coordinate.getArtifactId() == null) {
      return null;
    }
    return find(coordinate.getGroupId(), coordinate.getArtifactId());
  }

  @Nullable
  public static SupportLibrary find(@NotNull String groupId, @NotNull String artifactId) {
    for (SupportLibrary library : values()) {
      if (library.getGroupId().equals(groupId) && library.getArtifactId().equals(artifactId)) {
        return library;
      }
    }

    return null;
  }

  private final String myGroupId;
  private final String myArtifactId;

  SupportLibrary(String groupId, String artifactId) {
    myGroupId = groupId;
    myArtifactId = artifactId;
  }

  public String getGroupId() {
    return myGroupId;
  }

  public String getArtifactId() {
    return myArtifactId;
  }

  @NotNull
  public GradleCoordinate getGradleCoordinate(@NotNull String revision) {
    return new GradleCoordinate(myGroupId, myArtifactId, new GradleCoordinate.StringComponent(revision));
  }

  @Override
  public String toString() {
    return myGroupId + ":" + myArtifactId;
  }
}

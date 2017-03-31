/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.model;

import com.android.annotations.Nullable;
import com.android.builder.model.MavenCoordinates;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * Creates a deep copy of {@link MavenCoordinates}.
 *
 * @see IdeAndroidProject
 */
public class IdeMavenCoordinates implements MavenCoordinates, Serializable {
  @NotNull private final GradleVersion myGradleVersion;
  @NotNull private final String myGroupId;
  @NotNull private final String myArtifactId;
  @NotNull private final String myVersion;
  @NotNull private final String myPackaging;
  @Nullable private final String myClassifier;
  @Nullable private final String myVersionlessId;

  public IdeMavenCoordinates(@NotNull MavenCoordinates coordinates, @NotNull GradleVersion gradleVersion) {
    myGradleVersion = gradleVersion;
    myGroupId = coordinates.getGroupId();
    myArtifactId = coordinates.getArtifactId();
    myVersion = coordinates.getVersion();
    myPackaging = coordinates.getPackaging();
    myClassifier = coordinates.getClassifier();

    if (myGradleVersion.isAtLeast(2,3,0)) {
      myVersionlessId = coordinates.getVersionlessId();
    } else {
      myVersionlessId = "";
    }
  }

  @Override
  @NotNull
  public String getGroupId() {
    return myGroupId;
  }

  @Override
  @NotNull
  public String getArtifactId() {
    return myArtifactId;
  }

  @Override
  @NotNull
  public String getVersion() {
    return myVersion;
  }

  @Override
  @NotNull
  public String getPackaging() {
    return myPackaging;
  }

  @Override
  @Nullable
  public String getClassifier() {
    return myClassifier;
  }

  @Override
  @Nullable
  public String getVersionlessId() {
    return myVersionlessId;
  }
}

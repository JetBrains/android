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
package com.android.tools.idea.gradle.stubs.android;

import com.android.builder.model.MavenCoordinates;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MavenCoordinatesStub implements MavenCoordinates {
  @NotNull private final String myGroupId;
  @NotNull private final String myArtifactId;
  @NotNull private final String myVersion;
  @NotNull private final String myPackaging;

  public MavenCoordinatesStub(@NotNull String groupId, @NotNull String artifactId, @NotNull String version, @NotNull String packaging) {
    myGroupId = groupId;
    myArtifactId = artifactId;
    myVersion = version;
    myPackaging = packaging;
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
    return null;
  }

  @Override
  public String getVersionlessId() {
    return null;
  }
}

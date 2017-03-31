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
import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * Creates a deep copy of {@link Library}.
 *
 * @see IdeAndroidProject
 */
public class IdeLibrary implements Library, Serializable {
  @NotNull private final MavenCoordinates myResolvedCoordinates;
  @Nullable private final String myProject;
  @Nullable private final String myName;
  @Nullable private final MavenCoordinates myRequestedCoordinates;
  private final boolean mySkipped;
  private final boolean myProvided;

  public IdeLibrary(@NotNull Library library, @NotNull GradleVersion gradleVersion) {
    myResolvedCoordinates = new IdeMavenCoordinates(library.getResolvedCoordinates(), gradleVersion);

    myProject = library.getProject();
    myName = library.getName();

    MavenCoordinates liRequestedCoordinate = library.getRequestedCoordinates();
    myRequestedCoordinates = liRequestedCoordinate == null ? null :new IdeMavenCoordinates(liRequestedCoordinate, gradleVersion);

    mySkipped = library.isSkipped();
    myProvided = library.isProvided();
  }

  @Override
  @Nullable
  public MavenCoordinates getRequestedCoordinates() {
    return myRequestedCoordinates;
  }

  @Override
  @NotNull
  public MavenCoordinates getResolvedCoordinates() {
    return myResolvedCoordinates;
  }

  @Override
  @Nullable
  public String getProject() {
    return myProject;
  }

  @Override
  @Nullable
  public String getName() {
    return myName;
  }

  @Override
  public boolean isSkipped() {
    return mySkipped;
  }

  @Override
  public boolean isProvided() {
    return myProvided;
  }

}

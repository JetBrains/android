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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.Library;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.android.tools.idea.gradle.project.model.ide.android.IdeLibraries.computeResolvedCoordinate;

/**
 * Creates a deep copy of a {@link Library}.
 */
public abstract class IdeLibrary extends IdeModel implements Library {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 2L;

  @NotNull private final IdeMavenCoordinates myResolvedCoordinates;
  @Nullable private final String myProject;
  @Nullable private final String myName;
  @Nullable private final Boolean myProvided;
  private final int myHashCode;

  protected IdeLibrary(@NotNull Library library, @NotNull ModelCache modelCache) {
    super(library, modelCache);
    myResolvedCoordinates = computeResolvedCoordinate(library, modelCache);
    myProject = copyNewProperty(library::getProject, null);
    myName = copyNewProperty(library::getName, null); // Library.getName() was added in 2.2
    myProvided = copyNewProperty(library::isProvided, null);

    myHashCode = calculateHashCode();
  }

  @Override
  @Nullable
  public IdeMavenCoordinates getRequestedCoordinates() {
    throw new UnusedModelMethodException("getRequestedCoordinates");
  }

  @Override
  @NotNull
  public IdeMavenCoordinates getResolvedCoordinates() {
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
    throw new UnusedModelMethodException("isSkipped");
  }

  @Override
  public boolean isProvided() {
    if (myProvided != null) {
      return myProvided;
    }
    throw new UnsupportedMethodException("Unsupported method: AndroidLibrary.isProvided()");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeLibrary)) {
      return false;
    }
    IdeLibrary library = (IdeLibrary)o;
    return library.canEqual(this) &&
           Objects.equals(myProvided, library.myProvided) &&
           Objects.equals(myResolvedCoordinates, library.myResolvedCoordinates) &&
           Objects.equals(myProject, library.myProject) &&
           Objects.equals(myName, library.myName);
  }

  public boolean canEqual(Object other) {
    // See: http://www.artima.com/lejava/articles/equality.html
    return other instanceof IdeLibrary;
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  protected int calculateHashCode() {
    return Objects.hash(myResolvedCoordinates, myProject, myName, myProvided);
  }

  @Override
  public String toString() {
    return "myResolvedCoordinates=" + myResolvedCoordinates +
           ", myProject='" + myProject + '\'' +
           ", myName='" + myName + '\'' +
           ", myProvided=" + myProvided;
  }
}

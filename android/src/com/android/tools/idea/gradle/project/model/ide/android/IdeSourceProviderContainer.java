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

import com.android.builder.model.SourceProviderContainer;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Creates a deep copy of a {@link SourceProviderContainer}.
 */
public final class IdeSourceProviderContainer extends IdeModel implements SourceProviderContainer {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final String myArtifactName;
  @NotNull private final IdeSourceProvider mySourceProvider;
  private final int myHashCode;

  public IdeSourceProviderContainer(@NotNull SourceProviderContainer container, @NotNull ModelCache modelCache) {
    super(container, modelCache);
    myArtifactName = container.getArtifactName();
    mySourceProvider = modelCache.computeIfAbsent(container.getSourceProvider(), provider -> new IdeSourceProvider(provider, modelCache));

    myHashCode = calculateHashCode();
  }

  @Override
  @NotNull
  public String getArtifactName() {
    return myArtifactName;
  }

  @Override
  @NotNull
  public IdeSourceProvider getSourceProvider() {
    return mySourceProvider;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeSourceProviderContainer)) {
      return false;
    }
    IdeSourceProviderContainer container = (IdeSourceProviderContainer)o;
    return Objects.equals(myArtifactName, container.myArtifactName) &&
           Objects.equals(mySourceProvider, container.mySourceProvider);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myArtifactName, mySourceProvider);
  }

  @Override
  public String toString() {
    return "IdeSourceProviderContainer{" +
           "myArtifactName='" + myArtifactName + '\'' +
           ", mySourceProvider=" + mySourceProvider +
           '}';
  }
}

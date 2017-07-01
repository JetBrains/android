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

import com.android.builder.model.JavaArtifact;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.ide.android.level2.IdeDependenciesFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;

/**
 * Creates a deep copy of a {@link JavaArtifact}.
 */
public final class IdeJavaArtifact extends IdeBaseArtifactImpl implements JavaArtifact {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @Nullable private final File myMockablePlatformJar;
  private final int myHashCode;

  public IdeJavaArtifact(@NotNull JavaArtifact artifact,
                         @NotNull ModelCache seen,
                         @NotNull IdeDependenciesFactory dependenciesFactory,
                         @Nullable GradleVersion gradleVersion) {
    super(artifact, seen, dependenciesFactory, gradleVersion);
    myMockablePlatformJar = copyNewProperty(artifact::getMockablePlatformJar, null);

    myHashCode = calculateHashCode();
  }

  @Override
  @Nullable
  public File getMockablePlatformJar() {
    return myMockablePlatformJar;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeJavaArtifact)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    IdeJavaArtifact artifact = (IdeJavaArtifact)o;
    return artifact.canEquals(this) &&
           Objects.equals(myMockablePlatformJar, artifact.myMockablePlatformJar);
  }

  @Override
  protected boolean canEquals(Object other) {
    return other instanceof IdeJavaArtifact;
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  @Override
  protected int calculateHashCode() {
    return Objects.hash(super.calculateHashCode(), myMockablePlatformJar);
  }

  @Override
  public String toString() {
    return "IdeJavaArtifact{" +
           super.toString() +
           ", myMockablePlatformJar=" + myMockablePlatformJar +
           "}";
  }
}

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
package com.android.tools.idea.gradle.project.model.ide.android.stubs;

import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class SourceProviderContainerStub extends BaseStub implements SourceProviderContainer {
  @NotNull private final String myArtifactName;
  @NotNull private final SourceProvider mySourceProvider;

  public SourceProviderContainerStub() {
    this("name", new SourceProviderStub());
  }

  public SourceProviderContainerStub(@NotNull String name, @NotNull SourceProvider sourceProvider) {
    myArtifactName = name;
    mySourceProvider = sourceProvider;
  }

  @Override
  @NotNull
  public String getArtifactName() {
    return myArtifactName;
  }

  @Override
  @NotNull
  public SourceProvider getSourceProvider() {
    return mySourceProvider;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SourceProviderContainer)) {
      return false;
    }
    SourceProviderContainer stub = (SourceProviderContainer)o;
    return Objects.equals(getArtifactName(), stub.getArtifactName()) &&
           Objects.equals(getSourceProvider(), stub.getSourceProvider());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getArtifactName(), getSourceProvider());
  }

  @Override
  public String toString() {
    return "SourceProviderContainerStub{" +
           "myArtifactName='" + myArtifactName + '\'' +
           ", mySourceProvider=" + mySourceProvider +
           "}";
  }
}

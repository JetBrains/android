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
package com.android.tools.idea.model.ide;

import com.android.builder.model.BuildType;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Creates a deep copy of {@link BuildTypeContainer}.
 *
 * @see IdeAndroidProject
 */
final public class IdeBuildTypeContainer implements BuildTypeContainer, Serializable {
  @NotNull private final BuildType myBuildType;
  @NotNull private final SourceProvider mySourceProvider;
  @NotNull private final Collection<SourceProviderContainer> myExtraSourceProviders;

  public IdeBuildTypeContainer(@NotNull BuildTypeContainer container) {
    myBuildType = new IdeBuildType(container.getBuildType());
    mySourceProvider = new IdeSourceProvider(container.getSourceProvider());

    myExtraSourceProviders = new ArrayList<>();
    for (SourceProviderContainer sourceProviderContainer : container.getExtraSourceProviders()) {
      myExtraSourceProviders.add(new IdeSourceProviderContainer(sourceProviderContainer));
    }
  }

  @Override
  @NotNull
  public BuildType getBuildType() {
    return myBuildType;
  }

  @Override
  @NotNull
  public SourceProvider getSourceProvider() {
    return mySourceProvider;
  }

  @Override
  @NotNull
  public Collection<SourceProviderContainer> getExtraSourceProviders() {
    return myExtraSourceProviders;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BuildTypeContainer)) return false;
    BuildTypeContainer container = (BuildTypeContainer)o;
    return Objects.equals(getBuildType(), container.getBuildType()) &&
           Objects.equals(getSourceProvider(), container.getSourceProvider()) &&

           getExtraSourceProviders().containsAll(container.getExtraSourceProviders()) &&
           container.getExtraSourceProviders().containsAll(getExtraSourceProviders());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getBuildType(), getSourceProvider(), getExtraSourceProviders());
  }
}

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
package com.android.tools.idea.gradle.structure.model.android;

import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyModel;
import com.android.tools.idea.gradle.structure.model.PsdChildModel;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public abstract class PsdAndroidDependencyModel extends PsdChildModel {
  @NotNull private final Set<String> myVariants = Sets.newHashSet();
  @NotNull private final Set<Container> myContainers = Sets.newHashSet();

  @Nullable private DependencyModel myParsedModel;

  PsdAndroidDependencyModel(@NotNull PsdAndroidModuleModel parent,
                            @Nullable PsdAndroidArtifactModel artifactModel,
                            @Nullable DependencyModel parsedModel) {
    super(parent);
    myParsedModel = parsedModel;
    if (artifactModel != null) {
      addContainer(artifactModel);
    }
  }

  @Override
  @NotNull
  public PsdAndroidModuleModel getParent() {
    return (PsdAndroidModuleModel)super.getParent();
  }

  void addContainer(@NotNull PsdAndroidArtifactModel artifactModel) {
    myContainers.add(new Container(artifactModel));
    myVariants.add(artifactModel.getName());
  }

  @NotNull
  public List<String> getVariants() {
    return ImmutableList.copyOf(myVariants);
  }

  @NotNull
  public Set<Container> getContainers() {
    return myContainers;
  }

  @Nullable
  public String getConfigurationName() {
    return myParsedModel != null ? myParsedModel.configurationName() : null;
  }

  @Override
  public boolean isEditable() {
    return myParsedModel != null;
  }

  @Nullable
  protected DependencyModel getParsedModel() {
    return myParsedModel;
  }

  @NotNull
  public abstract String getValueAsText();

  public static class Container {
    @NotNull public final String variant;
    @NotNull public final String artifact;

    Container(@NotNull PsdAndroidArtifactModel artifactModel) {
      variant = artifactModel.getParent().getName();
      artifact = artifactModel.getGradleModel().getName();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Container container = (Container)o;
      return Objects.equal(variant, container.variant) &&
             Objects.equal(artifact, container.artifact);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(variant, artifact);
    }

    @Override
    public String toString() {
      return variant + " - " + artifact;
    }
  }
}

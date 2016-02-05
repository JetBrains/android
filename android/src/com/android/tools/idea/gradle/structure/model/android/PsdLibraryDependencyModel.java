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

import com.android.builder.model.Library;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Set;

import static com.intellij.util.PlatformIcons.LIBRARY_ICON;

public class PsdLibraryDependencyModel extends PsdAndroidDependencyModel {
  @NotNull private final ArtifactDependencySpec mySpec;

  @Nullable private final Library myGradleModel;
  @Nullable private ArtifactDependencyModel myParsedModel;

  @NotNull private final Set<String> myTransitiveDependencies = Sets.newHashSet();

  PsdLibraryDependencyModel(@NotNull PsdAndroidModuleModel parent,
                            @NotNull ArtifactDependencySpec spec,
                            @Nullable Library gradleModel,
                            @Nullable ArtifactDependencyModel parsedModel) {
    super(parent);
    mySpec = spec;
    myGradleModel = gradleModel;
    myParsedModel = parsedModel;
  }

  void addTransitiveDependency(@NotNull String dependency) {
    myTransitiveDependencies.add(dependency);
  }

  @NotNull
  public List<PsdAndroidDependencyModel> getTransitiveDependencies() {
    List<PsdAndroidDependencyModel> transitive = Lists.newArrayList();
    for (String dependency : myTransitiveDependencies) {
      PsdAndroidDependencyModel found = getParent().findDependency(dependency);
      if (found != null) {
        transitive.add(found);
      }
    }
    return transitive;
  }

  @NotNull
  public ArtifactDependencySpec getSpec() {
    return mySpec;
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return LIBRARY_ICON;
  }

  @Override
  @Nullable
  public String getConfigurationName() {
    return myParsedModel != null ? myParsedModel.configurationName() : null;
  }

  @Override
  @NotNull
  public String getValueAsText() {
    return mySpec.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PsdLibraryDependencyModel that = (PsdLibraryDependencyModel)o;
    return Objects.equal(mySpec, that.mySpec);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(mySpec);
  }

  @Override
  public boolean isEditable() {
    return myParsedModel != null;
  }

  @Override
  public String toString() {
    return getValueAsText();
  }
}

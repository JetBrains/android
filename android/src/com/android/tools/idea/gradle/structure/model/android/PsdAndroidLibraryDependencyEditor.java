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

import com.android.builder.model.AndroidLibrary;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsdChildEditor;
import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class PsdAndroidLibraryDependencyEditor extends PsdAndroidDependencyEditor {
  @NotNull private final ArtifactDependencySpec mySpec;

  @Nullable private final AndroidLibrary myGradleModel;
  @Nullable private final ArtifactDependencyModel myParsedModel;

  PsdAndroidLibraryDependencyEditor(@NotNull PsdAndroidModuleEditor parent,
                                    @NotNull ArtifactDependencySpec spec,
                                    @Nullable AndroidLibrary gradleModel,
                                    @Nullable ArtifactDependencyModel parsedModel) {
    super(parent);
    mySpec = spec;
    myGradleModel = gradleModel;
    myParsedModel = parsedModel;
  }

  @NotNull
  public ArtifactDependencySpec getSpec() {
    return mySpec;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PsdAndroidLibraryDependencyEditor that = (PsdAndroidLibraryDependencyEditor)o;
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
    return mySpec.toString();
  }
}

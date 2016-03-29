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
package com.android.tools.idea.gradle.structure.navigation;

import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryDependency;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

public class PsLibraryDependencyPath extends PsNavigationPath {
  @NotNull private final String myModuleName;
  @NotNull private final String myCompactNotation;

  public PsLibraryDependencyPath(@NotNull PsLibraryDependency dependency) {
    myModuleName = dependency.getParent().getName();
    PsArtifactDependencySpec spec = dependency.getDeclaredSpec();
    if (spec == null) {
      spec = dependency.getResolvedSpec();
    }
    myCompactNotation = spec.compactNotation();
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @NotNull
  public String getCompactNotation() {
    return myCompactNotation;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PsLibraryDependencyPath that = (PsLibraryDependencyPath)o;
    return Objects.equal(myModuleName, that.myModuleName) && Objects.equal(myCompactNotation, that.myCompactNotation);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myModuleName, myCompactNotation);
  }

  @Override
  public String toString() {
    return myModuleName + "/" + getCompactNotation();
  }
}

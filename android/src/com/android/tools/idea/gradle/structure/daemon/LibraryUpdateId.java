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
package com.android.tools.idea.gradle.structure.daemon;

import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.repositories.search.FoundArtifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;

class LibraryUpdateId {
  @NotNull private final String myName;

  @Nullable private final String myGroupId;

  LibraryUpdateId(@NotNull PsArtifactDependencySpec spec) {
    myName = spec.name;
    myGroupId = spec.group;
  }

  LibraryUpdateId(@NotNull FoundArtifact artifact) {
    myName = artifact.getName();
    myGroupId = artifact.getGroupId();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LibraryUpdateId that = (LibraryUpdateId)o;
    return Objects.equals(myName, that.myName) && Objects.equals(myGroupId, that.myGroupId);
  }

  @NotNull
  String getName() {
    return myName;
  }

  @Nullable
  String getGroupId() {
    return myGroupId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, myGroupId);
  }

  @Override
  public String toString() {
    return myGroupId != null ? myGroupId + GRADLE_PATH_SEPARATOR + myName : myName;
  }
}

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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.repositories.search.FoundArtifact;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class AvailableLibraryUpdates {
  @NotNull private final Map<LibraryUpdateId, FoundArtifact> myArtifactsById = Maps.newHashMap();

  void add(@NotNull FoundArtifact artifact) {
    myArtifactsById.put(new LibraryUpdateId(artifact), artifact);
  }

  void clear() {
    myArtifactsById.clear();
  }

  public int size() {
    return myArtifactsById.size();
  }

  @Nullable
  public FoundArtifact findUpdateFor(@NotNull PsArtifactDependencySpec spec) {
    String version = spec.version;
    if (isNotEmpty(version)) {
      GradleVersion parsedVersion = GradleVersion.tryParse(spec.version);
      if (parsedVersion != null) {
        LibraryUpdateId id = new LibraryUpdateId(spec);
        FoundArtifact foundArtifact = myArtifactsById.get(id);
        if (foundArtifact != null) {
          GradleVersion foundVersion = foundArtifact.getVersions().get(0);
          if (foundVersion.compareTo(parsedVersion) > 0) {
            return foundArtifact;
          }
        }
      }
    }
    return null;
  }
}

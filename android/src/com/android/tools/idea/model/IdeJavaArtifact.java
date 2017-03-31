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
package com.android.tools.idea.model;

import com.android.annotations.Nullable;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.Library;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

/**
 * Creates a deep copy of {@link JavaArtifact}.
 *
 * @see IdeAndroidProject
 */
public class IdeJavaArtifact extends IdeBaseArtifact implements JavaArtifact, Serializable {
  @Nullable private final File myMockablePlatformJar;

  public IdeJavaArtifact(@NotNull JavaArtifact artifact, @NotNull Map<Library, Library> seen, @NotNull GradleVersion gradleVersion) {
    super(artifact, seen, gradleVersion);
    myMockablePlatformJar = artifact.getMockablePlatformJar();
  }

  @Override
  @Nullable
  public File getMockablePlatformJar() {
    return myMockablePlatformJar;
  }
}

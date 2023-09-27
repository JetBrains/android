/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.gradle.model.artifacts;

import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A model that downloads Sources, samples and Javadoc for given MavenCoordinates, and returns the list of
 * resolved artifacts.
 */
public interface AdditionalClassifierArtifactsModel {
  /**
   * Returns the list of resolved artifacts.
   */
  @NotNull
  Collection<AdditionalClassifierArtifacts> getArtifacts();

  /**
   * Returns the error message if exception happened during download. Null if download is
   * successful.
   */
  @Nullable
  String getErrorMessage();
}
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
package com.android.ide.gradle.model;

import java.util.Collection;
import org.jetbrains.annotations.NotNull;

/**
 * The parameter for [com.android.ide.gradle.model.artifacts.builder.AdditionalArtifactsModelBuilder]
 */
public interface AdditionalClassifierArtifactsModelParameter {
  /**
   * Return a list of component ids to download sources and javadoc for.
   *
   * @return the list of components to download sources and javadoc for.
   */
  @NotNull
  Collection<ArtifactIdentifier> getArtifactIdentifiers();

  /**
   * Sets the list of component ids to download sources and javadoc for.
   *
   * @param ids the list of component ids to download sources and javadoc for.
   */
  void setArtifactIdentifiers(@NotNull Collection<ArtifactIdentifier> ids);
}


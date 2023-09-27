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

import com.android.ide.gradle.model.ArtifactIdentifier;
import java.io.File;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The information for Sources and Javadoc of a Library.
 */
public interface AdditionalClassifierArtifacts {
  /**
   * Returns a unique identifier.
   */
  @NotNull
  ArtifactIdentifier getId();

  /**
   * Returns the locations of downloaded Sources file, empty list if Sources are unavailable.
   */
  @NotNull
  List<File> getSources();

  /**
   * Returns the location of downloaded Javadoc file, null if Javadoc is not available.
   */
  @Nullable
  File getJavadoc();


  /**
   * Returns the location of downloaded pom file, null if pom file is not available.
   */
  @Nullable
  File getMavenPom();
}
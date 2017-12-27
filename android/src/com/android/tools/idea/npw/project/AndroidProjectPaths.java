/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.npw.project;

import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Paths which are useful when instantiating Android components.
 * E.g., New Activity, Fragment, Module, Project.
 */
public interface AndroidProjectPaths {
  @Nullable
  File getModuleRoot();

  /**
   * @param packageName package name of the new component. May affect resulting source directory (e.g., appended to source root).
   *                    For "com.google.foo.Bar", this would be "com.google.foo", and the resulting source directory *might* be
   *                    "src/main/java/com/google/foo/".
   *                    null if no transformation is required on the source root.
   * @return the target directory in which to place a new Android component.
   */
  @Nullable
  File getSrcDirectory(@Nullable String packageName);

  /**
   * Similar to {@link AndroidProjectPaths#getSrcDirectory(String)}, except for new tests.
   */
  @Nullable
  File getTestDirectory(@Nullable String packageName);

  @Nullable
  File getResDirectory();

  /**
   * Similar to {@link AndroidProjectPaths#getSrcDirectory(String)}, except for new aidl files.
   */
  @Nullable
  File getAidlDirectory(@Nullable String packageName);

  @Nullable
  File getManifestDirectory();
}

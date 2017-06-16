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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.Dependencies;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.Library;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * The unified dependency interface that should be used in IDE.
 * Both of {@link DependencyGraphs} in 3.0+ models and {@link Dependencies} in pre-3.0 models
 * should be converted to instance implementing this new interface.
 */
public interface IdeLevel2Dependencies {
  /**
   * The list of Android dependencies.
   *
   * This is a flat list that contains direct and transitive dependencies.
   *
   * @return the list of libraries of type LIBRARY_ANDROID.
   */
  @NotNull
  Collection<Library> getAndroidLibraries();

  /**
   * The list of Java dependencies.
   *
   * This is a flat list that contains direct and transitive dependencies.
   *
   * @return the list of libraries of type LIBRARY_JAVA.
   */
  @NotNull
  Collection<Library> getJavaLibraries();

  /**
   * The list of Module dependencies.
   *
   * This is a flat list that contains module dependencies.
   *
   * @return the list of libraries of type LIBRARY_MODULE.
   */
  @NotNull
  Collection<Library> getModuleDependencies();
}

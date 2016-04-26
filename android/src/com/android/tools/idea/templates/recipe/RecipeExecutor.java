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
package com.android.tools.idea.templates.recipe;

import com.android.tools.idea.templates.FreemarkerUtils.TemplateProcessingException;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Execution engine for the instructions in a Recipe.
 */
public interface RecipeExecutor {

  /**
   * Copies the given source file into the given destination file (where the
   * source is allowed to be a directory, in which case the whole directory is
   * copied recursively)
   */
  void copy(@NotNull File from, @NotNull File to);

  /**
   * Instantiates the given template file into the given output file (running the freemarker
   * engine over it)
   */
  void instantiate(@NotNull File from, @NotNull File to) throws TemplateProcessingException;

  /**
   * Merges the given source file into the given destination file (or it just copies it over if
   * the destination file does not exist).
   * <p/>
   * Only XML and Gradle files are currently supported.
   */
  void merge(@NotNull File from, @NotNull File to) throws TemplateProcessingException;

  /**
   * Create a directory at the specified location (if not already present). This will also create
   * any parent directories that don't exist, as well.
   */
  void mkDir(@NotNull File at);

  /**
   * Record that this file should be opened.
   */
  void addFilesToOpen(@NotNull File file);

  /**
   * Adds "apply plugin: '{@code plugin}'" statement to the module build.gradle file.
   */
  void applyPlugin(@NotNull String plugin);

  /**
   * Record a classpath dependency.
   */
  void addClasspath(@NotNull String mavenUrl);

  /**
   * Record a library dependency.
   */
  void addDependency(@NotNull String mavenUrl);

  /**
   * Update the project's gradle build file and sync, if necessary. This should only be called
   * once and after all dependencies are already added.
   */
  void updateAndSyncGradle();

  /**
   * Set the current folder that relative paths will be resolved against.
   */
  void pushFolder(@NotNull String folder);

  /**
   * Restore the previous folder that relative paths will be resolved against.
   */
  void popFolder();

  /**
   * Append contents of the first file to the second one.
   */
  void append(@NotNull File from, @NotNull File to);
}

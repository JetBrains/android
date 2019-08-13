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
package com.android.tools.idea.templates.recipe

import com.android.tools.idea.templates.FreemarkerUtils.TemplateProcessingException

import java.io.File

/**
 * Execution engine for the instructions in a Recipe.
 */
interface RecipeExecutor {

  /**
   * Copies the given source file into the given destination file (where the
   * source is allowed to be a directory, in which case the whole directory is
   * copied recursively)
   */
  fun copy(from: File, to: File)

  /**
   * Instantiates the given template file into the given output file (running the freemarker
   * engine over it)
   */
  @Throws(TemplateProcessingException::class)
  fun instantiate(from: File, to: File)

  /**
   * Merges the given source file into the given destination file (or it just copies it over if
   * the destination file does not exist).
   *
   *
   * Only XML and Gradle files are currently supported.
   */
  @Throws(TemplateProcessingException::class)
  fun merge(from: File, to: File)

  /**
   * Create a directory at the specified location (if not already present). This will also create
   * any parent directories that don't exist, as well.
   */
  fun mkDir(at: File)

  /**
   * Record that this file should be opened.
   */
  fun addFilesToOpen(file: File)

  /**
   * Adds "apply plugin: '`plugin`'" statement to the module build.gradle file.
   */
  fun applyPlugin(plugin: String)

  /**
   * Record a classpath dependency.
   */
  fun addClasspath(mavenUrl: String)

  /**
   * Record a library dependency.
   */
  fun addDependency(configuration: String, mavenUrl: String)

  /**
   * Update the project's gradle build file and sync, if necessary. This should only be called
   * once and after all dependencies are already added.
   */
  fun updateAndSync()

  /**
   * Set the current folder that relative paths will be resolved against.
   */
  fun pushFolder(folder: String)

  /**
   * Restore the previous folder that relative paths will be resolved against.
   */
  fun popFolder()

  /**
   * Add a variable that can be referenced while the template is being rendered.
   */
  fun addGlobalVariable(id: String, value: Any)

  /**
   * Add source directory or file (if [type] is a manifest).
   */
  fun addSourceSet(type: String, name: String, dir: String)

  /**
   * Set variable in ext block of global build.gradle.
   */
  fun setExtVar(name: String, value: String)
}

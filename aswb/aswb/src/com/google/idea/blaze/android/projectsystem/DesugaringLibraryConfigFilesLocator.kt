/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.projectsystem

import com.google.idea.blaze.base.settings.BuildSystemName
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.nio.file.Path

/**
 * Build systems can implement their own [DesugaringLibraryConfigFilesLocator] to help the IDE
 * to locate desugaring config files. Note that there can be multiple locators enabled at the same
 * time; see [DesugaringLibraryConfigFilesLocator.forBuildSystem] on how to
 * obtain them for a given build system.
 */
interface DesugaringLibraryConfigFilesLocator {
  /** Returns true if desugaring library config files exist.  */
  fun getDesugarLibraryConfigFilesKnown(): Boolean

  /** Returns the list of paths to the desugaring library config files  */
  fun getDesugarLibraryConfigFiles(project: Project): List<Path>

  /**
   * Returns the [BuildSystemName] this [DesugaringLibraryConfigFilesLocator] supports.
   */
  fun buildSystem(): BuildSystemName

  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<DesugaringLibraryConfigFilesLocator>("com.google.idea.blaze.DesugaringLibraryConfigFilesLocator")

    /**
     * Returns a [List] of [DesugaringLibraryConfigFilesLocator] that supports
     * the given build system.
     */
    @JvmStatic
    fun forBuildSystem(buildSystemName: BuildSystemName): List<DesugaringLibraryConfigFilesLocator> {
      return EP_NAME.extensionList
        .filter { it.buildSystem() == buildSystemName }
    }
  }
}

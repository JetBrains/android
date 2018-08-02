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
package com.android.tools.idea.gradle.structure.configurables

import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.daemon.PsAnalyzerDaemon
import com.android.tools.idea.gradle.structure.daemon.PsLibraryUpdateCheckerDaemon
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepositorySearchService
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable
import com.intellij.openapi.Disposable

interface PsContext : Disposable {
  val analyzerDaemon: PsAnalyzerDaemon
  val project: PsProject
  val libraryUpdateCheckerDaemon: PsLibraryUpdateCheckerDaemon
  val uiSettings: PsUISettings
  val selectedModule: String?
  val mainConfigurable: ProjectStructureConfigurable
  /**
   * Gets an [ArtifactRepositorySearchService] that searches the repositories configured for `module`. The results are cached and
   * in the case of an exactly matching request reused.
   */
  fun getArtifactRepositorySearchServiceFor(module: PsModule): ArtifactRepositorySearchService

  fun setSelectedModule(moduleName: String, source: Any)
  fun add(listener: GradleSyncListener, parentDisposable: Disposable)
  fun applyRunAndReparse(runnable: () -> Boolean)
}


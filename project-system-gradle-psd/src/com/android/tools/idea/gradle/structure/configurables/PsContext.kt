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

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.daemon.PsAnalyzerDaemon
import com.android.tools.idea.gradle.structure.daemon.PsLibraryUpdateCheckerDaemon
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.repositories.search.ArtifactRepositorySearchService
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import java.util.EventListener

interface PsContext : Disposable {
  interface SyncListener : EventListener {
    fun started()
    fun ended()
  }

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

  fun setSelectedModule(gradlePath: String, source: Any)
  fun add(listener: SyncListener, parentDisposable: Disposable)
  fun applyRunAndReparse(runnable: () -> Boolean)

  /**
   * Validates and applies changes to the [project].
   */
  @Throws(ConfigurationException::class)
  fun applyChanges()

  /**
   * Records [fieldId] for inclusion in the modified field list of the [PSDEvent] reported to [UsageTracker]. This method is supposed to be
   * called from the UI elements in response to the user's actions
   */
  fun logFieldEdited(fieldId: PSDEvent.PSDField)

  /**
   * Returns a copy of the list of the field ids recorded by [logFieldEdited] and clears the list.
   */
  fun getEditedFieldsAndClear(): List<PSDEvent.PSDField>
}

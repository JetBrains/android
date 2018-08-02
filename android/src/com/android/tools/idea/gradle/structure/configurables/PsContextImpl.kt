/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.idea.gradle.structure.GradleResolver
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.continueOnEdt
import com.android.tools.idea.gradle.structure.configurables.ui.handleFailureOnEdt
import com.android.tools.idea.gradle.structure.daemon.PsAnalyzerDaemon
import com.android.tools.idea.gradle.structure.daemon.PsLibraryUpdateCheckerDaemon
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepositorySearch
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepositorySearchResults
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepositorySearchService
import com.android.tools.idea.gradle.structure.model.repositories.search.SearchRequest
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.EventDispatcher
import com.intellij.util.ExceptionUtil
import java.util.function.Consumer
import javax.annotation.concurrent.GuardedBy

class PsContextImpl constructor(
  override val project: PsProjectImpl,
  parentDisposable: Disposable,
  disableAnalysis: Boolean = false
) : PsContext, Disposable {
  private val lock = Any()
  override val analyzerDaemon: PsAnalyzerDaemon
  private val gradleSync: GradleResolver = GradleResolver()
  override val libraryUpdateCheckerDaemon: PsLibraryUpdateCheckerDaemon

  private val gradleSyncEventDispatcher = EventDispatcher.create(
    GradleSyncListener::class.java)

  override var selectedModule: String? = null ; private set

  @GuardedBy("lock")
  private val artifactRepositorySearchServices = mutableMapOf<PsModule, ArtifactRepositorySearchService>()

  override val uiSettings: PsUISettings
    get() = PsUISettings.getInstance(project.ideProject)

  override val mainConfigurable: ProjectStructureConfigurable
    get() = ProjectStructureConfigurable.getInstance(project.ideProject)

  init {
    mainConfigurable.add(
      ProjectStructureConfigurable.ProjectStructureChangeListener { this.requestGradleModels() }, this)
    // The UI has not yet subscribed to notifications which is fine since we don't want to see "Loading..." at startup.
    requestGradleModels()

    libraryUpdateCheckerDaemon = PsLibraryUpdateCheckerDaemon(this)
    if (!disableAnalysis) {
      libraryUpdateCheckerDaemon.reset()
      libraryUpdateCheckerDaemon.queueAutomaticUpdateCheck()
    }

    analyzerDaemon = PsAnalyzerDaemon(this, libraryUpdateCheckerDaemon)
    if (!disableAnalysis) {
      analyzerDaemon.reset()
      project.forEachModule(Consumer { analyzerDaemon.queueCheck(it) })
    }

    Disposer.register(parentDisposable, this)
  }

  private fun requestGradleModels() {
    val project = this.project.ideProject
    gradleSyncEventDispatcher.multicaster.syncStarted(project, false, false)
    gradleSync
      .requestProjectResolved(project, this)
      .handleFailureOnEdt {
        gradleSyncEventDispatcher.multicaster.syncFailed(project, it?.let { ExceptionUtil.getRootCause(it).message }.orEmpty())
      }
      .continueOnEdt {
        this.project.refreshFrom(it)
        gradleSyncEventDispatcher.multicaster.syncSucceeded(project)
      }
  }

  override fun add(listener: GradleSyncListener, parentDisposable: Disposable) =
    gradleSyncEventDispatcher.addListener(listener, parentDisposable)


  override fun setSelectedModule(moduleName: String, source: Any) {
    selectedModule = moduleName
  }

  override fun dispose() {}

  /**
   * Gets a [ArtifactRepositorySearchService] that searches the repositories configured for `module`. The results are cached and
   * in the case of an exactly matching request reused.
   */
  override fun getArtifactRepositorySearchServiceFor(module: PsModule): ArtifactRepositorySearchService =
    synchronized(lock) {
      artifactRepositorySearchServices
        .getOrPut(module, { CachingArtifactRepositorySearch(
          ArtifactRepositorySearch(module.getArtifactRepositories())) })
    }


  private class CachingArtifactRepositorySearch(
    private val artifactRepositorySearch: ArtifactRepositorySearchService
  ) : ArtifactRepositorySearchService {
    private val lock = Any()

    @GuardedBy("lock")
    private val requestCache = mutableMapOf<SearchRequest, ListenableFuture<ArtifactRepositorySearchResults>>()

    override fun search(request: SearchRequest): ListenableFuture<ArtifactRepositorySearchResults> =
      synchronized(lock) {
        requestCache[request]?.takeUnless { it.isCancelled }
        ?: artifactRepositorySearch.search(request).also { requestCache[request] = it }
      }

  }
}

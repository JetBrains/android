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
package com.android.tools.idea.gradle.structure.daemon

import com.android.annotations.concurrency.UiThread
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.RepositorySearchFactory
import com.android.tools.idea.gradle.structure.daemon.AvailableLibraryUpdateStorage.AvailableLibraryUpdates
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository
import com.android.tools.idea.gradle.structure.model.repositories.search.SearchQuery
import com.android.tools.idea.gradle.structure.model.repositories.search.SearchRequest
import com.android.tools.idea.gradle.structure.model.repositories.search.getResultSafely
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil.isNotEmpty
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.MergingUpdateQueue.ANY_COMPONENT
import com.intellij.util.ui.update.Update
import java.util.EventListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

private val LOG = Logger.getInstance(PsLibraryUpdateCheckerDaemon::class.java)

class PsLibraryUpdateCheckerDaemon(
  parentDisposable: Disposable,
  private val project: PsProject,
  private val repositorySearchFactory: RepositorySearchFactory
) : PsDaemon(parentDisposable) {
  override val mainQueue: MergingUpdateQueue = createQueue("Project Structure Daemon Update Checker", null)
  override val resultsUpdaterQueue: MergingUpdateQueue = createQueue("Project Structure Available Update Results Updater", ANY_COMPONENT)

  private val eventDispatcher = EventDispatcher.create(AvailableUpdatesListener::class.java)

  fun getAvailableUpdates(): AvailableLibraryUpdates = AvailableLibraryUpdateStorage.getInstance(project.ideProject).getState()

  fun queueAutomaticUpdateCheck() {
    val searchTimeMillis = getAvailableUpdates().lastSearchTimeMillis
    if (searchTimeMillis > 0) {
      val elapsed = System.currentTimeMillis() - searchTimeMillis
      val daysPastSinceLastUpdate = TimeUnit.MILLISECONDS.toDays(elapsed)
      if (daysPastSinceLastUpdate < 3) {
        // Display stored updates from previous search
        resultsUpdaterQueue.queue(UpdatesAvailable())
        return
      }
    }
    queueUpdateCheck()
  }

  private fun queueUpdateCheck() {
    mainQueue.queue(SearchForAvailableUpdates())
  }

  fun add(@UiThread listener: () -> Unit, parentDisposable: Disposable) {
    eventDispatcher.addListener(
      object : AvailableUpdatesListener {
        override fun availableUpdates() = listener()
      }, parentDisposable)
  }

  private fun search(
    repositories: Collection<ArtifactRepository>,
    ids: Collection<LibraryUpdateId>
  ) {
    getAvailableUpdates().clear()

    val requests =
      ids
        .map { id -> SearchRequest(SearchQuery(id.groupId, id.name), 1, 0) }
        .toSet()

    val searcher = repositorySearchFactory.create(repositories)
    val resultFutures = requests.map { searcher.search(it) }

    Disposer.register(this, Disposable {
      resultFutures.forEach { it.cancel(true) }
    })

    val foundArtifacts =
      resultFutures
        .map { it.getResultSafely() }
        .flatMap { it?.artifacts.orEmpty() }

    val updates = getAvailableUpdates()
    foundArtifacts.forEach { updates.add(it) }
    updates.lastSearchTimeMillis = System.currentTimeMillis()

    resultsUpdaterQueue.queue(UpdatesAvailable())
  }

  private inner class SearchForAvailableUpdates : Update(project) {
    override fun run() {
      val repositories = mutableSetOf<ArtifactRepository>()
      val ids = mutableSetOf<LibraryUpdateId>()
      UIUtil.invokeAndWaitIfNeeded(Runnable {
        project.forEachModule(Consumer { module ->
          repositories.addAll(module.getArtifactRepositories())
          if (module is PsAndroidModule) {
            module.dependencies.forEachLibraryDependency { dependency ->
              val spec = dependency.spec
              if (isNotEmpty(spec.version)) {
                val version = GradleVersion.tryParse(spec.version!!)
                if (version != null) {
                  ids.add(LibraryUpdateId(spec.group.orEmpty(), spec.name))
                }
              }
            }
          }
        })
      })
      if (repositories.isNotEmpty() && ids.isNotEmpty()) {
        search(repositories, ids)
      } else {
        resultsUpdaterQueue.queue(UpdatesAvailable())
      }
    }
  }

  private inner class UpdatesAvailable : Update(project) {

    @UiThread
    override fun run() {
      eventDispatcher.multicaster.availableUpdates()
    }
  }

  private interface AvailableUpdatesListener : EventListener {
    @UiThread
    fun availableUpdates()
  }
}

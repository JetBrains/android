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

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.gradle.repositories.search.RepositorySearchFactory
import com.android.tools.idea.gradle.structure.model.PsLibraryKey
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.repositories.search.ArtifactRepository
import com.android.tools.idea.gradle.repositories.search.FoundArtifact
import com.android.tools.idea.gradle.repositories.search.SearchQuery
import com.android.tools.idea.gradle.repositories.search.SearchRequest
import com.android.tools.idea.gradle.repositories.search.getResultSafely
import com.android.tools.idea.gradle.structure.model.toLibraryKey
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.nullize
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.MergingUpdateQueue.ANY_COMPONENT
import com.intellij.util.ui.update.Update
import java.util.Collections
import java.util.EventListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PsLibraryUpdateCheckerDaemon(
  parentDisposable: Disposable,
  private val project: PsProject,
  private val repositorySearchFactory: RepositorySearchFactory
) : PsDaemon(parentDisposable) {
  val availableLibraryUpdateStorage = AvailableLibraryUpdateStorage.getInstance(project.ideProject)
  override val mainQueue: MergingUpdateQueue = createQueue("Project Structure Daemon Update Checker", null)
  override val resultsUpdaterQueue: MergingUpdateQueue = createQueue("Project Structure Available Update Results Updater", ANY_COMPONENT)

  private val eventDispatcher = EventDispatcher.create(AvailableUpdatesListener::class.java)
  private val beingSearchedKeys: MutableSet<PsLibraryKey> = Collections.newSetFromMap(ConcurrentHashMap())
  @field:GuardedBy("runningLock") private val runningSearches: MutableSet<Future<*>> = mutableSetOf()
  private val runningLock: Lock = ReentrantLock()  // Guards runningSearches and persistent storage (in memory copy).

  fun queueUpdateCheck() {
    mainQueue.queue(RefreshAvailableUpdates())
  }

  fun add(@UiThread listener: () -> Unit, parentDisposable: Disposable) {
    eventDispatcher.addListener(
      object : AvailableUpdatesListener {
        @UiThread
        override fun availableUpdates() = listener()
      }, parentDisposable)
  }

  override fun dispose() {
    super.dispose()
    runningLock.withLock {
      runningSearches.forEach { it.cancel(true) }
    }
  }

  @Slow
  private fun search(
    repositories: Collection<ArtifactRepository>,
    keys: Collection<PsLibraryKey>
  ) {
    val updateStorage = availableLibraryUpdateStorage

    val currentTimeMillis = System.currentTimeMillis()
    val existingUpdateKeys = updateStorage
      .retainAll {
        val searchTimeMillis = it.lastSearchTimeMillis
        (searchTimeMillis > 0 && TimeUnit.MILLISECONDS.toDays(currentTimeMillis - searchTimeMillis) < 3 &&
         keys.contains(it.toLibraryKey()))
      }
      .associateBy { it.toLibraryKey() }

    val requests =
      keys
        .filter { !existingUpdateKeys.containsKey(it) && beingSearchedKeys.add(it) }
        .toSet()

    val searcher = repositorySearchFactory.create(repositories)
    val resultFutures = runningLock.withLock {
      if (isStopped) return@search
      // If we passed this point, it means that [dispose] has not yet begun to cancel requests and it won't until we release the lock.
      requests.map { key ->
        val future = searcher.search(SearchRequest(SearchQuery(key.group, key.name), 1, 0))
        runningSearches.add(future)
        key to future
      }
    }

    val searchResults = resultFutures.map {
      it.first to it.second.getResultSafely()
    }

    runningLock.withLock {
      runningSearches.removeAll(resultFutures.map { it.second })
    }

    val foundArtifacts =
      searchResults
        .flatMap {
          it.second?.artifacts?.nullize() ?: run {
            val key = it.first
            val result = it.second
            if (result?.errors?.isEmpty() == true) listOf(FoundArtifact("", key.group, key.name, listOf())) else listOf()
          }
        }
    searchResults.forEach {
      beingSearchedKeys.remove(it.first)
    }
    runningLock.withLock {
      // Under the lock to prevent stop/dispose from exiting while updating storage.
      if (!isStopped) {
        foundArtifacts.forEach { updateStorage.addOrUpdate(it, currentTimeMillis) }
      }
    }
    resultsUpdaterQueue.queue(UpdatesAvailable())
  }

  private inner class RefreshAvailableUpdates : Update(project) {
    override fun run() {
      val repositories = mutableSetOf<ArtifactRepository>()
      val keys = mutableSetOf<PsLibraryKey>()
      invokeAndWaitIfNeeded(ModalityState.any()) {
        if (isDisposed || isStopped) return@invokeAndWaitIfNeeded
        project.modules.forEach { module ->
          repositories.addAll(module.getArtifactRepositories())
          keys.addAll(
            module
              .dependencies
              .libraries
              .map { it.spec.toLibraryKey() }
          )
        }
      }
      if (!repositories.isEmpty() && !keys.isEmpty()) {
        search(repositories, keys)
      }
      else {
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

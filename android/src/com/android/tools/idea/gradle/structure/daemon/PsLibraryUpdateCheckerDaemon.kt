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

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.daemon.AvailableLibraryUpdateStorage.AvailableLibraryUpdates
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository
import com.android.tools.idea.gradle.structure.model.repositories.search.SearchRequest
import com.android.tools.idea.gradle.structure.model.repositories.search.SearchResult
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.text.StringUtil.isNotEmpty
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.MergingUpdateQueue.ANY_COMPONENT
import com.intellij.util.ui.update.Update
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

class PsLibraryUpdateCheckerDaemon(context: PsContext) : PsDaemon(context) {
  override val mainQueue: MergingUpdateQueue = createQueue("Project Structure Daemon Update Checker", null)
  override val resultsUpdaterQueue: MergingUpdateQueue = createQueue("Project Structure Available Update Results Updater", ANY_COMPONENT)

  private val running = AtomicBoolean(true)

  private val eventDispatcher = EventDispatcher.create(AvailableUpdatesListener::class.java)

  fun getAvailableUpdates(): AvailableLibraryUpdates = AvailableLibraryUpdateStorage.getInstance(context.project.ideProject).getState()

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

  fun add(listener: () -> Unit, parentDisposable: Disposable) {
    eventDispatcher.addListener(
      object : AvailableUpdatesListener {
        override fun availableUpdates() = listener()
      }, parentDisposable)
  }

  override val isRunning: Boolean get() = running.get()

  private fun search(repositories: Collection<ArtifactRepository>,
                     ids: Collection<LibraryUpdateId>) {
    running.set(true)
    getAvailableUpdates().clear()

    val resultCount = repositories.size * ids.size
    val jobs = Lists.newArrayListWithExpectedSize<Future<SearchResult>>(resultCount)

    val requests = Sets.newHashSet<SearchRequest>()
    ids.forEach { id ->
      val request = SearchRequest(id.name, id.groupId, 1, 0)
      requests.add(request)
    }

    val results = Sets.newHashSet<SearchResult>()
    val errors = Lists.newArrayList<Exception>()

    val application = ApplicationManager.getApplication()
    application.executeOnPooledThread {
      for (repository in repositories) {
        for (request in requests) {
          jobs.add(application.executeOnPooledThread<SearchResult> { repository.search(request) })
        }
      }

      for (job in jobs) {
        try {
          val result = Futures.getChecked(job, Exception::class.java)
          val artifacts = result.artifacts
          if (artifacts.size == 1) {
            val artifact = artifacts[0]
            if (!artifact.versions.isEmpty()) {
              results.add(result)
            }
          }
        }
        catch (e: Exception) {
          errors.add(e)
        }

      }

      val updates = getAvailableUpdates()

      for (result in results) {
        val artifacts = result.artifacts
        updates.add(artifacts[0])
      }

      updates.lastSearchTimeMillis = System.currentTimeMillis()

      resultsUpdaterQueue.queue(UpdatesAvailable())
    }
  }

  private inner class SearchForAvailableUpdates : Update(context.project) {
    override fun run() {
      val repositories = mutableSetOf<ArtifactRepository>()
      val ids = mutableSetOf<LibraryUpdateId>()
      UIUtil.invokeAndWaitIfNeeded(Runnable {
        context.project.forEachModule(Consumer { module ->
          repositories.addAll(module.getArtifactRepositories())
          if (module is PsAndroidModule) {
            module.dependencies.forEach { dependency ->
              if (dependency is PsLibraryDependency) {
                val libraryDependency = dependency as PsLibraryDependency
                val spec = libraryDependency.spec
                if (isNotEmpty(spec.version)) {
                  val version = GradleVersion.tryParse(spec.version!!)
                  if (version != null) {
                    ids.add(LibraryUpdateId(spec.name, spec.group))
                  }
                }
              }
            }
          }
        })
      })
      if (!repositories.isEmpty() && !ids.isEmpty()) {
        search(repositories, ids)
      } else {
        resultsUpdaterQueue.queue(UpdatesAvailable())
      }
    }
  }

  private inner class UpdatesAvailable : Update(context.project) {

    override fun run() {
      eventDispatcher.multicaster.availableUpdates()
      running.set(false)
    }
  }

  private interface AvailableUpdatesListener : EventListener {
    fun availableUpdates()
  }
}

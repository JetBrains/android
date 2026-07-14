/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.sdk

import com.android.repository.impl.manager.LocalRepoFileWatcher
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.util.CommonAndroidUtil
import com.android.tools.sdk.AndroidSdkData
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runInterruptible
import org.jetbrains.android.AndroidPluginDisposable

/**
 * Service to manage a file watcher for the Android SDK directory. We want only one watcher per SDK directory, and don't want it to run if
 * there's no Android project open. Thus, we have an application-scoped service that tracks the project lifecycle and SDK path changes to
 * maintain this.
 *
 * This is written as if each Project can have its own SDK path, which isn't currently the case, but the extra generality only makes the
 * code simpler.
 */
@Service(Service.Level.APP)
class AndroidSdkWatcherService(private val coroutineScope: CoroutineScope) : Disposable {
  companion object {
    @JvmStatic
    val instance: AndroidSdkWatcherService
      get() = service()
  }

  private val projectSdkPaths = mutableMapOf<Project, Path>()
  private val activeWatchers = mutableMapOf<Path, AndroidSdkWatcher>()

  fun registerProject(project: Project, sdkPath: Path) {
    val sdkHandler = AndroidSdkData.getSdkData(sdkPath)?.sdkHandler
    synchronized(this) {
      val previousPath = projectSdkPaths[project]
      if (previousPath == sdkPath) return
      if (previousPath != null) {
        unregisterProject(project)
      }
      projectSdkPaths[project] = sdkPath
      Disposer.register(AndroidPluginDisposable.getProjectInstance(project)) { unregisterProject(project) }
      if (!activeWatchers.containsKey(sdkPath)) {
        if (sdkHandler != null) {
          activeWatchers[sdkPath] = AndroidSdkWatcher(coroutineScope + Dispatchers.IO, sdkHandler, sdkPath)
        }
      }
    }
  }

  @Synchronized
  fun unregisterProject(project: Project) {
    val oldPath = projectSdkPaths.remove(project) ?: return
    // Check if any remaining open projects still need this watcher
    if (projectSdkPaths.values.none { it == oldPath }) {
      stopWatcher(oldPath)
    }
  }

  @Synchronized
  private fun stopWatcher(sdkPath: Path) {
    activeWatchers.remove(sdkPath)?.dispose()
  }

  override fun dispose() {
    synchronized(this) {
      activeWatchers.values.forEach { it.dispose() }
      activeWatchers.clear()
      projectSdkPaths.clear()
    }
  }
}

private class AndroidSdkWatcher(coroutineScope: CoroutineScope, val sdkHandler: AndroidSdkHandler, val location: Path) : Disposable {
  private val logger = thisLogger()
  private val progress = StudioLoggerProgressIndicator(AndroidSdkWatcher::class.java)
  val watcher = LocalRepoFileWatcher.create(location, progress)

  private val job =
    coroutineScope.launch {
      // Interruption is not always sufficient to get out of the blocking read, we need to close the watcher explicitly
      currentCoroutineContext().job.invokeOnCompletion { watcher.close() }
      val repoManager = sdkHandler.getRepoManager(progress)
      flow {
          while (isActive) {
            if (runInterruptible { watcher.consumeWatchEvents(progress, blocking = true) }) {
              emit(Unit)
            }
          }
        }
        .retry { e ->
          when (e) {
            is ClosedWatchServiceException -> false
            else -> {
              logger.warn("Error consuming SDK watch events for $location", e)
              true
            }
          }
        }
        .debounce(100.milliseconds)
        .collect {
          logger.debug("Reloading local SDK packages due to filesystem changes")
          repoManager.loadLocalPackages(progress, cacheExpiration = Duration.ZERO)
        }
    }

  override fun dispose() {
    job.cancel()
    watcher.close()
  }
}

class AndroidSdkWatcherProjectActivity : ProjectActivity {
  init {
    // Various unit tests become flaky or fail when this watcher is running in the background updating the SDK.
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (!CommonAndroidUtil.getInstance().isAndroidProject(project)) return
    val sdkPath = IdeSdks.getInstance().getAndroidSdkPath()?.toPath()
    if (sdkPath != null) {
      AndroidSdkWatcherService.instance.registerProject(project, sdkPath)
    }
  }
}

class AndroidSdkWatcherEventListener : IdeSdks.AndroidSdkEventListener {
  init {
    // Various unit tests become flaky or fail when this watcher is running in the background updating the SDK.
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun afterSdkPathChange(sdkPath: File, project: Project) {
    service<AndroidSdkWatcherService>().registerProject(project, sdkPath.toPath())
  }
}

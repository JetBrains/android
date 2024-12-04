/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.adblib

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.adb.AdbFileProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File
import kotlinx.coroutines.CancellationException
import java.util.function.Supplier

/**
 * An [AdbFileLocationTracker] that keeps track of [Project] instances to retrieve the path
 * to `adb` on a "best effort" basis. If it "best effort", because Android Studio currently
 * does not support multiple `adb` paths and/or versions.
 *
 * This class is thread-safe.
 */
@AnyThread
internal class AdbFileLocationTracker : Supplier<File> {
  private val logger = thisLogger()

  /**
   * The application [AdbFileProvider], always available
   */
  private val applicationProvider by lazy(LazyThreadSafetyMode.PUBLICATION) {
    AdbFileProvider.fromApplication()
  }

  /**
   * One [AdbFileProvider] per project (we use a LinkedHashMap to keep enumeration ordering consistent).
   */
  @GuardedBy("projectProviders")
  private val projectProviders = LinkedHashMap<Project, AdbFileProvider>()

  /**
   * Registers a [Project] as a possible source of `adb` path location. The same [Project]
   * instance can be registered multiple times (for convenience).
   */
  fun registerProject(project: Project): Boolean {
    synchronized(projectProviders) {
      return if (!projectProviders.contains(project)) {
        logger.info("Registering project to adblib channel provider: $project")
        projectProviders[project] = AdbFileProvider.fromProject(project)
        true
      }
      else {
        false
      }
    }
  }

  /**
   * Unregisters a [Project] as a possible source of `adb` path location.
   */
  fun unregisterProject(project: Project): Boolean {
    return synchronized(projectProviders) {
      logger.info("Unregistering project from adblib channel provider: $project")
      projectProviders.remove(project) != null
    }
  }

  override fun get(): File {
    // Go through projects first
    val file = synchronized(projectProviders) {
      projectProviders.values.firstNotNullOfOrNull { it.get() }
    }
    // Then application if nothing found
    try {
      return file ?: applicationProvider.get() ?: throw IllegalStateException("ADB location has not been initialized")
    }
    catch (e: IllegalStateException) {
      throw if (ApplicationManager.getApplication().isDisposed) CancellationException() else e
    }
  }
}
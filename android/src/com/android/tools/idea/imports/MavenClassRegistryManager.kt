/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.imports

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.util.Disposer
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

/** Key used in cache directories to locate the gmaven.index network cache. */
private const val GMAVEN_INDEX_CACHE_DIR_KEY = "gmaven.index"

/** Scheduled refreshment interval for local disk cache. */
private val REFRESH_INTERVAL: Duration = Duration.ofDays(1)

/**
 * An application service responsible for downloading index from network and populating the corresponding Maven
 * class registry. [getMavenClassRegistry] returns the the best effort of Maven class registry when asked.
 */
class MavenClassRegistryManager : Disposable {
  private val gMavenIndexRepository: GMavenIndexRepository

  init {
    gMavenIndexRepository = GMavenIndexRepository(BASE_URL, getCacheDir(), REFRESH_INTERVAL)
    Disposer.register(this, gMavenIndexRepository)
  }

  /**
   * Returns [MavenClassRegistry] extracted from [gMavenIndexRepository].
   */
  fun getMavenClassRegistry(): MavenClassRegistry {
    return gMavenIndexRepository.getMavenClassRegistry()
  }

  private fun getCacheDir(): Path {
    return Paths.get(PathManager.getSystemPath(), GMAVEN_INDEX_CACHE_DIR_KEY)
  }

  override fun dispose() {}

  companion object {
    @JvmStatic
    fun getInstance(): MavenClassRegistryManager = ApplicationManager.getApplication().getService(MavenClassRegistryManager::class.java)
  }
}

class AutoRefresherForMavenClassRegistry : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    // Start refresher in GMavenIndexRepository at project start-up.
    MavenClassRegistryManager.getInstance()
  }
}
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

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.application
import java.nio.file.Paths
import kotlinx.coroutines.CoroutineScope

/** Key used in cache directories to locate the gmaven.index network cache. */
private const val GMAVEN_INDEX_CACHE_DIR_KEY = "gmaven.index"

/**
 * An application service responsible for downloading index from network and populating the
 * corresponding Maven class registry. [getMavenClassRegistry] returns the the best effort of Maven
 * class registry when asked.
 */
@Service
class MavenClassRegistryManager(coroutineScope: CoroutineScope) {
  private val gMavenIndexRepository =
    GMavenIndexRepository(
      BASE_URL,
      Paths.get(PathManager.getSystemPath(), GMAVEN_INDEX_CACHE_DIR_KEY),
      coroutineScope,
    )

  /** Returns [MavenClassRegistry] extracted from [gMavenIndexRepository]. */
  fun getMavenClassRegistry() = gMavenIndexRepository.getMavenClassRegistry()

  companion object {
    @JvmStatic fun getInstance(): MavenClassRegistryManager = application.service()
  }
}

class AutoRefresherForMavenClassRegistry : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    if (
      !IdeInfo.getInstance().isAndroidStudio && !IdeSdks.getInstance().hasConfiguredAndroidSdk()
    ) {
      // IDE must not hit network on startup
      return
    }

    // Start refresher in GMavenIndexRepository at project start-up.
    MavenClassRegistryManager.getInstance()
  }
}

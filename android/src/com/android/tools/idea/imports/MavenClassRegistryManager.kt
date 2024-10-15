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
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.application
import java.util.concurrent.atomic.AtomicReference

/**
 * An application service responsible for downloading index from network and populating the
 * corresponding Maven class registry. [getMavenClassRegistry] returns the best effort of Maven
 * class registry when asked.
 */
@Service
class MavenClassRegistryManager : Disposable.Default {
  init {
    GMavenIndexRepository.getInstance().addListener(IndexListener, this)
  }

  private val lastComputedMavenClassRegistry = AtomicReference<MavenClassRegistry?>()

  /** Returns [MavenClassRegistry]. */
  fun getMavenClassRegistry(): MavenClassRegistry {
    return lastComputedMavenClassRegistry.get()
      ?: MavenClassRegistry.createFrom { GMavenIndexRepository.getInstance().loadIndexFromDisk() }
        .apply { lastComputedMavenClassRegistry.set(this) }
  }

  private fun onIndexUpdated() {
    lastComputedMavenClassRegistry.getAndUpdate {
      if (it == null) {
        null
      } else {
        val mavenClassRegistry =
          MavenClassRegistry.createFrom { GMavenIndexRepository.getInstance().loadIndexFromDisk() }
        thisLogger().info("Updated in-memory Maven class registry.")
        mavenClassRegistry
      }
    }
  }

  private object IndexListener : GMavenIndexRepositoryListener {
    override fun onIndexUpdated() {
      getInstance().onIndexUpdated()
    }
  }

  companion object {
    @JvmStatic fun getInstance(): MavenClassRegistryManager = application.service()
  }
}

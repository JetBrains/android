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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly

/**
 * An application service responsible for downloading index from network and populating the
 * corresponding Maven class registry. [getMavenClassRegistry] returns the most up-to-date Maven
 * class registry available.
 */
@Service
class MavenClassRegistryManager
@TestOnly
internal constructor(
  private val coroutineScope: CoroutineScope,
  private val defaultDispatcher: CoroutineDispatcher,
  private val ioDispatcher: CoroutineDispatcher,
) : Disposable.Default {

  constructor(
    coroutineScope: CoroutineScope
  ) : this(coroutineScope, Dispatchers.Default, Dispatchers.IO)

  /**
   * Job that returns a [MavenClassRegistry].
   *
   * This is initially set to an unlaunched Job, so that the registry is only initialized if some
   * consumer needs it. At the time it runs, it also registers a listener for updates whenever the
   * underlying index is changed.
   *
   * After any updates, this will be replaced with a newly completed Job that returns the new
   * [MavenClassRegistry].
   */
  @Volatile
  private var registryJob =
    coroutineScope.async(defaultDispatcher, CoroutineStart.LAZY) {
      // Register for index updates only now that we're initializing the registry; any index updates
      // before initialization can be ignored, since there's no registry to be updated.
      val gmavenIndexRepository = GMavenIndexRepository.getInstance()
      gmavenIndexRepository.addListener(::onIndexUpdated, this@MavenClassRegistryManager)

      withContext(ioDispatcher) {
        MavenClassRegistry.createFrom { gmavenIndexRepository.loadIndexFromDisk() }
      }
    }

  /**
   * Returns a [MavenClassRegistry]. Blocks for disk IO if the registry hasn't been initialized yet.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  @Deprecated("Use tryGetMavenClassRegistry or getMavenClassRegistry instead.")
  fun getMavenClassRegistryBlocking(): MavenClassRegistry {
    val job = registryJob
    if (job.isCompleted) return job.getCompleted()

    return runBlocking { job.await() }
  }

  /**
   * Returns [MavenClassRegistry] if it has been initialized. Otherwise, kicks off initialization in
   * the background and immediately returns null.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun tryGetMavenClassRegistry(): MavenClassRegistry? {
    val job = registryJob
    if (job.isCompleted) return job.getCompleted()

    job.start()
    return null
  }

  /**
   * Returns a [MavenClassRegistry]. Suspends for disk IO if the registry hasn't been initialized
   * yet.
   */
  suspend fun getMavenClassRegistry(): MavenClassRegistry {
    return registryJob.await()
  }

  private fun onIndexUpdated() {
    coroutineScope.launch(defaultDispatcher) {
      val job =
        coroutineScope.async(ioDispatcher) {
          MavenClassRegistry.createFrom { GMavenIndexRepository.getInstance().loadIndexFromDisk() }
        }

      // Only store the new job in [registryJob] after it's finished initialization in the
      // background. This allows any consumers to continue to use the older index while the new one
      // is being created.
      job.join()
      registryJob = job
      thisLogger().info("Updated in-memory Maven class registry.")
    }
  }

  companion object {
    @JvmStatic fun getInstance(): MavenClassRegistryManager = application.service()
  }
}

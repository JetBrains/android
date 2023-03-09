/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ShutDownTracker
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly

/** Studio-specific [RenderService] management. */
open class StudioRenderService {
  companion object {
    /**
     * [Key] used to keep the [StudioRenderService] instance project association. They key is also used as synchronization object to guard
     * the access to the new instances.
     */
    private val KEY: Key<RenderService> = Key.create(RenderService::class.java.name)
    init {
      // Register the executor to be shutdown on close
      ShutDownTracker.getInstance().registerShutdownTask { RenderService.shutdownRenderExecutor() }
    }

    /**
     * @return the [RenderService] for the given facet.
     */
    @JvmStatic
    fun getInstance(project: Project): RenderService {
      synchronized(KEY) {
        var renderService = project.getUserData(KEY)
        if (renderService == null) {
          renderService = RenderService { }
          Disposer.register(project, renderService)
          Disposer.register(renderService) { project.putUserData(KEY, null) }
          project.putUserData(KEY, renderService)
        }
        return renderService
      }
    }

    @TestOnly
    @JvmStatic
    fun setForTesting(project: Project, renderService: RenderService?) {
      synchronized(KEY) { project.putUserData(KEY, renderService) }
    }
  }
}

/**
 * Returns a [RenderService.RenderTaskBuilder] that can be used to build a new [RenderTask].
 */
fun RenderService.taskBuilder(facet: AndroidFacet, configuration: Configuration, logger: RenderLogger): RenderService.RenderTaskBuilder =
  taskBuilder(AndroidFacetRenderModelModule(facet), StudioRenderConfiguration(configuration), logger)

/**
 * Returns a [RenderService.RenderTaskBuilder] that can be used to build a new [RenderTask].
 */
fun RenderService.taskBuilder(facet: AndroidFacet, configuration: Configuration): RenderService.RenderTaskBuilder =
  taskBuilder(facet, configuration, createLogger(facet.module, StudioFlags.NELE_LOG_ANDROID_FRAMEWORK.get()))
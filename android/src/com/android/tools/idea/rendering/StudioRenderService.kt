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

import com.android.sdklib.IAndroidTarget
import com.android.tools.configurations.Configuration
import com.android.tools.idea.layoutlib.LayoutLibrary
import com.android.tools.idea.layoutlib.RenderingException
import com.android.tools.sdk.getLayoutLibrary
import com.android.tools.rendering.RenderLogger
import com.android.tools.rendering.RenderService
import com.android.tools.rendering.RenderTask
import com.android.tools.rendering.api.RenderModelModule
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ShutDownTracker
import org.jetbrains.android.sdk.getInstance
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.utils.identity

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
@JvmOverloads
fun RenderService.taskBuilder(
  buildTarget: AndroidBuildTargetReference,
  configuration: Configuration,
  logger: RenderLogger = createLogger(buildTarget.project),
  wrapRenderModule: (RenderModelModule) -> RenderModelModule = identity(),
): RenderService.RenderTaskBuilder =
  taskBuilder(wrapRenderModule(AndroidFacetRenderModelModule(buildTarget)), configuration, logger)

fun getLayoutLibrary(module: Module, target: IAndroidTarget?): LayoutLibrary? {
  val context = StudioLayoutlibContext(module.project)
  val platform = getInstance(module)
  if (target == null || platform == null) {
    return null
  }

  return try {
    getLayoutLibrary(target, platform, context)
  } catch (e: RenderingException) {
    null
  }
}

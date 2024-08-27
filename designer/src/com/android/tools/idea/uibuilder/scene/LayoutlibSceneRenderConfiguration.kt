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
package com.android.tools.idea.uibuilder.scene

import com.android.annotations.TestOnly
import com.android.ide.common.rendering.api.SessionParams
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.LayoutScannerConfiguration
import com.android.tools.idea.rendering.parsers.PsiXmlFile
import com.android.tools.idea.rendering.taskBuilder
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.rendering.RenderAsyncActionExecutor.RenderingTopic
import com.android.tools.rendering.RenderLogger
import com.android.tools.rendering.RenderService
import com.android.tools.rendering.RenderService.RenderTaskBuilder
import com.android.tools.rendering.RenderTask
import com.android.tools.rendering.api.RenderModelModule
import com.android.tools.rendering.imagepool.ImagePool
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.await
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.job
import org.jetbrains.kotlin.utils.identity

/**
 * Render configuration to be used when rendering the given [model].
 *
 * This class contains two type of configuration options:
 * - Some to be used when creating a [RenderTask] for the [model].
 * - Some to be used by the corresponding [LayoutlibSceneRenderer] when inflating and rendering.
 */
class LayoutlibSceneRenderConfiguration(
  private val model: NlModel,
  private val surface: NlDesignSurface,
  /**
   * Configuration for layout validation from Accessibility Testing Framework through Layoutlib.
   * Based on the configuration layout validation will be turned on or off while rendering.
   */
  val layoutScannerConfig: LayoutScannerConfiguration,
) {

  /**
   * Topic to tag the render with. This is used to identify the tool or context under which a render
   * is executed.
   */
  var renderingTopic: RenderingTopic = RenderingTopic.NOT_SPECIFIED

  /**
   * When true, the model will be inflated on the next render, causing the existing [RenderTask] to
   * be disposed and replaced by a newer one.
   *
   * Clients of this render configuration should set this to true to indicate the need of
   * re-inflating. And the corresponding [LayoutlibSceneRenderer] will reset this to false after the
   * inflation is done.
   *
   * The initial value is true because the model needs to be inflated on the first render.
   */
  val needsInflation = AtomicBoolean(true)

  /** When true, a re-render of the model will be done after the next render if needed. */
  val doubleRenderIfNeeded = AtomicBoolean(false)

  /**
   * If true, when a render fails, the result will retain the last successful image.
   *
   * This flag has no effect in metrics and the actual result will be reported.
   */
  var cacheSuccessfulRenderImage = false

  /**
   * If true, errors during inflation and render will be logged and made available to be shown in
   * the errors panel.
   */
  var logRenderErrors = true

  /** If true, a transparent background will be used when rendering. */
  var useTransparentRendering = false
    set(value) {
      if (field != value) {
        field = value
        needsInflation.set(true)
      }
    }

  /** If true, [SessionParams.RenderingMode.SHRINK] will be used when rendering. */
  var useShrinkRendering = false
    set(value) {
      if (field != value) {
        field = value
        needsInflation.set(true)
      }
    }

  /** If true, system decorations (status and navigation bards) will be painted when rendering. */
  var showDecorations = false
    set(value) {
      if (field != value) {
        field = value
        // Showing decorations changes the XML content of the render so requires re-inflation
        needsInflation.set(true)
      }
    }

  /**
   * If true, the scene should use a private ClassLoader.
   *
   * Whether this scene manager should use a private/individual ClassLoader. If two compose previews
   * share the same ClassLoader they share the same compose framework. This way they share the
   * state. In the interactive preview and animation inspector, we would like to control the state
   * of the framework and preview. Shared state makes control impossible. Therefore, in certain
   * situations (currently only in compose) we want to create a dedicated ClassLoader so that the
   * preview has its own compose framework. Having a dedicated ClassLoader also allows for clearing
   * resources right after the preview no longer used. We could apply this approach by default (e.g.
   * for static previews as well) but it might have a negative impact if there are many of them.
   * Therefore, this should be configured by calling this method when we want to use the private
   * ClassLoader, e.g. in interactive previews or animation inspector.
   */
  var usePrivateClassLoader = false
    set(value) {
      if (field != value) {
        field = value
        // Showing decorations changes the XML content of the render so requires re-inflation
        needsInflation.set(true)
      }
    }

  /**
   * List of classes to preload when rendering.
   *
   * This intended for classes that are very likely to be used, so that they can be preloaded.
   * Interactive Preview is an example where preloading classes is a good idea.
   */
  var classesToPreload = emptyList<String>()

  /** If false, the [ImagePool] won't be used when rendering. */
  var useImagePool = true

  /**
   * Value in the range [0f..1f] to set the quality of the rendering, 0 meaning the lowest quality.
   */
  var quality = 1f

  /** The time for which the frame to be rendered will be selected. */
  var elapsedFrameTimeMs: Long = -1

  /**
   * If true, the rendering will report when the user classes used are out of date and have been
   * modified after the last build. The reporting will be done via the rendering log. Compose has
   * its own mechanism to track out of date files, so it will disable this reporting.
   */
  var reportOutOfDateUserClasses = false

  /**
   * Custom parser that will be applied to the root view of the layout in order to build the
   * ViewInfo hierarchy. If null, layoutlib will use its default parser.
   */
  var customContentHierarchyParser: ((Any) -> List<ViewInfo>)? = null

  /** If true, layoutlib will search for a custom inflater when rendering and try to use it. */
  var useCustomInflater = true

  /** No-op in production, intended to be used for testing purposes only. */
  private var wrapRenderModule: (RenderModelModule) -> RenderModelModule = identity()

  /** No-op in production, intended to be used for testing purposes only. */
  private var wrapRenderTaskBuilder: (RenderTaskBuilder) -> RenderTaskBuilder = identity()

  @TestOnly
  fun setRenderModuleWrapperForTest(wrapper: (RenderModelModule) -> RenderModelModule) {
    wrapRenderModule = wrapper
  }

  @TestOnly
  fun setRenderTaskBuilderWrapperForTest(wrapper: (RenderTaskBuilder) -> RenderTaskBuilder) {
    wrapRenderTaskBuilder = wrapper
  }

  /** Creates a new [RenderTask] for the [model] using the current configuration values */
  internal suspend fun createRenderTask(
    configuration: Configuration,
    renderService: RenderService,
    logger: RenderLogger,
  ): RenderTask? {
    val taskBuilder =
      renderService
        .taskBuilder(model.buildTarget, configuration, logger) { wrapRenderModule(it) }
        .withPsiFile(PsiXmlFile(model.file))
        .withLayoutScanner(layoutScannerConfig.isLayoutScannerEnabled)
        .withTopic(renderingTopic)
        .setUseCustomInflater(useCustomInflater)
    if (!useImagePool) taskBuilder.disableImagePool()
    if (quality < 1f) taskBuilder.withQuality(quality)
    if (!showDecorations) taskBuilder.disableDecorations()
    if (useShrinkRendering) taskBuilder.withRenderingMode(SessionParams.RenderingMode.SHRINK)
    if (useTransparentRendering) taskBuilder.useTransparentBackground()
    if (usePrivateClassLoader) taskBuilder.usePrivateClassLoader()
    if (classesToPreload.isNotEmpty()) taskBuilder.preloadClasses(classesToPreload)
    if (!reportOutOfDateUserClasses) taskBuilder.doNotReportOutOfDateUserClasses()
    customContentHierarchyParser?.let { taskBuilder.setCustomContentHierarchyParser(it) }
    if (!surface.layoutPreviewHandler.previewWithToolsVisibilityAndPosition) {
      taskBuilder.disableToolsVisibilityAndPosition()
    }
    val disposable = Disposer.newCheckedDisposable("RenderTaskBuilderDisposable")
    // Register a disposal callback that will be executed when the coroutine scope completes
    // (normally or due to cancellation)
    coroutineContext.job.invokeOnCompletion { Disposer.dispose(disposable) }
    return wrapRenderTaskBuilder(taskBuilder).build(disposable).await()
  }
}

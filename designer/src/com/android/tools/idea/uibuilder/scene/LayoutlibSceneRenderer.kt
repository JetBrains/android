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

import com.android.annotations.concurrency.GuardedBy
import com.android.ide.common.rendering.api.ILayoutLog
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.analytics.CommonUsageTracker
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.rendering.ShowFixFactory
import com.android.tools.idea.rendering.StudioRenderService
import com.android.tools.idea.rendering.createHtmlLogger
import com.android.tools.idea.rendering.createRenderTaskErrorResult
import com.android.tools.idea.rendering.isErrorResult
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.res.ResourceNotificationManager.ResourceVersion
import com.android.tools.idea.uibuilder.io.saveFileIfNecessary
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.rendering.ProblemSeverity
import com.android.tools.rendering.RenderLogger
import com.android.tools.rendering.RenderProblem
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.RenderService
import com.android.tools.rendering.RenderTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.UIUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.jetbrains.android.facet.AndroidFacet

// TODO(b/335424569): this class is meant to be used for extracting the rendering responsibilities
//   out of LayoutlibSceneManager. Add proper class description later.
internal class LayoutlibSceneRenderer(
  parentDisposable: Disposable,
  private val disposeExecutor: Executor,
  private val model: NlModel,
  private val surface: NlDesignSurface,
  private val renderTaskFactory:
    (Configuration, RenderService, RenderLogger) -> CompletableFuture<RenderTask?>,
) : Disposable {
  private val scope = AndroidCoroutineScope(this)
  private val isDisposed = AtomicBoolean(false)
  private val updateHierarchyLock = ReentrantLock()
  private val renderTaskLock = ReentrantLock()
  private val renderResultLock = ReentrantLock()

  /** If true, when a new RenderResult is an error it will retain the last successful image. */
  var cacheSuccessfulRenderImage = false

  /** The version of Android resources when the last inflation was done. See [ResourceVersion] */
  var renderedVersion: ResourceVersion? = null
    private set

  /**
   * The quality used the last time the content of this scene manager was successfully rendered.
   * Defaults to 0 until a successful render happens.
   */
  var lastRenderQuality = 0f
    private set

  init {
    Disposer.register(parentDisposable, this)
  }

  // TODO(b/335424569): make this field private, or at least its setter
  @GuardedBy("renderTaskLock")
  var renderTask: RenderTask? = null
    get() = renderTaskLock.withLock { field }
    set(newTask) {
      val oldTask: RenderTask?
      renderTaskLock.withLock {
        oldTask = field
        // TODO(b/168445543): move session clock to RenderTask
        sessionClock = RealTimeSessionClock()
        field = newTask
      }
      try {
        oldTask?.dispose()
      } catch (t: Throwable) {
        Logger.getInstance(LayoutlibSceneManager::class.java).warn(t)
      }
    }

  // TODO(b/335424569): make this field private
  @GuardedBy("renderTaskLock")
  var sessionClock: SessionClock = RealTimeSessionClock()
    get() = renderTaskLock.withLock { field }
    private set

  // TODO(b/335424569): make this field private, or at least its setter
  var renderResult: RenderResult? = null
    get() = renderResultLock.withLock { field }
    set(newResult) {
      val oldResult: RenderResult?
      renderResultLock.withLock {
        if (field === newResult) return
        oldResult = field
        field =
          if (
            cacheSuccessfulRenderImage &&
              newResult.isErrorResult() &&
              oldResult.containsValidImage()
          ) {
            // newResult can not be null if isErrorResult is true
            // oldResult can not be null if containsValidImage is true
            newResult!!.copyWithNewImageAndRootViewDimensions(
              StudioRenderService.Companion.getInstance(newResult.project)
                .sharedImagePool
                .copyOf(oldResult!!.getRenderedImage().copy),
              oldResult.rootViewDimensions,
            )
          } else newResult
      }
      oldResult?.dispose()
    }

  // TODO(b/335424569): remove this method, directly use the suspendable render
  fun renderAsync(
    forceInflate: Boolean,
    logRenderErrors: Boolean,
    reverseUpdate: AtomicBoolean,
    elapsedFrameTimeMs: Long,
    quality: Float,
  ): CompletableFuture<RenderResult?> =
    scope
      .async { render(forceInflate, logRenderErrors, reverseUpdate, elapsedFrameTimeMs, quality) }
      .asCompletableFuture()

  private suspend fun render(
    forceInflate: Boolean,
    logRenderErrors: Boolean,
    reverseUpdate: AtomicBoolean,
    elapsedFrameTimeMs: Long,
    quality: Float,
  ): RenderResult? {
    var result: RenderResult? = null
    try {
      // Inflate only if needed
      val inflateResult =
        if (renderTask != null && !forceInflate) null else inflate(logRenderErrors, reverseUpdate)
      if (inflateResult?.renderResult?.isSuccess == false) {
        surface.updateErrorDisplay()
        return null
      }
      renderTask?.let {
        if (elapsedFrameTimeMs != -1L) {
          it.setElapsedFrameTimeNanos(TimeUnit.MILLISECONDS.toNanos(elapsedFrameTimeMs))
        }
        // Make sure that the task's quality is up-to-date before rendering
        it.setQuality(quality)
        result = it.render().await() // await is the suspendable version of join
        if (result?.renderResult?.isSuccess == true) {
          lastRenderQuality = quality
          // When the layout was inflated in this same call, we do not have to update the hierarchy
          // again
          if (inflateResult != null) reverseUpdate.set(updateHierarchy(result))
        }
      }
    } catch (throwable: Throwable) {
      if (!model.isDisposed) {
        result =
          createRenderTaskErrorResult(
            model.file,
            (throwable as? CompletionException)?.cause ?: throwable,
          )
      }
    }

    result?.let { renderResult = result }
    return result
  }

  /**
   * Inflates the model and updates the view hierarchy
   *
   * @param force forces the model to be re-inflated even if a previous version was already inflated
   * @return A [CompletableFuture] containing the [RenderResult] of the inflate operation or
   *   containing null if the model did not need to be re-inflated or could not be re-inflated (like
   *   the project been disposed).
   */
  private suspend fun inflate(
    logRenderErrors: Boolean,
    reverseUpdate: AtomicBoolean,
  ): RenderResult? {
    val project: Project = model.project
    if (project.isDisposed || isDisposed.get()) {
      return null
    }

    // Some types of files must be saved to disk first, because layoutlib doesn't
    // delegate XML parsers for non-layout files (meaning layoutlib will read the
    // disk contents, so we have to push any edits to disk before rendering)
    model.file.saveFileIfNecessary()

    // Record the current version we're rendering from; we'll use that in #activate to make sure
    // we're picking up any external changes
    val facet: AndroidFacet = model.facet
    val configuration: Configuration = model.configuration
    val resourceNotificationManager = ResourceNotificationManager.getInstance(project)
    renderedVersion =
      resourceNotificationManager.getCurrentVersion(facet, model.file, configuration)

    val renderService = StudioRenderService.getInstance(model.project)
    val logger =
      if (logRenderErrors) renderService.createHtmlLogger(project) else renderService.nopLogger

    var result: RenderResult?
    try {
      val newTask =
        renderTaskFactory(configuration, renderService, logger)
          .await() // await is the suspendable version of join
      result =
        if (newTask != null) doInflate(newTask, logger)
        else createRenderTaskErrorResult(model.file, logger)
    } catch (throwable: Throwable) {
      Logger.getInstance(LayoutlibSceneRenderer::class.java).warn(throwable)
      result = createRenderTaskErrorResult(model.file, throwable)
    }
    result?.let {
      renderResult = it
      if (it.renderResult.isSuccess) {
        reverseUpdate.set(updateHierarchy(result))
        // Do more updates async
        scope.launch(workerThread) {
          model.notifyListenersModelDerivedDataChanged()
          CommonUsageTracker.getInstance(surface)
            .logRenderResult(null, result, CommonUsageTracker.RenderResultType.INFLATE)
        }
      }
    }
    return result
  }

  private suspend fun doInflate(newTask: RenderTask, logger: RenderLogger): RenderResult? {
    newTask.defaultForegroundColor = '#'.toString() + ColorUtil.toHex(UIUtil.getLabelForeground())
    var result: RenderResult? = null
    try {
      result = newTask.inflate().await() // await is the suspendable version of join
      // If the result is not valid or the project was already disposed, then we do not need the
      // task. Also remove the previous task if the render has finished but was not a success.
      if (
        model.module.isDisposed ||
          result == null ||
          !result.renderResult.isSuccess ||
          isDisposed.get()
      ) {
        newTask.dispose()
        renderTask = null
      } else renderTask = newTask
      result?.renderResult?.exception?.let { throw it }
    } catch (throwable: Throwable) {
      Logger.getInstance(LayoutlibSceneRenderer::class.java).warn(throwable)
      if (result == null || !result.renderResult.isSuccess) {
        // Do not ignore ClassNotFoundException on inflate
        if (throwable is ClassNotFoundException) {
          logger.addMessage(
            RenderProblem.createHtml(
              ProblemSeverity.ERROR,
              "Error inflating the preview",
              model.project,
              logger.linkManager,
              throwable,
              ShowFixFactory,
            )
          )
        } else {
          logger.error(ILayoutLog.TAG_INFLATE, "Error inflating the preview", throwable, null, null)
        }
      }
      result = result ?: createRenderTaskErrorResult(model.file, throwable)
    }
    return result
  }

  private fun RenderResult?.containsValidImage() =
    this?.renderedImage?.let { it.width > 1 && it.height > 1 } ?: false

  // TODO(b/335424569): make this method private
  fun updateHierarchy(result: RenderResult?): Boolean {
    var reverseUpdate = false
    try {
      updateHierarchyLock.withLock {
        reverseUpdate =
          if (result == null || !result.renderResult.isSuccess) {
            NlModelHierarchyUpdater.updateHierarchy(emptyList<ViewInfo>(), model)
          } else {
            NlModelHierarchyUpdater.updateHierarchy(result, model)
          }
      }
    } catch (ignored: InterruptedException) {}
    return reverseUpdate
  }

  fun deactivate() {
    renderTask = null
    renderResult = null
  }

  override fun dispose() {
    if (isDisposed.getAndSet(true)) return
    if (ApplicationManager.getApplication().isReadAccessAllowed) {
      // dispose is called by the project close using the read lock. Invoke the render task dispose
      // later without the lock.
      disposeExecutor.execute(::deactivate)
    } else deactivate()
  }
}

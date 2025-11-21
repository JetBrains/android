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
import com.android.tools.idea.common.diagnostics.NlDiagnosticsManager
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.LayoutScannerConfiguration
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.concurrency.executeOnPooledThread
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
import com.android.tools.rendering.RenderTask
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.UIUtil
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.android.facet.AndroidFacet

private class RenderRequest(val trigger: LayoutEditorRenderResult.Trigger?) {
  val requestTime: Long = System.currentTimeMillis()
}

/**
 * Class responsible for inflation and rendering of the given [NlModel]. It's configurable via its
 * [sceneRenderConfiguration]. This class is also responsible for handling render requests by
 * enqueueing them, skipping some and processing others (see [requestsChannel]), and handling the
 * lifecycle of its associated [RenderTask]s and [RenderResult]s.
 *
 * @param parentDisposable parent to use when registering this renderer into the [Disposer], usually
 *   the scene manager using this renderer.
 * @param disposeExecutor executor where deactivation should be executed when disposing.
 * @param model the [NlModel] that this renderer will be rendering.
 * @param surface the [NlDesignSurface] where the model is being displayed.
 */
class LayoutlibSceneRenderer(
  parentDisposable: Disposable,
  private val disposeExecutor: Executor,
  private val model: NlModel,
  private val surface: NlDesignSurface,
  layoutScannerConfig: LayoutScannerConfiguration,
) : Disposable {
  private val log = Logger.getInstance(LayoutlibSceneRenderer::class.java)
  private val isDisposed = AtomicBoolean(false)
  private val isActive = AtomicBoolean(true)
  private val renderIsRunning = AtomicBoolean(false)

  /**
   * Worker thread based coroutine scope used for processing render requests, and performing
   * inflation and rendering.
   */
  private val scope: CoroutineScope

  /** Lock guarding execution of [NlModelHierarchyUpdater.updateHierarchy] for [model]. */
  private val updateHierarchyLock = ReentrantLock()

  /** Lock guarding read and write of [renderTask]. */
  private val renderTaskLock = ReentrantLock()

  /** Lock guarding read and write of [renderResult]. */
  private val renderResultLock = ReentrantLock()

  /** Lock guarding the enqueueing of requests into [requestsChannel]. */
  private val requestsLock = Mutex()

  /** The configuration to use when inflating and rendering, and for creating new [RenderTask]s. */
  val sceneRenderConfiguration =
    LayoutlibSceneRenderConfiguration(model, surface, layoutScannerConfig)

  /** The version of Android resources when the last inflation was done. See [ResourceVersion] */
  var renderedVersion: ResourceVersion? = null
    private set

  /**
   * The quality used the last time the content of this scene manager was successfully rendered.
   * Defaults to 0 until a successful render happens.
   */
  var lastRenderQuality = 0f
    private set

  @GuardedBy("renderTaskLock")
  internal var renderTask: RenderTask? = null
    get() = renderTaskLock.withLock { field }
    private set(newTask) {
      val oldTask: RenderTask?
      renderTaskLock.withLock {
        // If renderer is inactive or disposed, any new task should be immediately disposed
        if (isDisposedOrDeactivated() && newTask != null) oldTask = newTask
        else {
          oldTask = field
          sessionClock = sceneRenderConfiguration.sessionClockProvider()
          field = newTask
          if (field == null) sceneRenderConfiguration.needsInflation.set(true)
        }
      }
      try {
        oldTask?.dispose()
      } catch (t: Throwable) {
        Logger.getInstance(LayoutlibSceneManager::class.java).warn(t)
      }
    }

  @GuardedBy("renderTaskLock")
  internal var sessionClock: SessionClock? = null
    get() = renderTaskLock.withLock { field }
    private set

  /**
   * Executes all callbacks in the current [renderTask] until there are no more callbacks to
   * execute. This method will suspend until all callbacks have been executed, or we reach time
   * limit.
   */
  suspend fun executeAllCallbacks() {
    var hasCallbacks = true
    withTimeoutOrNull(timeMillis = 100L) {
      while (hasCallbacks) {
        val task = renderTask ?: return@withTimeoutOrNull
        val clock = sessionClock ?: return@withTimeoutOrNull
        hasCallbacks = task.executeCallbacks(clock.timeNanos).await().hasMoreCallbacks()
      }
    }
  }

  // TODO(b/335424569): make this field private, or at least its setter
  var renderResult: RenderResult? = null
    get() = renderResultLock.withLock { field }
    set(newResult) {
      val oldResult: RenderResult?
      renderResultLock.withLock {
        if (field === newResult) return
        // If renderer is inactive or disposed, any new result should be immediately disposed
        if (isDisposedOrDeactivated() && newResult != null) oldResult = newResult
        else {
          oldResult = field
          field =
            if (
              sceneRenderConfiguration.cacheSuccessfulRenderImage &&
                newResult.isErrorResult() &&
                oldResult.containsValidImage()
            ) {
              // newResult can not be null if isErrorResult is true
              // oldResult can not be null if containsValidImage is true
              newResult!!.copyWithNewImageAndRootViewDimensions(
                StudioRenderService.getInstance(newResult.project)
                  .sharedImagePool
                  .copyOf(oldResult!!.getRenderedImage().copy),
                oldResult.rootViewDimensions,
              )
            } else newResult
        }
      }
      oldResult?.dispose()
    }

  /**
   * This channel will receive all render requests but will keep at most one in its queue, dropping
   * old requests waiting to be processed when a newer comes in.
   */
  private val requestsChannel = Channel<RenderRequest>(capacity = Channel.CONFLATED)

  /**
   * This flow contains the [RenderRequest.requestTime] of the latest request that has finished to
   * be processed, regardless of the result.
   */
  private val lastProcessedRenderRequestTime = MutableStateFlow<Long>(-1)

  init {
    // Order here is relevant. We need to register first to ensure that the scope does not get
    // leaked
    // if this class is disposed before the scope is correctly initialized.
    Disposer.register(parentDisposable, this)
    scope = this.createCoroutineScope()
    scope.launch {
      requestsChannel.receiveAsFlow().collect {
        renderIsRunning.set(true)
        try {
          if (isActive.get()) {
            val doubleRender = sceneRenderConfiguration.doubleRender.getAndSet(false)
            val executeCallbacks =
              sceneRenderConfiguration.executeCallbacksAfterRender.getAndSet(false)
            doRender(it, executeCallbacks)
            if (doubleRender) {
              doRender(it, executeCallbacks)
            }
          } else {
            log.info("Render skipped due to deactivated LayoutlibSceneRenderer (model = $model)")
          }
        } catch (t: CancellationException) {
          log.debug(t)
        } catch (t: Throwable) {
          log.warn(t)
        } finally {
          lastProcessedRenderRequestTime.tryEmit(
            maxOf(lastProcessedRenderRequestTime.value, it.requestTime)
          )
          renderIsRunning.set(false)
        }
      }
    }
  }

  /**
   * Adds a new [RenderRequest] to the queue and returns immediately.
   *
   * Note that it's not guaranteed that this specific request will be executed, as it could be
   * replaced by a newer request that arrives later, before this one starts to execute.
   *
   * @param trigger reason that triggered the render, used for tracking purposes
   */
  fun requestRender(trigger: LayoutEditorRenderResult.Trigger?) {
    scope.launch { enqueueRenderRequest(trigger) }
  }

  /**
   * Adds a new [RenderRequest] to the queue and waits for it or some newer request to complete.
   *
   * Note that it's not guaranteed that this specific request will be executed, as it could be
   * replaced by a newer request that arrives later, before this one starts to execute.
   *
   * @param trigger reason that triggered the render, used for tracking purposes
   */
  @RequiresBackgroundThread
  suspend fun requestRenderAndWait(trigger: LayoutEditorRenderResult.Trigger?) {
    val request = enqueueRenderRequest(trigger) ?: return
    // Suspends until this or any newer request is processed
    lastProcessedRenderRequestTime.first { it >= request.requestTime }
  }

  /**
   * Creates a new [RenderRequest] request, adds it to the queue and returns it.
   *
   * Note: this method will return `null` if the renderer has been disposed.
   */
  private suspend fun enqueueRenderRequest(
    trigger: LayoutEditorRenderResult.Trigger?
  ): RenderRequest? {
    // TODO(b/405340706): remove once the root cause of the early facet disposal is fixed
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager::class.java)
        .warn("requesting render after LayoutlibSceneManager has been disposed")
      return null
    }
    // Mutex is needed to guarantee that requests are sent to the channel in order of their
    // requestTime
    return requestsLock.withLock { RenderRequest(trigger).also { requestsChannel.send(it) } }
  }

  /**
   * Render the model and update the view hierarchy.
   *
   * This method will also [inflate] the model when forced or needed (i.e. when
   * [LayoutlibSceneRenderConfiguration.needsInflation] is true or when [renderTask] is null).
   */
  @RequiresBackgroundThread
  private suspend fun doRender(request: RenderRequest, executeCallbacks: Boolean) {
    var result: RenderResult? = null
    val renderStartTimeMs = System.currentTimeMillis()

    // Wrapping into blockingContext because model.file requires read action
    val file = model.file
    try {
      // Inflate only if needed
      if (renderTask == null && !sceneRenderConfiguration.needsInflation.get()) {
        log.warn(
          "Configuration indicates that inflation is not needed, but renderTask is null, reinflating anyway"
        )
      }
      val inflateResult =
        if (sceneRenderConfiguration.needsInflation.getAndSet(false) || renderTask == null)
          inflate()
        else null
      if (inflateResult?.renderResult?.isSuccess == false) {
        surface.updateErrorDisplay()
        return
      }
      renderTask?.let {
        if (sceneRenderConfiguration.elapsedFrameTimeMs != -1L) {
          it.setElapsedFrameTimeNanos(
            TimeUnit.MILLISECONDS.toNanos(sceneRenderConfiguration.elapsedFrameTimeMs)
          )
        }

        if (sceneRenderConfiguration.clearOverrideRenderSize) {
          it.clearOverrideRenderSize()
          // We reset it here because we only want to clear it once per render
          sceneRenderConfiguration.clearOverrideRenderSize = false
        }
        // Make sure that the task's quality is up-to-date before rendering
        val quality = sceneRenderConfiguration.quality
        it.setQuality(quality)
        result = it.render().await() // await is the suspendable version of join
        if (result?.renderResult?.isSuccess == true) {
          lastRenderQuality = quality
          // After the first successful render of a render task, layout validation doesn't need to
          // be executed again for this render task.
          renderTask?.setEnableLayoutScanner(false)
          // When the layout was inflated in this same call, we do not have to update the hierarchy
          // again
          if (inflateResult == null) updateHierarchy(result)
          // After a render, execute callbacks if indicated, to be ready for the next render.
          if (executeCallbacks) executeAllCallbacks()
        }
      }
    } catch (throwable: Throwable) {
      if (!model.isDisposed) {
        val renderService = StudioRenderService.getInstance(model.project)
        val logger =
          if (sceneRenderConfiguration.logRenderErrors)
            renderService.createHtmlLogger(model.project)
          else renderService.nopLogger

        result =
          createRenderTaskErrorResult(
            file,
            throwable = (throwable as? CompletionException)?.cause ?: throwable,
            logger = logger,
          )
      }
      throw throwable
    } finally {
      result?.let { renderResult = result }

      // Notify surface and track metrics async
      val renderTimeMs = System.currentTimeMillis() - renderStartTimeMs
      executeOnPooledThread {
        surface.modelRendered()
        result?.let {
          // In an unlikely event when result is disposed we can still safely request the size of
          // the image
          NlDiagnosticsManager.getWriteInstance(surface)
            .recordRender(renderTimeMs, it.renderedImage.width * it.renderedImage.height * 4L)
          CommonUsageTracker.getInstance(surface)
            .logRenderResult(request.trigger, it, CommonUsageTracker.RenderResultType.RENDER)
        }
      }
    }
  }

  /**
   * Creates a new [RenderTask] using the [renderTaskFactory] and uses it to inflate the model and
   * update the view hierarchy.
   *
   * Returns the [RenderResult] of the inflate operation, which might be an error result, or null if
   * the model could not be inflated (e.g. because the project was disposed).
   *
   * It throws a [CancellationException] if cancelled midway.
   */
  @RequiresBackgroundThread
  private suspend fun inflate(): RenderResult? {
    val project: Project = model.project
    if (project.isDisposed || isDisposed.get()) {
      return null
    }

    // Some types of files must be saved to disk first, because layoutlib doesn't
    // delegate XML parsers for non-layout files (meaning layoutlib will read the
    // disk contents, so we have to push any edits to disk before rendering)
    // Wrapping into blockingContext because model.file requires read action
    val file = model.file
    file.saveFileIfNecessary()

    // Record the current version we're rendering from; we'll use that in #activate to make sure
    // we're picking up any external changes
    val facet: AndroidFacet = model.facet
    val configuration: Configuration = model.configuration
    val resourceNotificationManager = ResourceNotificationManager.getInstance(project)
    renderedVersion = resourceNotificationManager.getCurrentVersion(facet, file, configuration)

    val renderService = StudioRenderService.getInstance(project)
    val logger =
      if (sceneRenderConfiguration.logRenderErrors) renderService.createHtmlLogger(project)
      else renderService.nopLogger

    var result: RenderResult? = null
    var newTask: RenderTask? = null
    try {
      newTask = sceneRenderConfiguration.createRenderTask(configuration, renderService, logger)
      result = newTask?.let { doInflate(it, logger) } ?: createRenderTaskErrorResult(file, logger)
    } catch (throwable: Throwable) {
      result = createRenderTaskErrorResult(file, throwable, logger)
      if (throwable is CancellationException) {
        // Re-throw any CancellationException to correctly propagate cancellations upward
        throw throwable
      } else {
        // Log but don't re-throw other exceptions, as these exceptions are specific to inflation
        Logger.getInstance(LayoutlibSceneRenderer::class.java).warn(throwable)
      }
    } finally {
      // Make sure not to cancel the post-inflation work needed to keep this renderer in a
      // consistent state
      renderResult = result
      if (result?.renderResult?.isSuccess == true && !isDisposed.get()) {
        renderTask = newTask
        // After inflation, always execute callbacks to be ready for the follow-up render.
        executeAllCallbacks()
        updateHierarchy(result)
        // Do more updates async
        scope.launch {
          model.notifyListenersModelDerivedDataChanged()
          CommonUsageTracker.getInstance(surface)
            .logRenderResult(null, result, CommonUsageTracker.RenderResultType.INFLATE)
        }
      } else {
        // If the result is not successful or the renderer is disposed, then we discard the task.
        newTask?.dispose()
        renderTask = null
      }
    }
    return result
  }

  /**
   * Perform the inflation of [newTask] and wait for its result.
   *
   * Returns the result if successful, or throws an exception if inflation fails or gets cancelled.
   */
  @RequiresBackgroundThread
  private suspend fun doInflate(newTask: RenderTask, logger: RenderLogger): RenderResult {
    newTask.defaultForegroundColor = '#'.toString() + ColorUtil.toHex(UIUtil.getLabelForeground())
    try {
      val result = newTask.inflate().await() // await is the suspendable version of join
      when {
        result == null -> throw IllegalStateException("Inflate returned null RenderResult")
        result.renderResult.exception != null -> throw result.renderResult.exception
        !result.renderResult.isSuccess ->
          throw IllegalStateException(
            "Inflate returned unsuccessful RenderResult without an internal exception"
          )
      }
      return result
    } catch (throwable: Throwable) {
      // Do not ignore ClassNotFoundException on inflate
      if (throwable is ClassNotFoundException) {
        logger.addMessage(
          RenderProblem.createHtml(
            ProblemSeverity.ERROR,
            "Error inflating the preview",
            logger.linkManager,
            throwable,
            ShowFixFactory,
          )
        )
      } else {
        logger.error(ILayoutLog.TAG_INFLATE, "Error inflating the preview", throwable, null, null)
      }
      throw throwable
    }
  }

  private fun RenderResult?.containsValidImage() =
    this?.renderedImage?.let { it.width > 1 && it.height > 1 } ?: false

  // TODO(b/335424569): make this method private
  /** Updates the hierarchy based on the [model]'s render/inflate result. */
  fun updateHierarchy(result: RenderResult?) {
    try {
      updateHierarchyLock.withLock {
        if (result == null || !result.renderResult.isSuccess) {
          NlModelHierarchyUpdater.updateHierarchy(emptyList<ViewInfo>(), model)
        } else {
          NlModelHierarchyUpdater.updateHierarchy(result, model)
        }
      }
    } catch (ignored: InterruptedException) {}
  }

  fun activate() {
    isActive.set(true)
  }

  fun deactivate() {
    if (isActive.getAndSet(false)) {
      renderTask = null
      renderResult = null
    }
  }

  /**
   * Returns true when there are running or pending render requests and the renderer is not
   * currently deactivated.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun isRendering() = isActive.get() && (!requestsChannel.isEmpty || renderIsRunning.get())

  override fun dispose() {
    if (isDisposed.getAndSet(true)) return
    requestsChannel.close()
    if (ApplicationManager.getApplication().isReadAccessAllowed) {
      // dispose is called by the project close using the read lock. Invoke the render task dispose
      // later without the lock.
      disposeExecutor.execute(::deactivate)
    } else deactivate()
  }

  private fun isDisposedOrDeactivated() = isDisposed.get() || !isActive.get()
}

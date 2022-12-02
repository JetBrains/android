/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.AndroidXConstants
import com.android.ide.common.rendering.api.RenderSession
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.compose.COMPOSE_VIEW_ADAPTER_FQN
import com.intellij.openapi.diagnostic.Logger
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Arrays
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/**
 * We run user code and code of 3rd party libraries when rendering user previews. Many 3rd party libraries have global static variables
 * that retain parts of the user code, that in turn does not allow us to free the resources in time and produces memory leaks.
 *
 * This file contains a collection of functions that allow for cleaning [RenderSession] and related [LayoutlibCallbackImpl] (essentially
 * used as a [ClassLoader]) when it is safe to do so.
 */

private val LOG = Logger.getInstance("RenderSessionDisposer")

private const val SNAPSHOT_KT_FQN = "androidx.compose.runtime.snapshots.SnapshotKt"
private const val FONT_REQUEST_WORKER_FQN = "androidx.core.provider.FontRequestWorker"
private const val WINDOW_RECOMPOSER_ANDROID_KT_FQN = "androidx.compose.ui.platform.WindowRecomposer_androidKt"

private const val GAP_WORKER_CLASS_NAME = "androidx.recyclerview.widget.GapWorker"

/**
 * Initiates a custom [RenderSession] disposal, involving clearing several static collections including some Compose-related objects as well
 * as executing default [RenderSession.dispose].
 *
 * Returns a [CompletableFuture] that completes when the custom disposal process finishes.
 */
fun RenderSession.dispose(classLoader: LayoutlibCallbackImpl): CompletableFuture<Void> {
  var disposeMethod = Optional.empty<Method>()
  val applyObserversRef = AtomicReference<WeakReference<MutableCollection<*>?>?>(null)
  if (classLoader.hasLoadedClass(COMPOSE_VIEW_ADAPTER_FQN)) {
    try {
      val composeViewAdapter: Class<*> = classLoader.findClass(COMPOSE_VIEW_ADAPTER_FQN)
      // Kotlin bytecode generation converts dispose() method into dispose$ui_tooling() therefore we have to perform this filtering
      disposeMethod = Arrays.stream(composeViewAdapter.methods).filter { m: Method ->
        m.name.contains("dispose")
      }.findFirst()
    }
    catch (ex: ClassNotFoundException) {
      LOG.debug("$COMPOSE_VIEW_ADAPTER_FQN class not found", ex)
    }
    if (disposeMethod.isEmpty) {
      LOG.warn("Unable to find dispose method in ComposeViewAdapter")
    }
    try {
      val windowRecomposer: Class<*> = classLoader.findClass(WINDOW_RECOMPOSER_ANDROID_KT_FQN)
      val animationScaleField = windowRecomposer.getDeclaredField("animationScale")
      animationScaleField.isAccessible = true
      val animationScale = animationScaleField[windowRecomposer]
      if (animationScale is Map<*, *>) {
        (animationScale as MutableMap<*, *>).clear()
      }
    }
    catch (ex: ReflectiveOperationException) {
      // If the WindowRecomposer does not exist or the animationScale does not exist anymore, ignore.
      LOG.debug("Unable to dispose the recompose animationScale", ex)
    }
    applyObserversRef.set(WeakReference(findApplyObservers(classLoader)))
  }

  try {
    val fontRequestWorker: Class<*> = classLoader.findClass(FONT_REQUEST_WORKER_FQN)
    val pendingRepliesField = fontRequestWorker.getDeclaredField("PENDING_REPLIES")
    pendingRepliesField.isAccessible = true
    val pendingReplies = pendingRepliesField[fontRequestWorker]
    // Clear the SimpleArrayMap
    pendingReplies.javaClass.getMethod("clear").invoke(pendingReplies)
  }
  catch (ex: ReflectiveOperationException) {
    // If the FontRequestWorker does not exist or the PENDING_REPLIES does not exist anymore, ignore.
    LOG.debug("Unable to dispose the PENDING_REPLIES", ex)
  }

  disposeMethod.ifPresent { m: Method -> m.isAccessible = true }
  val finalDisposeMethod = disposeMethod
  return RenderService.getRenderAsyncActionExecutor().runAsyncAction(RenderAsyncActionExecutor.RenderingPriority.HIGH) {
    finalDisposeMethod.ifPresent { m: Method? ->
      this@dispose.execute(
        Runnable {
          this@dispose.rootViews.forEach(
            Consumer { v: ViewInfo? ->
              disposeIfCompose(v!!, m!!)
            })
        }
      )
    }
    val weakApplyObservers = applyObserversRef.get()
    if (weakApplyObservers != null) {
      val applyObservers = weakApplyObservers.get()
      applyObservers?.clear()
    }
    this@dispose.dispose()
  }
}

/**
 * Performs dispose() call against View object associated with [ViewInfo] if that object is an instance of [ComposeViewAdapter]
 *
 * @param viewInfo      a [ViewInfo] associated with the View object to be potentially disposed of
 * @param disposeMethod a dispose method to be executed against View object
 */
private fun disposeIfCompose(viewInfo: ViewInfo, disposeMethod: Method) {
  val viewObject: Any? = viewInfo.viewObject
  if (viewObject?.javaClass?.name != COMPOSE_VIEW_ADAPTER_FQN) {
    return
  }
  try {
    disposeMethod.invoke(viewObject)
  }
  catch (ex: IllegalAccessException) {
    LOG.warn("Unexpected error while disposing compose view", ex)
  }
  catch (ex: InvocationTargetException) {
    LOG.warn("Unexpected error while disposing compose view", ex)
  }
}

private fun findApplyObservers(classLoader: LayoutlibCallbackImpl): MutableCollection<*>? {
  try {
    val snapshotKt = classLoader.findClass(SNAPSHOT_KT_FQN)
    val applyObserversField = snapshotKt.getDeclaredField("applyObservers")
    applyObserversField.isAccessible = true
    val applyObservers = applyObserversField[null]
    if (applyObservers is MutableCollection<*>) {
      return applyObservers
    }
    LOG.warn("SnapshotsKt.applyObservers found but it is not a List")
  }
  catch (ex: ReflectiveOperationException) {
    LOG.warn("Unable to find SnapshotsKt.applyObservers", ex)
  }
  return null
}

/**
 * Clear static gap worker variable used by Recycler View.
 */
fun clearGapWorkerCache(classLoader: LayoutlibCallbackImpl) {
  if (!classLoader.hasLoadedClass(AndroidXConstants.RECYCLER_VIEW.newName()) &&
      !classLoader.hasLoadedClass(AndroidXConstants.RECYCLER_VIEW.oldName())) {
    // If RecyclerView has not been loaded, we do not need to care about the GapWorker cache
    return
  }

  try {
    val gapWorkerClass = classLoader.findClass(GAP_WORKER_CLASS_NAME)
    val gapWorkerField = gapWorkerClass.getDeclaredField("sGapWorker")
    gapWorkerField.isAccessible = true

    // Because we are clearing-up a ThreadLocal, the code must run on the Layoutlib Thread
    RenderService.getRenderAsyncActionExecutor().runAsyncAction(RenderAsyncActionExecutor.RenderingPriority.HIGH) {
      try {
        val gapWorkerFieldValue = gapWorkerField[null] as ThreadLocal<*>
        gapWorkerFieldValue.set(null)
        LOG.debug("GapWorker was cleared")
      }
      catch (e: IllegalAccessException) {
        LOG.debug(e)
      }
    }
  }
  catch (t: Throwable) {
    LOG.debug(t)
  }
}
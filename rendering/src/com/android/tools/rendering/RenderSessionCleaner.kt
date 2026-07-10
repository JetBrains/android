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
@file:JvmName("RenderSessionCleaner")

package com.android.tools.rendering

import com.android.AndroidXConstants
import com.android.SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER
import com.android.ide.common.rendering.api.RenderSession
import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.rendering.classloading.ModuleClassLoader
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.rendering.compose.RECOMPOSER_CLASS
import com.intellij.openapi.diagnostic.Logger
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Arrays
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.function.Consumer

/**
 * We run user code and code of 3rd party libraries when rendering user previews. Many 3rd party libraries have global static variables that
 * retain parts of the user code, that in turn does not allow us to free the resources in time and produces memory leaks.
 *
 * This file contains a collection of functions that allow for cleaning [RenderSession] and related [LayoutlibCallbackImpl] (essentially
 * used as a [ClassLoader]) when it is safe to do so.
 */
private val LOG = Logger.getInstance("RenderSessionDisposer")

private const val SNAPSHOT_KT_FQN = "androidx.compose.runtime.snapshots.SnapshotKt"
private const val FONT_REQUEST_WORKER_FQN = "androidx.core.provider.FontRequestWorker"
private const val TYPEFACE_COMPAT_FQN = "androidx.core.graphics.TypefaceCompat"
private const val WINDOW_RECOMPOSER_ANDROID_KT_FQN = "androidx.compose.ui.platform.WindowRecomposer_androidKt"
private const val LOCAL_BROADCAST_MANAGER_FQN = "androidx.localbroadcastmanager.content.LocalBroadcastManager"

private val GAP_WORKER_CLASS_NAMES = listOf("androidx.recyclerview.widget.GapWorker", "android.support.v7.widget.GapWorker")

private const val INTERNAL_PACKAGE = "_layoutlib_._internal_."
private const val ANDROID_UI_DISPATCHER_FQN = "androidx.compose.ui.platform.AndroidUiDispatcher"
private const val ANDROID_UI_DISPATCHER_COMPANION_FQN = "$ANDROID_UI_DISPATCHER_FQN\$Companion"
private const val COMBINED_CONTEXT_FQN = "${INTERNAL_PACKAGE}kotlin.coroutines.CombinedContext"
private const val ANDROID_COMPOSE_VIEW_FQN = "androidx.compose.ui.platform.AndroidComposeView"

/**
 * Initiates a custom [RenderSession] disposal, involving clearing several static collections including some Compose-related objects as well
 * as executing default [RenderSession.dispose].
 *
 * Returns a [CompletableFuture] that completes when the custom disposal process finishes.
 */
fun RenderSession.dispose(classLoader: ModuleClassLoader): CompletableFuture<Void?> {
  var disposeMethod = Optional.empty<Method>()
  var applyObserversRef: WeakReference<MutableCollection<*>?>? = null
  var globalWriteObserversRef: WeakReference<MutableCollection<*>?>? = null
  var toRunTrampolinedRef: WeakReference<MutableCollection<*>?>? = null
  // After render clean-up. Dispose the FontRequestWorker cache and TypefaceCompat caches for all projects.
  clearFontRequestWorker(classLoader)
  clearTypefaceCompatCache(classLoader)
  clearAndroidComposeView(classLoader)

  if (classLoader.hasLoadedClass(CLASS_COMPOSE_VIEW_ADAPTER)) {
    clearCompositions(classLoader)
    try {
      val composeViewAdapter: Class<*> = classLoader.loadClass(CLASS_COMPOSE_VIEW_ADAPTER)
      // Kotlin bytecode generation converts dispose() method into dispose$ui_tooling() therefore we
      // have to perform this filtering
      disposeMethod = Arrays.stream(composeViewAdapter.methods).filter { m: Method -> m.name.contains("dispose") }.findFirst()
    } catch (ex: ClassNotFoundException) {
      LOG.debug("$CLASS_COMPOSE_VIEW_ADAPTER class not found", ex)
    }
    if (disposeMethod.isEmpty) {
      LOG.warn("Unable to find dispose method in ComposeViewAdapter")
    }
    try {
      val windowRecomposer: Class<*> = classLoader.loadClass(WINDOW_RECOMPOSER_ANDROID_KT_FQN)
      val animationScaleField = windowRecomposer.getDeclaredField("animationScale")
      animationScaleField.isAccessible = true
      val animationScale = animationScaleField[windowRecomposer]
      if (animationScale is Map<*, *>) {
        (animationScale as MutableMap<*, *>).clear()
      }
    } catch (ex: ReflectiveOperationException) {
      // If the WindowRecomposer does not exist or the animationScale does not exist anymore,
      // ignore.
      LOG.debug("Unable to dispose the recompose animationScale", ex)
    }
    applyObserversRef = WeakReference(findSnapshotKtObserversField(classLoader, "applyObservers"))
    globalWriteObserversRef = WeakReference(findSnapshotKtObserversField(classLoader, "globalWriteObservers"))
    toRunTrampolinedRef = WeakReference(findToRunTrampolined(classLoader))

    // Run an early clean-up of the snapshot and global write observers. These hold a lot of
    // information and can cause memory pressure if the render queue is slow to process events.
    runCatching { applyObserversRef.get()?.clear() }
    runCatching { globalWriteObserversRef.get()?.clear() }
    runCatching { toRunTrampolinedRef.get()?.clear() }
  }

  val broadcastManagerInstanceField = WeakReference(findLocalBroadcastManagerInstance(classLoader))

  disposeMethod.ifPresent { m: Method -> m.isAccessible = true }
  val finalDisposeMethod = disposeMethod
  return RenderService.getRenderAsyncActionExecutor().runAsyncAction(RenderAsyncActionExecutor.RenderingTopic.CLEAN) {
    finalDisposeMethod.ifPresent { m: Method? ->
      this@dispose.execute { this@dispose.rootViews.forEach(Consumer { v: ViewInfo? -> disposeIfCompose(v!!, m!!) }) }
    }
    applyObserversRef?.get()?.clear()
    globalWriteObserversRef?.get()?.clear()
    toRunTrampolinedRef?.get()?.clear()
    broadcastManagerInstanceField.get()?.set(null, null)
    this@dispose.dispose()
    clearGapWorkerCache(classLoader)
  }
}

/**
 * Performs dispose() call against View object associated with [ViewInfo] if that object is an instance of [ComposeViewAdapter]
 *
 * @param viewInfo a [ViewInfo] associated with the View object to be potentially disposed of
 * @param disposeMethod a dispose method to be executed against View object
 */
private fun disposeIfCompose(viewInfo: ViewInfo, disposeMethod: Method) {
  val viewObject: Any? = viewInfo.viewObject
  if (viewObject?.javaClass?.name != CLASS_COMPOSE_VIEW_ADAPTER) {
    return
  }
  try {
    disposeMethod.invoke(viewObject)
  } catch (ex: IllegalAccessException) {
    LOG.warn("Unexpected error while disposing compose view", ex)
  } catch (ex: InvocationTargetException) {
    LOG.warn("Unexpected error while disposing compose view", ex)
  }
}

private fun findToRunTrampolined(classLoader: ModuleClassLoader): MutableCollection<*>? {
  try {
    // For some unknown reason sometimes we end up in a situation where ComposeViewAdapter is loaded
    // but AndroidUiDispatcher.Main is not. This seemed infeasible. However, when it happens this
    // function is called, and we assume that we are in the Compose preview, but because
    // AndroidUiDispatcher.Main has never been called, its lazy evaluation is called for the very
    // first time from the non-UI thread and end-up being stuck in runBlocking(Dispatchers.Main).
    if (!classLoader.hasLoadedClass(ANDROID_UI_DISPATCHER_FQN)) {
      LOG.warn("Unexpected: $CLASS_COMPOSE_VIEW_ADAPTER is loaded and $ANDROID_UI_DISPATCHER_FQN is not")
      return null
    }
    val uiDispatcher = classLoader.loadClass(ANDROID_UI_DISPATCHER_FQN)
    if (!classLoader.hasLoadedClass(ANDROID_UI_DISPATCHER_COMPANION_FQN)) {
      LOG.warn("Unexpected: $ANDROID_UI_DISPATCHER_FQN is loaded and $ANDROID_UI_DISPATCHER_COMPANION_FQN is not")
      return null
    }
    // This is very hacky, but it might allow us to avoid calling getMain when it has never been
    // called before.
    val ANDROID_UI_DISPATCHER_COMPANION_VALUE_FQN = "$ANDROID_UI_DISPATCHER_COMPANION_FQN\$Main\$2"
    if (classLoader.hasLoadedClass(ANDROID_UI_DISPATCHER_COMPANION_VALUE_FQN)) {
      val uiDispatcherCompanionValue = classLoader.loadClass(ANDROID_UI_DISPATCHER_COMPANION_VALUE_FQN)
      try {
        val instanceField = uiDispatcherCompanionValue.getField("INSTANCE")
        if (instanceField[null] == null) {
          LOG.warn("Unexpected: uninitialized AndroidUiDispatcher.Main")
        }
      } catch (ignore: ReflectiveOperationException) {}
    }
    val uiDispatcherCompanion = classLoader.loadClass(ANDROID_UI_DISPATCHER_COMPANION_FQN)
    val uiDispatcherCompanionField = uiDispatcher.getDeclaredField("Companion")
    val uiDispatcherCompanionObj = uiDispatcherCompanionField[null]
    val getMainMethod = uiDispatcherCompanion.getDeclaredMethod("getMain").apply { isAccessible = true }
    val mainObj = getMainMethod.invoke(uiDispatcherCompanionObj)
    val combinedContext =
      try {
        classLoader.loadClass(COMBINED_CONTEXT_FQN)
      } catch (e: ClassNotFoundException) {
        // Standalone CLI tool doesn't repackage kotlin packages.
        classLoader.loadClass(COMBINED_CONTEXT_FQN.removePrefix(INTERNAL_PACKAGE))
      }
    val elementField = combinedContext.getDeclaredField("element").apply { isAccessible = true }
    val uiDispatcherObj = elementField[mainObj]

    val toRunTrampolinedField = uiDispatcher.getDeclaredField("toRunTrampolined").apply { isAccessible = true }
    val toRunTrampolinedObj = toRunTrampolinedField[uiDispatcherObj]
    if (toRunTrampolinedObj is MutableCollection<*>) {
      return toRunTrampolinedObj
    }
    LOG.warn("AndroidUiDispatcher.toRunTrampolined found but it is not a MutableCollection")
  } catch (ex: ReflectiveOperationException) {
    LOG.warn("Unable to find AndroidUiDispatcher.toRunTrampolined", ex)
  }
  return null
}

private fun findSnapshotKtObserversField(classLoader: ModuleClassLoader, fieldName: String): MutableCollection<*>? {
  try {
    val snapshotKt = classLoader.loadClass(SNAPSHOT_KT_FQN)
    val observersField = snapshotKt.getDeclaredField(fieldName)
    observersField.isAccessible = true
    val applyObservers = observersField[null]
    if (applyObservers is MutableCollection<*>) {
      return applyObservers
    }
    LOG.warn("SnapshotsKt.$fieldName found but it is not a Collection")
  } catch (ex: ReflectiveOperationException) {
    LOG.warn("Unable to find SnapshotsKt.$fieldName", ex)
  }
  return null
}

private fun findLocalBroadcastManagerInstance(classLoader: ModuleClassLoader): Field? {
  if (!classLoader.hasLoadedClass(LOCAL_BROADCAST_MANAGER_FQN)) return null

  return try {
    val broadcastManagerClass: Class<*> = classLoader.loadClass(LOCAL_BROADCAST_MANAGER_FQN)
    broadcastManagerClass.getDeclaredField("mInstance").apply { this.isAccessible = true }
  } catch (ex: ReflectiveOperationException) {
    LOG.debug("Unable to find $LOCAL_BROADCAST_MANAGER_FQN.mInstance", ex)
    null
  }
}

private fun clearFontRequestWorker(classLoader: ModuleClassLoader) {
  if (!classLoader.hasLoadedClass(FONT_REQUEST_WORKER_FQN)) return

  try {
    val fontRequestWorker: Class<*> = classLoader.loadClass(FONT_REQUEST_WORKER_FQN)

    // Safety check to ensure we only modify classes loaded by the ModuleClassLoader itself
    if (fontRequestWorker.classLoader !== classLoader) {
      LOG.debug("FontRequestWorker loaded by parent classloader, skipping clean-up to avoid affecting IDE state")
      return
    }

    val pendingRepliesField = fontRequestWorker.getDeclaredField("PENDING_REPLIES")
    pendingRepliesField.isAccessible = true
    val pendingReplies = pendingRepliesField[null]
    if (pendingReplies != null) {
      // Clear the SimpleArrayMap
      try {
        pendingReplies.javaClass.getMethod("clear").invoke(pendingReplies)
      } catch (e: Exception) {
        LOG.debug("Failed to clear PENDING_REPLIES map", e)
      }
    }

    // Clear Typeface cache
    try {
      val resetMethod = fontRequestWorker.getDeclaredMethod("resetTypefaceCache")
      resetMethod.isAccessible = true
      resetMethod.invoke(null)
    } catch (e: Exception) {
      LOG.debug("Failed to reset Typeface cache", e)
    }

    // Shut down the executor service to cancel hanging tasks
    try {
      val executorField = fontRequestWorker.getDeclaredField("DEFAULT_EXECUTOR_SERVICE")
      executorField.isAccessible = true
      val executor = executorField[null] as? ExecutorService
      executor?.shutdownNow()
    } catch (e: Exception) {
      LOG.debug("Failed to shutdown FontRequestWorker executor", e)
    }
  } catch (ex: ReflectiveOperationException) {
    LOG.debug("Unable to dispose the FontRequestWorker", ex)
  }
}

/** Clear static gap worker variable used by Recycler View. */
private fun clearGapWorkerCache(classLoader: ModuleClassLoader) {
  if (
    !classLoader.hasLoadedClass(AndroidXConstants.RECYCLER_VIEW.newName()) &&
      !classLoader.hasLoadedClass(AndroidXConstants.RECYCLER_VIEW.oldName())
  ) {
    // If RecyclerView has not been loaded, we do not need to care about the GapWorker cache
    return
  }

  for (className in GAP_WORKER_CLASS_NAMES) {
    try {
      val gapWorkerClass = classLoader.loadClass(className)
      if (gapWorkerClass.classLoader !is DelegatingClassLoader) {
        LOG.debug("GapWorker loaded by IDE/system classloader, skipping clean-up")
        continue
      }
      val gapWorkerField = gapWorkerClass.getDeclaredField("sGapWorker")
      gapWorkerField.isAccessible = true

      val gapWorkerFieldValue = gapWorkerField[null] as? ThreadLocal<*>
      gapWorkerFieldValue?.set(null)
      LOG.debug("GapWorker was cleared")
    } catch (t: Throwable) {
      LOG.debug(t)
    }
  }
}

/** Clear any pending re-compositions */
private fun clearCompositions(classLoader: ModuleClassLoader) {
  if (!classLoader.hasLoadedClass(RECOMPOSER_CLASS)) return

  try {
    val recomposerClass = classLoader.loadClass(RECOMPOSER_CLASS)
    val runningRecomposers = recomposerClass.getDeclaredField("_runningRecomposers").apply { isAccessible = true }.get(null)
    val currentRunningSet =
      runningRecomposers::class.java.getMethod("getValue").apply { isAccessible = true }.invoke(runningRecomposers) as Set<*>
    if (currentRunningSet.isNotEmpty()) {
      val recomposerCompanion = recomposerClass.getField("Companion").get(null)
      val recomposerCompanionClass = classLoader.loadClass("androidx.compose.runtime.Recomposer\$Companion")
      val removeRunning = recomposerCompanionClass.methods.single { it.name.contains("removeRunning") }
      currentRunningSet.forEach { removeRunning.invoke(null, recomposerCompanion, it) }
    }
  } catch (t: Throwable) {
    LOG.debug(t)
  }
}

/** Clear static Typeface cache in TypefaceCompat (androidx.core.graphics.TypefaceCompat.sTypefaceCache). */
private fun clearTypefaceCompatCache(classLoader: ModuleClassLoader) {
  if (!classLoader.hasLoadedClass(TYPEFACE_COMPAT_FQN)) return

  try {
    val typefaceCompatClass = classLoader.loadClass(TYPEFACE_COMPAT_FQN)

    // Safety check to ensure we only modify classes loaded by the ModuleClassLoader itself
    if (typefaceCompatClass.classLoader !== classLoader) {
      LOG.debug("TypefaceCompat loaded by parent classloader, skipping clean-up")
      return
    }

    val clearCacheMethod = typefaceCompatClass.getDeclaredMethod("clearCache")
    clearCacheMethod.isAccessible = true
    clearCacheMethod.invoke(null)
  } catch (ex: ReflectiveOperationException) {
    LOG.debug("Unable to dispose TypefaceCompat.sTypefaceCache", ex)
  }
}

/** Clear static variables or caches held by `AndroidComposeView`. */
private fun clearAndroidComposeView(classLoader: ModuleClassLoader) {
  if (!classLoader.hasLoadedClass(ANDROID_COMPOSE_VIEW_FQN)) return

  try {
    val androidComposeViewClass = classLoader.loadClass(ANDROID_COMPOSE_VIEW_FQN)

    // Safety check to ensure we only modify classes loaded by the ModuleClassLoader itself
    if (androidComposeViewClass.classLoader !== classLoader) {
      LOG.debug("AndroidComposeView loaded by parent classloader, skipping clean-up")
      return
    }

    clearStaticFields(androidComposeViewClass)

    val companionFqn = "$ANDROID_COMPOSE_VIEW_FQN\$Companion"
    if (classLoader.hasLoadedClass(companionFqn)) {
      val companionClass = classLoader.loadClass(companionFqn)
      clearStaticFields(companionClass)
    }
  } catch (ex: ReflectiveOperationException) {
    LOG.debug("Unable to dispose AndroidComposeView static fields", ex)
  }
}

private fun clearStaticFields(clazz: Class<*>) {
  for (field in clazz.declaredFields) {
    if (Modifier.isStatic(field.modifiers)) {
      runCatching {
        field.isAccessible = true
        val value = field[null]
        if (value is MutableCollection<*>) {
          value.clear()
        } else if (value is MutableMap<*, *>) {
          value.clear()
        }
        if (!Modifier.isFinal(field.modifiers)) {
          field.set(null, null)
        }
      }
    }
  }
}

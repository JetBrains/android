/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.model

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.concurrency.ThrottlingAsyncSupplier
import com.android.tools.idea.model.MergedManifestException.MergingError
import com.android.tools.idea.model.MergedManifestException.MissingAttribute
import com.android.tools.idea.model.MergedManifestException.MissingElement
import com.android.tools.idea.model.MergedManifestException.ParsingError
import com.android.tools.idea.model.MergedManifestManager.Companion.LOG
import com.android.tools.idea.stats.ManifestMergerStatsTracker.MergeResult
import com.android.tools.idea.util.androidFacet
import com.android.utils.TraceUtils
import com.android.utils.concurrency.AsyncSupplier
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.RecursionManager
import com.intellij.util.messages.Topic
import org.jetbrains.android.facet.AndroidFacet
import java.time.Duration
import java.util.concurrent.ExecutionException

/**
 * The minimum amount of time between the creation of any two [MergedManifestSnapshot]s
 * for the same module. See [ThrottlingAsyncSupplier].
 */
private val RECOMPUTE_INTERVAL = Duration.ofMillis(50)

/**
 * [MergedManifestSupplier] wraps a [ThrottlingAsyncSupplier] that knows how to calculate and cache
 * [MergedManifestSnapshot]s. It provides additional functionality to avoid deadlocking when legacy
 * callers block on getting the merged manifest while holding the global read/write lock.
 *
 * @see [getOrCreateSnapshotInCallingThread]
 * @see [MergedManifestManager.getSnapshot]
 */
private class MergedManifestSupplier(private val module: Module) : AsyncSupplier<MergedManifestSnapshot>, Disposable, ModificationTracker {

  private val delegate = ThrottlingAsyncSupplier(::getOrCreateSnapshotFromDelegate, ::snapshotUpToDate, RECOMPUTE_INTERVAL)

  private val callingThreadLock = Any()
  /**
   * The cached result of the last merged manifest computation to complete in a calling thread
   * (as opposed to the delegate supplier's background thread). This value is consumed on the delegate
   * supplier's background thread in [getOrCreateSnapshotFromDelegate], which is the only place that
   * it can be set to null.
   */
  @GuardedBy("callingThreadLock")
  private var snapshotFromCallingThread: MergedManifestSnapshot? = null
  /**
   * The future result of a merged manifest computation running in some calling thread
   * (as opposed to the delegate supplier's background thread). When the computation has finished,
   * we guarantee that [snapshotFromCallingThread] has been updated before broadcasting the result
   * via this future.
   *
   * @see [getOrCreateSnapshotInCallingThread]
   */
  @GuardedBy("callingThreadLock")
  private var snapshotBeingComputedInCallingThread: ListenableFuture<MergedManifestSnapshot>? = null

  init {
    Disposer.register(this, delegate)
  }

  override fun dispose() {
  }

  override fun getModificationCount(): Long = delegate.modificationCount

  override val now: MergedManifestSnapshot?
    // getOrCreateSnapshotFromDelegate clears snapshotFromCallingThread before using it,
    // so if the value is not null, it must be more recent than whatever the delegate has cached.
    get() = synchronized(callingThreadLock) { snapshotFromCallingThread } ?: delegate.now

  override fun get(): ListenableFuture<MergedManifestSnapshot> = delegate.get()

  @Slow
  private fun getOrCreateSnapshot(cachedSnapshot: MergedManifestSnapshot?): MergedManifestSnapshot {
    return runCancellableReadAction {
      val facet = module.androidFacet
                  ?: throw IllegalArgumentException("Attempt to obtain manifest info from a non Android module: ${module.name}")
      when {
        // Make sure the module wasn't disposed while we were waiting for the read lock.
        facet.isDisposed || module.project.isDisposed -> throw ProcessCanceledException()
        cachedSnapshot != null && snapshotUpToDate(cachedSnapshot) -> cachedSnapshot
        else -> createMergedManifestSnapshot(facet)
      }
    }
  }

  /** Create a [MergedManifestSnapshot] and record associated telemetry. */
  @Slow
  private fun createMergedManifestSnapshot(facet: AndroidFacet): MergedManifestSnapshot {
    val startMillis = System.currentTimeMillis()
    val token = Object()
    ApplicationManager.getApplication().messageBus.syncPublisher(MergedManifestSnapshotComputeListener.TOPIC)
      .snapshotCreationStarted(token, startMillis)

    var snapshot: MergedManifestSnapshot
    var result = MergeResult.FAILED

    try {
      snapshot = MergedManifestSnapshotFactory.createMergedManifestSnapshot(facet)
      result = MergeResult.SUCCESS
    }
    catch (e: ProcessCanceledException) {
      result = MergeResult.CANCELED
      throw e
    }
    catch (e: MergedManifestException) {
      when (e) {
        is MergingError,
        is MissingElement,
        is ParsingError,
        is MissingAttribute -> {
          LOG.info("Manifest merge attempt failed", e)
          snapshot = MergedManifestSnapshotFactory.createEmptyMergedManifestSnapshot(module, facet, e)
        }
        // skipping InfrastructureError as it is a wrapper for general Exception
        is MergedManifestException.InfrastructureError -> throw e
      }
    }
    finally {
      val endMillis = System.currentTimeMillis()
      ApplicationManager.getApplication().messageBus.syncPublisher(MergedManifestSnapshotComputeListener.TOPIC)
        .snapshotCreationEnded(token, startMillis, endMillis, result)
    }

    return snapshot
  }

  /**
   * Workaround to avoid deadlock for legacy callers who block on merged manifest computation
   * while holding the global read/write lock.
   *
   * @see [MergedManifestManager.getSnapshot]
   */
  @Deprecated(
    message = "Do NOT call this function! It only exists as a workaround to avoid deadlocks when computing the merged manifest."
              + " Use the AsyncSupplier returned by getMergedManifestSupplier() instead.",
    replaceWith = ReplaceWith("get()")
  )
  fun getOrCreateSnapshotInCallingThread(): MergedManifestSnapshot {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val manifestSnapshot = RecursionManager.doPreventingRecursion(module, true, ::doGetOrCreateSnapshotInCallingThread)
    if (manifestSnapshot != null) {
      return manifestSnapshot
    }

    logger<MergedManifestSupplier>().warn(
        "Infinite recursion detected when computing merged manifest for module ${module.name}\n" + TraceUtils.currentStack)
    return MergedManifestSnapshotFactory.createEmptyMergedManifestSnapshot(module, module.androidFacet, null)
  }

  private fun doGetOrCreateSnapshotInCallingThread(): MergedManifestSnapshot {
    while (true) {
      val future = SettableFuture.create<MergedManifestSnapshot>()
      val snapshotBeingComputed = synchronized(callingThreadLock) {
        if (snapshotBeingComputedInCallingThread == null) {
          snapshotBeingComputedInCallingThread = future
        }
        snapshotBeingComputedInCallingThread!!
      }
      if (snapshotBeingComputed === future) {
        // No other calling thread was computing the merged manifest when we acquired
        // the lock, so we need to actually compute it on this thread.
        val cachedSnapshot = now
        val snapshot = try {
          getOrCreateSnapshot(cachedSnapshot)
        }
        catch (t: Throwable) {
          synchronized(callingThreadLock) {
            snapshotBeingComputedInCallingThread = null
          }
          future.setException(t)
          throw t
        }
        synchronized(callingThreadLock) {
          snapshotBeingComputedInCallingThread = null
          snapshotFromCallingThread = snapshot
        }
        future.set(snapshot)
        return snapshot
      }

      // Otherwise, block on the already-executing computation and use the result.
      // It must be up to date because another thread can't invalidate the merged manifest
      // without the write lock, which it can't obtain until this thread gives up its read access.
      try {
        return ProgressIndicatorUtils.awaitWithCheckCanceled(snapshotBeingComputed)
      }
      catch (e: ProcessCanceledException) {
        // Either this thread was cancelled or the snapshotBeingComputed future got cancelled.
        // Check if this thread was cancelled and throw a ProcessCanceledException if so.
        ProgressManager.checkCanceled()
        // This thread is still active - try again.
      }
    }
  }

  /**
   * Note that this function is only ever called on the delegate supplier's single background thread.
   */
  @WorkerThread
  private fun getOrCreateSnapshotFromDelegate(): MergedManifestSnapshot {
    val (snapshot, snapshotBeingComputed) = synchronized(callingThreadLock) {
      val snapshot = snapshotFromCallingThread
      snapshotFromCallingThread = null
      snapshot to snapshotBeingComputedInCallingThread
    }
    if (snapshotBeingComputed == null) {
      // Nobody else is computing the merged manifest right now. If we've cached something
      // from a calling thread and it's up to date, use that. Otherwise, it's time to recompute.
      return getOrCreateSnapshot(snapshot)
    }
    // Wait for the already-running calling thread computation.
    try {
      snapshotBeingComputed.get()
    }
    catch (e: ExecutionException) {
      if (e.cause is ProcessCanceledException) {
        // The thread that was already computing the merged manifest was canceled,
        // but this thread is still active so we should compute it ourselves. Note
        // that we don't try to use the cached snapshot because the fact that there was
        // another thread already computing the merged manifest means that the cached
        // snapshot was stale.
        ProgressManager.checkCanceled()
        return getOrCreateSnapshot(null)
      }
      throw e
    }
    // Once the already-running calling thread computation has finished, consume the result.
    val result = synchronized(callingThreadLock) {
      snapshotFromCallingThread.also {
        snapshotFromCallingThread = null
      }
    }
    return getOrCreateSnapshot(result)
  }

  /**
   * Checks whether the given [snapshot] is up to date. The [delegate] supplier uses this function
   * to determine when to call [getOrCreateSnapshot] to get a fresh snapshot, and [getOrCreateSnapshot]
   * may use it to see if we can reuse a snapshot that's been calculated in the calling thread for some
   * legacy caller.
   */
  @AnyThread
  private fun snapshotUpToDate(snapshot: MergedManifestSnapshot): Boolean {
    // The only way the snapshot's merged manifest info could be null is if the facet
    // is disposed, in which case there's no need to try and recalculate it.
    val mergedManifestInfo = snapshot.mergedManifestInfo ?: return true
    return runCancellableReadAction(mergedManifestInfo::isUpToDate)
  }

  private fun <T> runCancellableReadAction(computable: Computable<T>): T {
    if (ApplicationManager.getApplication().isReadAccessAllowed) {
      return computable.compute()
    }
    return ReadAction.nonBlocking(computable::compute).executeSynchronously()
  }
}

/**
 * Module service responsible for offloading merged manifest computations
 * to a worker thread and maintaining a cache of the resulting [MergedManifestSnapshot].
 *
 * This class is open for mocking. Do not extend it.
 */
class MergedManifestManager(module: Module) : Disposable {
  private val supplier = MergedManifestSupplier(module)
  val mergedManifest: AsyncSupplier<MergedManifestSnapshot> get() = supplier

  init {
    // The Disposer tree doesn't access the fields of the objects
    // that it contains: it uses only reference equality and
    // System#identityHashcode. Therefore we don't have to worry
    // about accessing uninitialized fields when leaking "this".
    @Suppress("LeakingThis")
    Disposer.register(this, supplier)
  }

  override fun dispose() { }

  companion object {
    @JvmStatic
    fun getInstance(module: Module): MergedManifestManager = module.getService(MergedManifestManager::class.java)

    /**
     * Convenience function for requesting a fresh [MergedManifestSnapshot] which, if necessary, will be calculated
     * in a background thread. Callers who can tolerate a potentially stale merged manifest should consider using
     * [getMergedManifestSupplier] instead.
     */
    @JvmStatic
    fun getMergedManifest(module: Module): ListenableFuture<MergedManifestSnapshot> = getMergedManifestSupplier(module).get()

    @JvmStatic
    fun getMergedManifestSupplier(module: Module): AsyncSupplier<MergedManifestSnapshot> = getInstance(module).mergedManifest

    @Deprecated(
      message = "Do NOT call this function! It only exists as a workaround to avoid deadlocks when computing the merged manifest."
                + " Use the AsyncSupplier returned by getMergedManifestSupplier() instead.",
      replaceWith = ReplaceWith("MergedManifestManager.getMergedManifestSupplier(module)")
    )
    @Slow
    @JvmStatic
    fun getFreshSnapshotInCallingThread(module: Module): MergedManifestSnapshot =
        getInstance(module).supplier.getOrCreateSnapshotInCallingThread()

    /**
     * Returns a potentially stale [MergedManifestSnapshot] for the given [AndroidFacet], blocking the calling
     * thread to create one if necessary.
     */
    @Deprecated(
      message = "To avoid blocking the calling thread, use the AsyncSupplier returned by getMergedManifestSupplier() instead.",
      replaceWith = ReplaceWith("MergedManifestManager.getMergedManifestSupplier(facet.module)")
    )
    @Slow
    @JvmStatic
    fun getSnapshot(facet: AndroidFacet): MergedManifestSnapshot = getSnapshot(facet.module)

    /**
     * Returns a potentially stale [MergedManifestSnapshot] for the given [Module], blocking the calling
     * thread to create one if necessary.
     */
    @Deprecated(
      message = "To avoid blocking the calling thread, use the AsyncSupplier returned by getMergedManifestSupplier() instead.",
      replaceWith = ReplaceWith("MergedManifestManager.getMergedManifestSupplier(module)")
    )
    @Slow
    @JvmStatic
    fun getSnapshot(module: Module): MergedManifestSnapshot {
      val supplier = getInstance(module).mergedManifest
      return supplier.now ?: getFreshSnapshot(module)
    }

    /**
     * Returns a fresh [MergedManifestSnapshot] for the given [Module], blocking the calling
     * thread to create one if necessary.
     */
    @Deprecated(
      message = "To avoid blocking the calling thread, asynchronously respond to the future returned by getMergedManifest() instead.",
      replaceWith = ReplaceWith("MergedManifestManager.getMergedManifest(module)")
    )
    @Slow
    @JvmStatic
    fun getFreshSnapshot(module: Module): MergedManifestSnapshot {
      val supplier = getInstance(module).supplier
      return try {
        if (ApplicationManager.getApplication().isReadAccessAllowed) {
          // If we're holding the global write lock, blocking on the delegate supplier's
          // computation would create a deadlock since the worker thread needs the read
          // lock to create a new snapshot. If we're holding the read lock, we can't block
          // on another thread that also requires the read lock, as this would cause deadlock
          // if there's an incoming write request before the second thread acquires the lock.
          supplier.getOrCreateSnapshotInCallingThread()
        }
        else {
          supplier.get().get()
        }
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Exception) {
        MergedManifestSnapshotFactory.createEmptyMergedManifestSnapshot(module, module.androidFacet, e)
      }
    }

    val LOG: Logger = Logger.getInstance(MergedManifestManager::class.java)
  }
}

/** Listener for events related to manifest merge computation. */
interface MergedManifestSnapshotComputeListener {
  companion object {
    val TOPIC = Topic.create(MergedManifestSnapshotComputeListener::class.qualifiedName!!,
                             MergedManifestSnapshotComputeListener::class.java,
                             Topic.BroadcastDirection.TO_CHILDREN)
  }

  /**
   * Invoked when manifest merge computation begins.
   *
   * @param token an arbitrary object representing the computation in progress. This allows the consumer to track a specific computation
   * between calls to the start and end events, since multiple merges may be happening simultaneously. The token passed to each method will
   * be the same object for a given single merge computation.
   */
  fun snapshotCreationStarted(token: Any, startTimestampMillis: Long)

  /**
   * Invoked when manifest merge computation end.
   *
   * @param token an arbitrary object representing the computation that has ended. This allows the consumer to track a specific computation
   * between calls to the start and end events, since multiple merges may be happening simultaneously. The token passed to each method will
   * be the same object for a given single merge computation.
   */
  fun snapshotCreationEnded(token: Any, startTimestampMillis: Long, endTimestampMillis: Long, result: MergeResult)

}

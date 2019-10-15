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
import com.android.tools.idea.util.androidFacet
import com.android.utils.concurrency.AsyncSupplier
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly
import java.lang.RuntimeException
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
private class MergedManifestSupplier(private val facet: AndroidFacet) :
  AsyncSupplier<MergedManifestSnapshot>,
  Disposable,
  ModificationTracker {

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

  var updateCallback: Runnable? = null
    set(value) {
      field = value
      delegate.setUpdateCallback(value)
    }

  init {
    Disposer.register(this, delegate)
  }

  override fun dispose() {
    updateCallback = null
  }

  override fun getModificationCount() = delegate.modificationCount

  override val now: MergedManifestSnapshot?
    // getOrCreateSnapshotFromDelegate clears snapshotFromCallingThread before using it,
    // so if the value is not null, it must be more recent than whatever the delegate has cached.
    get() = synchronized(callingThreadLock) { snapshotFromCallingThread } ?: delegate.now

  override fun get() = delegate.get()

  @Slow
  private fun getOrCreateSnapshot(cachedSnapshot: MergedManifestSnapshot?): MergedManifestSnapshot {
    return runReadAction {
      when {
        // Make sure the module wasn't disposed while we were waiting for the read lock.
        Disposer.isDisposed(facet) -> MergedManifestSnapshotFactory.createEmptyMergedManifestSnapshot(facet.module)
        cachedSnapshot != null && snapshotUpToDate(cachedSnapshot) -> cachedSnapshot
        else -> MergedManifestSnapshotFactory.createMergedManifestSnapshot(facet, MergedManifestInfo.create(facet))
      }
    }
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
    val future = SettableFuture.create<MergedManifestSnapshot>()!!
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
      if (snapshot !== cachedSnapshot) {
        updateCallback?.run()
      }
      return snapshot
    }
    // Otherwise, block on the already-executing computation and use the result.
    // It must be up to date because another thread can't invalidate the merged manifest
    // without the write lock, which it can't obtain until this thread gives up its read access.
    try {
      return snapshotBeingComputed.get()
    }
    catch (e: ExecutionException) {
      if (e.cause is ProcessCanceledException) {
        // The thread that was already computing the merged manifest was canceled,
        // but this thread may still be active. If it is, we should try again.
        ProgressManager.checkCanceled()
        return getOrCreateSnapshotInCallingThread()
      }
      throw e
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
    return runReadAction(mergedManifestInfo::isUpToDate)
  }
}

/**
 * Module service responsible for offloading merged manifest computations
 * to a worker thread and maintaining a cache of the resulting [MergedManifestSnapshot].
 */
open class MergedManifestManager(module: Module) {
  private val supplier: MergedManifestSupplier
  open val mergedManifest: AsyncSupplier<MergedManifestSnapshot> get() = supplier
  open val modificationTracker: ModificationTracker get() = supplier

  init {
    val facet = module.androidFacet
                ?: throw IllegalArgumentException("Attempt to obtain manifest info from a non Android module: ${module.name}")
    supplier = MergedManifestSupplier(facet)
    Disposer.register(facet, supplier)
  }

  companion object {
    @JvmStatic
    fun getInstance(module: Module) = ModuleServiceManager.getService(module, MergedManifestManager::class.java)!!

    /**
     * Registers a [callback] to be executed whenever the [module]'s merged manifest has been recomputed.
     */
    @JvmStatic
    @TestOnly
    fun setUpdateCallback(module: Module, callback: Runnable?) {
      getInstance(module).supplier.updateCallback = callback
    }

    /**
     * Convenience function for requesting a fresh [MergedManifestSnapshot] which, if necessary, will be calculated
     * in a background thread. Callers who can tolerate a potentially stale merged manifest should consider using
     * [getMergedManifestSupplier] instead.
     */
    @JvmStatic
    fun getMergedManifest(module: Module) = getMergedManifestSupplier(module).get()

    @JvmStatic
    fun getMergedManifestSupplier(module: Module) = getInstance(module).mergedManifest

    @JvmStatic
    fun getModificationTracker(module: Module) = getInstance(module).modificationTracker

    @Deprecated(
      message = "Do NOT call this function! It only exists as a workaround to avoid deadlocks when computing the merged manifest."
                + " Use the AsyncSupplier returned by getMergedManifestSupplier() instead.",
      replaceWith = ReplaceWith("MergedManifestManager.getMergedManifestSupplier(module)")
    )
    @JvmStatic
    fun getFreshSnapshotInCallingThread(module: Module) = getInstance(module).supplier.getOrCreateSnapshotInCallingThread()

    @Deprecated(
      message = "Use the AsyncSupplier returned by getMergedManifestSupplier() instead.",
      replaceWith = ReplaceWith("MergedManifestManager.getMergedManifestSupplier(facet.module).now")
    )
    @JvmStatic
    fun getCachedSnapshot(facet: AndroidFacet) = getMergedManifestSupplier(facet.module).now

    /**
     * Returns a potentially stale [MergedManifestSnapshot] for the given [AndroidFacet], blocking the calling
     * thread to create one if necessary.
     */
    @Deprecated(
      message = "To avoid blocking the calling thread, use the AsyncSupplier returned by getMergedManifestSupplier() instead.",
      replaceWith = ReplaceWith("MergedManifestManager.getMergedManifestSupplier(facet.module)")
    )
    @JvmStatic
    fun getSnapshot(facet: AndroidFacet) = getSnapshot(facet.module)

    /**
     * Returns a potentially stale [MergedManifestSnapshot] for the given [Module], blocking the calling
     * thread to create one if necessary.
     */
    @Deprecated(
      message = "To avoid blocking the calling thread, use the AsyncSupplier returned by getMergedManifestSupplier() instead.",
      replaceWith = ReplaceWith("MergedManifestManager.getMergedManifestSupplier(module)")
    )
    @JvmStatic
    fun getSnapshot(module: Module): MergedManifestSnapshot {
      val supplier = getInstance(module).supplier
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
      catch(e: ProcessCanceledException) {
        throw e
      }
      catch(e: Exception) {
        MergedManifestSnapshotFactory.createEmptyMergedManifestSnapshot(module)
      }
    }
  }
}
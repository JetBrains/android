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
import com.android.tools.idea.concurrency.ThrottlingAsyncSupplier
import com.android.tools.idea.util.androidFacet
import com.android.utils.concurrency.AsyncSupplier
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.android.facet.AndroidFacet
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

/**
 * The minimum amount of time between the creation of any two [MergedManifestSnapshot]s
 * for the same module. See [ThrottlingAsyncSupplier].
 */
private val RECOMPUTE_INTERVAL = Duration.ofMillis(50)

private val LOG get() = Logger.getInstance(MergedManifestManager::class.java)

/**
 * [MergedManifestSupplier] wraps a [ThrottlingAsyncSupplier] that knows how to calculate and cache
 * [MergedManifestSnapshot]s. It provides additional functionality to handle legacy callers that block
 * on getting the merged manifest while holding the write lock.
 *
 * @see [getOrCreateSnapshotFromWriteAction]
 * @see [MergedManifestManager.getSnapshot]
 */
private class MergedManifestSupplier(private val facet: AndroidFacet) :
  AsyncSupplier<MergedManifestSnapshot>,
  ModificationTracker {

  private val delegate = ThrottlingAsyncSupplier(::getOrCreateSnapshot, ::snapshotUpToDate, RECOMPUTE_INTERVAL)
  private var snapshotFromWriteAction = AtomicReference<MergedManifestSnapshot?>(null)

  init {
    Disposer.register(facet, delegate)
  }

  override fun getModificationCount() = delegate.modificationCount

  override val now: MergedManifestSnapshot?
    // We always set snapshotFromWriteAction's value to null during the delegate's call to getOrCreateSnapshot(),
    // so if the value is not null, it must be more recent than whatever the delegate has cached.
    get() = snapshotFromWriteAction.get() ?: delegate.now

  override fun get() = delegate.get()

  private fun getOrCreateSnapshot(): MergedManifestSnapshot {
    val cachedSnapshot = snapshotFromWriteAction.getAndSet(null)
    return runReadAction {
      when {
        // Make sure the module wasn't disposed while we were waiting for the read lock.
        Disposer.isDisposed(facet) -> null
        cachedSnapshot != null && snapshotUpToDate(cachedSnapshot) -> cachedSnapshot
        else -> MergedManifestSnapshotFactory.createMergedManifestSnapshot(facet, MergedManifestInfo.create(facet))
      }
    } ?: MergedManifestSnapshotFactory.createEmptyMergedManifestSnapshot(facet.module)
  }

  /**
   * Checks whether the given [snapshot] is up to date. The [delegate] supplier uses this function
   * to determine when to call [getOrCreateSnapshot] to get a fresh snapshot, and [getOrCreateSnapshot]
   * may use it to see if we can reuse a snapshot that's been calculated on the EDT for some legacy caller.
   */
  @AnyThread
  private fun snapshotUpToDate(snapshot: MergedManifestSnapshot): Boolean {
    // The only way the snapshot's merged manifest info could be null is if the facet
    // is disposed, in which case there's no need to try and recalculate it.
    val mergedManifestInfo = snapshot.mergedManifestInfo ?: return true
    return runReadAction(mergedManifestInfo::isUpToDate)
  }

  /**
   * Workaround to avoid deadlock for legacy callers who block on merged manifest computation
   * while holding the write lock.
   *
   * @see [MergedManifestManager.getSnapshot]
   */
  fun getOrCreateSnapshotFromWriteAction(): MergedManifestSnapshot {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    // It's possible that we've already created a snapshot during a previous write action
    // and the result just hasn't been picked up and cleared by the delegate supplier yet.
    // In that case, we don't need to compute the snapshot again.
    return snapshotFromWriteAction.get() ?: getOrCreateSnapshot().also(snapshotFromWriteAction::set)
  }
}

/**
 * Module service responsible for offloading merged manifest computations
 * to a worker thread and maintaining a cache of the resulting [MergedManifestSnapshot].
 */
class MergedManifestManager(module: Module) {
  private val supplier: MergedManifestSupplier
  val mergedManifest: AsyncSupplier<MergedManifestSnapshot> get() = supplier
  val modificationTracker: ModificationTracker get() = supplier

  init {
    val facet = module.androidFacet
                ?: throw IllegalArgumentException("Attempt to obtain manifest info from a non Android module: ${module.name}")
    supplier = MergedManifestSupplier(facet)
  }

  companion object {
    @JvmStatic
    fun getInstance(module: Module) = ModuleServiceManager.getService(module, MergedManifestManager::class.java)!!

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
      val application = ApplicationManager.getApplication()
      if (application.isDispatchThread) {
        LOG.warn("Blocking on merged manifest computation on the EDT causes UI freezes!"
                 + " (isWriteAccessAllowed=${application.isWriteAccessAllowed})", Throwable())
      }
      val supplier = getInstance(module).supplier
      return supplier.now ?: if (application.isWriteAccessAllowed) {
        // If we're holding the global write lock, blocking on the delegate supplier's
        // computation would create a deadlock since the worker thread needs the read
        // lock to create a new snapshot. The only way to satisfy the caller in this
        // case is to create the snapshot here on the EDT.
        supplier.getOrCreateSnapshotFromWriteAction()
      }
      else {
        supplier.get().get()
      }
    }
  }
}
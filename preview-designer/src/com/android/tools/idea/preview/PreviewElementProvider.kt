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
package com.android.tools.idea.preview

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.ModificationTracker
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Interface to be implemented by classes providing a list of [PreviewElement]
 */
interface PreviewElementProvider<P: PreviewElement> {
  /**
   * Returns a [Sequence] of [PreviewElement]s.
   */
  suspend fun previewElements(): Sequence<P>
}

class StaticPreviewProvider<P : PreviewElement>(private val collection: Collection<P>) : PreviewElementProvider<P> {
  override suspend fun previewElements(): Sequence<P> = collection.asSequence()
}

/**
 * A [PreviewElementProvider] that applies a filter to the result.
 */
class FilteredPreviewElementProvider<P: PreviewElement>(private val delegate: PreviewElementProvider<P>,
                                                        private val filter: (P) -> Boolean) : PreviewElementProvider<P> {
  override suspend fun previewElements(): Sequence<P> = delegate.previewElements().filter(filter)
}

/**
 * A [PreviewElementProvider] for dealing with [PreviewElementProvider] that might be @[Slow]. This [PreviewElementProvider] contents
 * will only be updated when the given [modificationTracker] updates.
 */
class MemoizedPreviewElementProvider<P: PreviewElement>(private val delegate: PreviewElementProvider<P>,
                                                        private val modificationTracker: ModificationTracker) : PreviewElementProvider<P> {
  private var savedModificationStamp = -1L
  private val cachedPreviewElementLock = ReentrantReadWriteLock()
  @GuardedBy("cachedPreviewElementLock")
  private var cachedPreviewElements: Collection<P> = emptyList()

  /**
   * Refreshes the [previewElements]. Do not call on the UI thread.
   */
  private suspend fun refreshIfNeeded() {
    val newModificationStamp = modificationTracker.modificationCount

    try {
      if (newModificationStamp != savedModificationStamp) {
        val elements = delegate.previewElements().toList()
        cachedPreviewElementLock.write {
          cachedPreviewElements = elements
        }
        savedModificationStamp = newModificationStamp
      }
    } catch(t: Throwable) {
      // An exception happened during the refresh, log it and return the previous cached value if any.
      // This is a mitigation for b/222843951 which causes an unexpected exception during delegate.previewElements()
      Logger.getInstance(MemoizedPreviewElementProvider::class.java).warn(t)
    }
  }

  /**
   * Returns the latest value of the [ComposePreviewElement]s contained in the [delegate]. If the [modificationTracker] has not changed,
   * this property will return a cached value.
   *
   * _This call might be [Slow]. Do not call on the UI thread._
   */
  override suspend fun previewElements(): Sequence<P> {
    refreshIfNeeded()
    cachedPreviewElementLock.read {
      return cachedPreviewElements.asSequence()
    }
  }
}
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
package com.android.tools.idea.compose.preview

import com.android.annotations.concurrency.Slow

/**
 * Interface to be implemented by classes providing a list of [PreviewElement]
 */
interface PreviewElementProvider {
  val previewElements: List<PreviewElement>
}

/**
 * A [PreviewElementProvider] that applies a filter to the result.
 */
class FilteredPreviewElementProvider(private val delegate: PreviewElementProvider,
                                     private val filter: (PreviewElement) -> Boolean) : PreviewElementProvider {
  override val previewElements: List<PreviewElement>
    get() = delegate.previewElements.filter(filter)
}

/**
 * A [PreviewElementProvider] for dealing with [PreviewElementProvider] that might be @[Slow]. This [PreviewElementProvider] contents
 * will only be updated when the [refresh] method is called and it will return the same contents until a new call happens.
 */
class MemoizedPreviewElementProvider(private val delegate: PreviewElementProvider) : PreviewElementProvider {
  /**
   * The [PreviewElement]s returned by this property are cached and might not represent the latest state of the [delegate].
   * You need to call [refresh] to update this copy.
   */
  override var previewElements: List<PreviewElement> = emptyList()
    @Synchronized get
    @Synchronized private set

  /**
   * Refreshes the [previewElements]. Do not call on the UI thread.
   */
  @Slow
  fun refresh() {
    previewElements = delegate.previewElements
  }
}
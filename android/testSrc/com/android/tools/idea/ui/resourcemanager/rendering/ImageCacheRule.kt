/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.rendering

import com.android.tools.idea.testing.NamedExternalResource
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.update.MergingUpdateQueue
import org.junit.Before
import org.junit.runner.Description

/**
 * A rule to use an [ImageCache]
 */
class ImageCacheRule : NamedExternalResource() {

  lateinit var imageCache: ImageCache
  private lateinit var disposable: Disposable

  @Before
  override fun before(description: Description) {
    disposable = Disposer.newDisposable()
    imageCache = ImageCache.createImageCache(
      disposable,
      mergingUpdateQueue = MergingUpdateQueue("queue", 0, true, MergingUpdateQueue.ANY_COMPONENT, disposable, null,
                                              false).apply { isPassThrough = true })
  }

  override fun after(description: Description) {
    imageCache.clear()
    Disposer.dispose(disposable)
  }
}
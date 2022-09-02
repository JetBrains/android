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

import com.intellij.openapi.util.SimpleModificationTracker
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class PreviewElementProviderTest {
  @Test
  fun testFilteredProvider() = runBlocking {
    val staticPreviewProvider = StaticPreviewProvider(listOf(
      TestPreviewElement("PreviewMethod1"),
      TestPreviewElement("PreviewMethod2"),
      TestPreviewElement("AMethod")
    ))

    var filterWord = "AM"
    val filtered = FilteredPreviewElementProvider(staticPreviewProvider) {
      !it.displaySettings.name.contains(filterWord)
    }

    Assert.assertEquals(3, staticPreviewProvider.previewElements().count())
    // The filtered provider contains all elements without the word internal
    Assert.assertEquals(listOf("PreviewMethod1", "PreviewMethod2"),
                        filtered.previewElements().map { it.displaySettings.name }.toList())

    // Now remove all elements with the word Preview
    filterWord = "Preview"
    Assert.assertEquals("AMethod", filtered.previewElements().single().displaySettings.name)
  }

  @Test
  fun testMemoized() = runBlocking {
    var staticPreviewProvider = StaticPreviewProvider(listOf(
      TestPreviewElement("PreviewMethod1"),
      TestPreviewElement("PreviewMethod2"),
      TestPreviewElement("AMethod")
    ))

    val modificationTracker = SimpleModificationTracker()
    val memoized = MemoizedPreviewElementProvider(object : PreviewElementProvider<PreviewElement> {
      override suspend fun previewElements() = staticPreviewProvider.previewElements()
    }, modificationTracker)

    // Before the first refresh, the list is empty
    Assert.assertEquals(3, memoized.previewElements().count())

    staticPreviewProvider = StaticPreviewProvider(listOf(
      TestPreviewElement("PreviewMethod1")
    ))
    // Updated the source but did not "refresh" by changing the modification stamp
    Assert.assertEquals(3, memoized.previewElements().count())
    modificationTracker.incModificationCount()
    Assert.assertEquals(1, memoized.previewElements().count())
  }
}
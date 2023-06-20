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

import com.android.tools.idea.preview.FilteredPreviewElementProvider
import com.android.tools.idea.preview.StaticPreviewProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposePreviewElementProviderTest {
  @Test
  fun testFilteredProvider() = runBlocking {
    val staticPreviewProvider =
      StaticPreviewProvider<ComposePreviewElementInstance>(
        listOf(
          SingleComposePreviewElementInstance.forTesting("com.sample.TestClass.PreviewMethod1"),
          SingleComposePreviewElementInstance.forTesting("com.sample.TestClass.PreviewMethod2"),
          SingleComposePreviewElementInstance.forTesting("internal.com.sample.TestClass.AMethod")
        )
      )

    var filterWord = "internal"
    val filtered =
      FilteredPreviewElementProvider(staticPreviewProvider) {
        !it.composableMethodFqn.contains(filterWord)
      }

    assertEquals(3, staticPreviewProvider.previewElements().count())
    // The filtered provider contains all elements without the word internal
    assertEquals(
      listOf("com.sample.TestClass.PreviewMethod1", "com.sample.TestClass.PreviewMethod2"),
      filtered.previewElements().map { it.composableMethodFqn }.toList()
    )

    // Now remove all elements with the word Preview
    filterWord = "Preview"
    assertEquals(
      "internal.com.sample.TestClass.AMethod",
      filtered.previewElements().single().composableMethodFqn
    )
  }
}

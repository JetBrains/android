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

import junit.framework.Assert.assertEquals
import org.junit.Test

class UtilsTest {
  @Test
  fun testPreviewConfigurationCleaner() {
    assertEquals(
      PreviewConfiguration.cleanAndGet(-120, null, 1, 1),
      PreviewConfiguration.cleanAndGet(-120, null, -2, -10))

    assertEquals(
      PreviewConfiguration.cleanAndGet(9000, null, MAX_WIDTH, MAX_HEIGHT),
      PreviewConfiguration.cleanAndGet(9000, null, 500000, 500000))

    assertEquals(
      PreviewConfiguration.cleanAndGet(12, null, 120, MAX_HEIGHT),
      PreviewConfiguration.cleanAndGet(12, null, 120, 500000))
  }
}
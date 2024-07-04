/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.preview

import org.junit.Assert
import org.junit.Test

class PreviewConfigurationTest {
  @Test
  fun testPreviewConfigurationCleaner() {
    Assert.assertEquals(
      PreviewConfiguration.cleanAndGet(-120, null, 1, 1, "", 2f, null, "", 2),
      PreviewConfiguration.cleanAndGet(-120, null, -2, -10, null, 2f, 0, null, 2)
    )

    Assert.assertEquals(
      PreviewConfiguration.cleanAndGet(
        9000,
        null,
        MAX_DIMENSION,
        MAX_DIMENSION,
        null,
        null,
        null,
        "id:device"
      ),
      PreviewConfiguration.cleanAndGet(9000, null, 500000, 500000, null, 1f, 0, "id:device")
    )

    Assert.assertEquals(
      PreviewConfiguration.cleanAndGet(12, null, 120, MAX_DIMENSION, null, -1f, 123, null, -1),
      PreviewConfiguration.cleanAndGet(12, null, 120, 500000, null, 0f, 123, null, null)
    )
  }
}

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
package com.android.tools.idea.wear.preview

import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

fun CodeInsightTestFixture.stubWearTilePreviewAnnotation() {
  addFileToProjectAndInvalidate(
    "src/main/androidx/wear/tiles/tooling/preview/Preview.kt",
    // language=kotlin
    """
        package androidx.wear.tiles.tooling.preview

        import androidx.annotation.FloatRange

        object WearDevices {
            const val LARGE_ROUND = "id:wearos_large_round"
            const val SMALL_ROUND = "id:wearos_small_round"
            const val SQUARE = "id:wearos_square"
            const val RECT = "id:wearos_rect"
        }

        class TilePreviewData

        annotation class Preview(
            val name: String = "",
            val group: String = "",
            val device: String = WearDevices.SMALL_ROUND,
            val locale: String = "",
            @FloatRange(from = 0.01) val fontScale: Float = 1f,
        )
        """.trimIndent(),
  )
}
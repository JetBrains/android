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
package org.jetbrains.android.compose

import com.intellij.testFramework.fixtures.CodeInsightTestFixture

fun CodeInsightTestFixture.stubComposableAnnotation() {
  addFileToProject(
    "src/androidx/compose/Composable.kt",
    // language=kotlin
    """
    package androidx.compose

    annotation class Composable
    """.trimIndent()
  )
}

fun CodeInsightTestFixture.stubPreviewAnnotation() {
  addFileToProject(
    "src/com/android/tools/preview/Preview.kt",
    // language=kotlin
    """
    package androidx.ui.tooling.preview

    data class Configuration(
      private val apiLevel: Int? = null,
      private val theme: String? = null,
      private val width: Int? = null,
      private val height: Int? = null,
      private val fontScale: Float = 1f
    )

    annotation class Preview(
      val name: String = "",
      val group: String = "",
      val apiLevel: Int = -1,
      val theme: String = "",
      val widthDp: Int = -1,
      val heightDp: Int = -1,
      val fontScale: Float = 1f
    )
    """.trimIndent()
  )
}

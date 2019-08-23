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

import com.android.tools.idea.flags.StudioFlags
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language

open class ComposeLightCodeInsightFixtureTestCase : LightCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()

    StudioFlags.COMPOSE_PREVIEW.override(true)

    @Language("kotlin")
    val previewAnnotation = myFixture.addFileToProject("src/com/android/tools/preview/Preview.kt", """
      package com.android.tools.preview

      data class Configuration(private val apiLevel: Int? = null,
                               private val theme: String? = null,
                               private val width: Int? = null,
                               private val height: Int? = null)

      annotation class Preview(val name: String = "",
                               val apiLevel: Int = -1,
                               val theme: String = "",
                               val width: Int = -1,
                               val height: Int = -1)

      fun Preview(name: String? = null,
                  configuration: Configuration? = null,
                  children: () -> Unit) {
          children()
      }
    """.trimIndent())

    @Language("kotlin")
    val composeAnnotation = myFixture.addFileToProject("src/android/compose/Composable.kt", """
      package androidx.compose

      annotation class Composable()
    """.trimIndent())
  }

  override fun tearDown() {
    super.tearDown()

    StudioFlags.COMPOSE_PREVIEW.clearOverride()
  }
}
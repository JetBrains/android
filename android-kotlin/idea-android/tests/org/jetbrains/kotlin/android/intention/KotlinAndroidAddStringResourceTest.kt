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
package org.jetbrains.kotlin.android.intention

import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.android.KotlinAndroidTestCase

class KotlinAndroidAddStringResourceTest : KotlinAndroidTestCase() {

  private val addStringResource = AndroidBundle.message("add.string.resource.intention.text")

  fun testNotApplicableFoComposeFunctions() {
    myFixture.addClass(
      // language=JAVA
      """
      package androidx.compose;

      public @interface Composable {}
      """.trimIndent()
    )
    assertTrue(myFixture.findClass("androidx.compose.Composable").isAnnotationType)

    val file = myFixture.addFileToProject("src/com/example/test.kt",
      // language=kotlin
      """
        package com.example

        import androidx.compose.Composable

        @Composable
        fun myCompose() {
          val str = "stri${caret}ng"
        }

        fun myNotCompose() {
          val str = "string2"
        }
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    assertThat(intentions).doesNotContain(addStringResource)

    // Check that it's available in other places.
    myFixture.moveCaret("stri|ng2")
    assertThat(intentions).contains(addStringResource)
  }

  private val intentions get() = myFixture.availableIntentions.map { it.text }
}

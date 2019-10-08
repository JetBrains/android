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
package org.jetbrains.android

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression
import java.awt.Color

/**
 * Tests for [ComposeColorAnnotator]
 */
class ComposeColorAnnotatorTest : AndroidTestCase() {
  override fun setUp() {
    StudioFlags.COMPOSE_GUTTER_ICON_COLOR.override(true)
    super.setUp()
    myFixture.addClass(
      //language=kotlin
      """
      package androidx.ui.graphics
      class ColorSpace {}
      """)
    myFixture.addFileToProject(
      "src/com/android/test/Color.kt",
      //language=kotlin
      """
      package androidx.ui.graphics
      class Color(value: Double) {
      }
      fun Color(color: Int): Color? = Color(rgb.toDouble())
      fun Color(color: Long): Color? = Color(1)
      fun Color(
          red: Int,
          green: Int,
          blue: Int,
          alpha: Int = 0xFF
      ): Color? = Color(1)
      fun Color(
          red: Float,
          green: Float,
          blue: Float,
          alpha: Float = 1f,
          colorSpace: ColorSpace? = nul
      ): Color? = Color(1)
      """.trimIndent())
  }

  override fun tearDown() {
    try {
      StudioFlags.COMPOSE_GUTTER_ICON_COLOR.clearOverride()
    } finally {
      super.tearDown()
    }
  }

  fun testColorLong() {
    val psiFile = myFixture.addFileToProject(
      "src/com/android/test/A.kt",
      //language=kotlin
      """
      package com.android.test
      import androidx.ui.graphics.Color
      class A {
        val other = Color(0xFFC20029)
        fun () {
          val primary = Color(0xFF4A8A7B)
          val primaryVariant = Color(color = 0xFF57AD28)
        }
      }
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    checkGutterIconInfos(
      listOf(
        Color(194,0,41),
        Color(74, 138, 123),
        Color(87, 173, 40)
      ),
      includeClickAction = true
    )
    setNewColor("Co|lor(0xFF4A8A7B)", Color(0xFFAABBCC.toInt()))
    assertThat(myFixture.editor.document.text).isEqualTo(
      //language=kotlin
      """
      package com.android.test
      import androidx.ui.graphics.Color
      class A {
        val other = Color(0xFFC20029)
        fun () {
          val primary = Color(0xFFAABBCC)
          val primaryVariant = Color(color = 0xFF57AD28)
        }
      }
      """.trimIndent()
    )
  }

  fun testColorInt() {
    val psiFile = myFixture.addFileToProject(
      "src/com/android/test/A.kt",
      //language=kotlin
      """
      package com.android.test
      import androidx.ui.graphics.Color
      class A {
        val other = Color(0xC20029)
        fun () {
          val primary = Color(0x4A8A7B)
          val primaryVariant = Color(color = 0x57AD28)
        }
      }
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    checkGutterIconInfos(
      listOf(
        Color(194,0,41),
        Color(74, 138, 123),
        Color(87, 173, 40)),
      includeClickAction = true
    )
    setNewColor("Co|lor(0x4A8A7B)", Color(0xFFAABBCC.toInt()))
    assertThat(myFixture.editor.document.text).isEqualTo(
      //language=kotlin
      """
      package com.android.test
      import androidx.ui.graphics.Color
      class A {
        val other = Color(0xC20029)
        fun () {
          val primary = Color(0xFFAABBCC)
          val primaryVariant = Color(color = 0x57AD28)
        }
      }
      """.trimIndent())
  }

  private fun setNewColor(window: String, newColor: Color) {
    val element = myFixture.moveCaret(window)
    val annotationHolder = AnnotationHolderImpl(AnnotationSession(myFixture.file))
    ComposeColorAnnotator().annotate(element.parentOfType<KtCallExpression>()!! as PsiElement, annotationHolder)
    val iconRenderer = annotationHolder[0].gutterIconRenderer as ColorIconRenderer
    iconRenderer.setColorToAttribute(newColor)
  }

  fun testColorIntx3x4() {
    val psiFile = myFixture.addFileToProject(
      "src/com/android/test/A.kt",
      //language=kotlin
      """
      package com.android.test
      import androidx.ui.graphics.Color
      class A {
        val other = Color(0.194f, 0f, 0.41f)
        fun () {
          val primary = Color(0.74f, 0.138f, 0.3f, 0.845f)
          val primaryVariant = Color(red = 0.87f, green = 0.173f, blue = 0.4f)
        }
      }
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    checkGutterIconInfos(
      listOf(
        Color(49,0,105),
        Color(189, 35, 77, 215),
        Color(222, 44, 102)),
      includeClickAction = false
    )
  }

  fun testFloatIntx3x4() {
    val psiFile = myFixture.addFileToProject(
      "src/com/android/test/A.kt",
      //language=kotlin
      """
      package com.android.test
      import androidx.ui.graphics.Color
      class A {
        val other = Color(194, 0, 41)
        fun () {
          val primary = Color(74, 138, 123, 145)
          val primaryVariant = Color(red = 87, green = 173, blue = 40)
        }
      }
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    checkGutterIconInfos(
      listOf(
        Color(194,0,41),
        Color(74, 138, 123, 145),
        Color(87, 173, 40)),
      includeClickAction = false
    )
  }

  private fun checkGutterIconInfos(expectedColorIcons: List<Color>, includeClickAction: Boolean) {
    val iconList = myFixture.doHighlighting().filter { it.gutterIconRenderer is ColorIconRenderer }.sortedBy { it.startOffset }
    assertThat(iconList).hasSize(3)
    iconList.forEach {
      assertThat(it.gutterIconRenderer.icon).isNotNull()
      if (includeClickAction) {
        assertThat((it.gutterIconRenderer as ColorIconRenderer).clickAction).isNotNull()
      } else {
        assertThat((it.gutterIconRenderer as ColorIconRenderer).clickAction).isNull()
      }
    }
    assertThat(iconList.map { (it.gutterIconRenderer as ColorIconRenderer).color }).containsExactlyElementsIn(expectedColorIcons)
  }
}
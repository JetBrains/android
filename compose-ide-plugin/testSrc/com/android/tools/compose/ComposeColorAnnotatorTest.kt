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
package com.android.tools.compose

import com.android.SdkConstants
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.ui.resourcemanager.rendering.MultipleColorIcon
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.AndroidAnnotatorUtil
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Color

/**
 * Tests for [ComposeColorAnnotator]
 */
class ComposeColorAnnotatorTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private val myFixture: JavaCodeInsightTestFixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }

  @Before
  fun setUp() {
    StudioFlags.COMPOSE_EDITOR_SUPPORT.override(true)
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.addClass(
      //language=java
      """
      package androidx.compose.ui.graphics;
      class ColorSpace {
        public static final ColorSpace TEST_SPACE = ColorSpace();
      }
      """)
    myFixture.addFileToProject(
      "src/com/androidx/compose/ui/graphics/Color.kt",
      //language=kotlin
      """
      package androidx.compose.ui.graphics
      fun Color(color: Int): Long = 1L
      fun Color(color: Long): Long = 1L
      fun Color(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int = 0xFF
      ): Long = 1L
      fun Color(
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float = 1f,
        colorSpace: ColorSpace? = null
      ): Long = 1L
      """.trimIndent())
  }

  @Test
  fun testColorLong() {
    val psiFile = myFixture.addFileToProject(
      "src/com/android/test/A.kt",
      //language=kotlin
      """
      package com.android.test
      import androidx.compose.ui.graphics.Color
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
        Color(194, 0, 41),
        Color(74, 138, 123),
        Color(87, 173, 40)
      ),
      includeClickAction = true
    )
    setNewColor("Co|lor(0xFF4A8A7B)", Color(0xFFAABBCC.toInt()), ComposeColorConstructor.LONG)
    assertThat(myFixture.editor.document.text).isEqualTo(
      //language=kotlin
      """
      package com.android.test
      import androidx.compose.ui.graphics.Color
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

  @Test
  fun testColorInt() {
    val psiFile = myFixture.addFileToProject(
      "src/com/android/test/A.kt",
      //language=kotlin
      """
      package com.android.test
      import androidx.compose.ui.graphics.Color
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
        Color(194, 0, 41, 0),
        Color(74, 138, 123, 0),
        Color(87, 173, 40, 0)),
      includeClickAction = true
    )
    setNewColor("Co|lor(0x4A8A7B)", Color(0xFFAABBCC.toInt()), ComposeColorConstructor.INT)
    assertThat(myFixture.editor.document.text).isEqualTo(
      //language=kotlin
      """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC20029)
        fun () {
          val primary = Color(0xFFAABBCC)
          val primaryVariant = Color(color = 0x57AD28)
        }
      }
      """.trimIndent())
  }

  @Test
  fun testColorInt_X3() {
    val psiFile = myFixture.addFileToProject(
      "src/com/android/test/A.kt",
      //language=kotlin
      """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC2, 0x00, 0x29)
        fun () {
          val primary = Color(0x4A, 0x8A, 0x7B)
          val primaryVariant = Color(red = 0x57, green = 0xAD, blue = 0x28)
        }
      }
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    checkGutterIconInfos(
      listOf(
        Color(194, 0, 41),
        Color(74, 138, 123),
        Color(87, 173, 40)),
      includeClickAction = true
    )
    setNewColor("Co|lor(0x4A, 0x8A, 0x7B)", Color(0xFFAABBCC.toInt()), ComposeColorConstructor.INT_X3)
    assertThat(myFixture.editor.document.text).isEqualTo(
      //language=kotlin
      """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC2, 0x00, 0x29)
        fun () {
          val primary = Color(170, 187, 204)
          val primaryVariant = Color(red = 0x57, green = 0xAD, blue = 0x28)
        }
      }
      """.trimIndent())
  }

  @Test
  fun testColorInt_X4() {
    val psiFile = myFixture.addFileToProject(
      "src/com/android/test/A.kt",
      //language=kotlin
      """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC2, 0x00, 0x29, 0xFF)
        fun () {
          val primary = Color(0x4A, 0x8A, 0x7B, 0xFF)
          val primaryVariant = Color(red = 0x57, green = 0xAD, blue = 0x28, alpha = 0xFF)
        }
      }
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    checkGutterIconInfos(
      listOf(
        Color(194, 0, 41),
        Color(74, 138, 123),
        Color(87, 173, 40)),
      includeClickAction = true
    )
    setNewColor("Co|lor(0x4A, 0x8A, 0x7B, 0xFF)", Color(0xFFAABBCC.toInt()), ComposeColorConstructor.INT_X4)
    assertThat(myFixture.editor.document.text).isEqualTo(
      //language=kotlin
      """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC2, 0x00, 0x29, 0xFF)
        fun () {
          val primary = Color(170, 187, 204, 255)
          val primaryVariant = Color(red = 0x57, green = 0xAD, blue = 0x28, alpha = 0xFF)
        }
      }
      """.trimIndent())
  }


  @Test
  fun testColorFloat_X3() {
    val psiFile = myFixture.addFileToProject(
      "src/com/android/test/A.kt",
      //language=kotlin
      """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0.14f, 0.0f, 0.16f)
        fun () {
          val primary = Color(0.3f, 0.54f, 0.48f)
          val primaryVariant = Color(red = 0.34f, green = 0.68f, blue = 0.15f)
        }
      }
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    checkGutterIconInfos(
      listOf(
        Color(36, 0, 41),
        Color(77, 138, 122),
        Color(87, 173, 38)),
      includeClickAction = true
    )
    setNewColor("Co|lor(0.3f, 0.54f, 0.48f)", Color(0xFFAABBCC.toInt()), ComposeColorConstructor.FLOAT_X3)
    assertThat(myFixture.editor.document.text).isEqualTo(
      //language=kotlin
      """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0.14f, 0.0f, 0.16f)
        fun () {
          val primary = Color(0.6666667f, 0.73333335f, 0.8f)
          val primaryVariant = Color(red = 0.34f, green = 0.68f, blue = 0.15f)
        }
      }
      """.trimIndent())
  }

  @Test
  fun testColorFloat_X4() {
    val psiFile = myFixture.addFileToProject(
      "src/com/android/test/A.kt",
      //language=kotlin
      """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0.194f, 0f, 0.41f, 0.5f)
        fun () {
          val primary = Color(0.74f, 0.138f, 0.3f, 0.845f)
          val primaryVariant = Color(red = 0.87f, green = 0.173f, blue = 0.4f, alpha = 0.25f)
        }
      }
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    checkGutterIconInfos(
      listOf(
        Color(49, 0, 105, 128),
        Color(189, 35, 77, 215),
        Color(222, 44, 102, 64)),
      includeClickAction = true
    )
    setNewColor("Co|lor(0.74f, 0.138f, 0.3f, 0.845f)", Color(0xFFAABBCC.toInt()), ComposeColorConstructor.FLOAT_X4)
    assertThat(myFixture.editor.document.text).isEqualTo(
      //language=kotlin
      """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0.194f, 0f, 0.41f, 0.5f)
        fun () {
          val primary = Color(0.6666667f, 0.73333335f, 0.8f, 1.0f)
          val primaryVariant = Color(red = 0.87f, green = 0.173f, blue = 0.4f, alpha = 0.25f)
        }
      }
      """.trimIndent())
  }

  @Test
  fun testColorFloat_X4_ColorSpace() {
    val psiFile = myFixture.addFileToProject(
      "src/com/android/test/A.kt",
      //language=kotlin
      """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      import androidx.compose.ui.graphics.ColorSpace
      class A {
        val other = Color(0.194f, 0f, 0.41f, 0.5f, ColorSpace.TEST_SPACE)
        fun () {
          val primary = Color(0.74f, 0.138f, 0.3f, 0.845f, ColorSpace.TEST_SPACE)
          val primaryVariant = Color(red = 0.87f, green = 0.173f, blue = 0.4f, alpha = 0.25f, colorSpace = ColorSpace.TEST_SPACE)
        }
      }
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    checkGutterIconInfos(
      listOf(
        Color(49, 0, 105, 128),
        Color(189, 35, 77, 215),
        Color(222, 44, 102, 64)),
      includeClickAction = false
    )
  }

  private fun setNewColor(window: String, newColor: Color, constructorType: ComposeColorConstructor) {
    runInEdtAndWait {
      val element = myFixture.moveCaret(window)
      val annotationHolder = AnnotationHolderImpl(AnnotationSession(myFixture.file))
      annotationHolder.runAnnotatorWithContext(element.parentOfType<KtCallExpression>()!! as PsiElement, ComposeColorAnnotator())
      val iconRenderer = annotationHolder[0].gutterIconRenderer as ColorIconRenderer
      val project = myFixture.project

      val setColorTask = iconRenderer.getSetColorTask(constructorType) ?: return@runInEdtAndWait
      ApplicationManager.getApplication().invokeLater({
        WriteCommandAction.runWriteCommandAction(project, "Change Color", null, { setColorTask.invoke(newColor) })
      }, project.disposed)

      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
  }

  private fun checkGutterIconInfos(expectedColorIcons: List<Color>, includeClickAction: Boolean) {
    val iconList = myFixture.doHighlighting().filter { it.gutterIconRenderer is ColorIconRenderer }.sortedBy { it.startOffset }
    assertThat(iconList).hasSize(3)
    iconList.forEach {
      assertThat(it.gutterIconRenderer.icon).isNotNull()
      if (includeClickAction) {
        val action = runReadAction { (it.gutterIconRenderer as ColorIconRenderer).clickAction }
        assertThat(action).isNotNull()
      }
      else {
        val action = runReadAction { (it.gutterIconRenderer as ColorIconRenderer).clickAction }
        assertThat(action).isNull()
      }
    }
    assertThat(iconList.map { (it.gutterIconRenderer as ColorIconRenderer).color }).containsExactlyElementsIn(expectedColorIcons)
  }
}

/**
 * Tests for [AndroidKotlinResourceExternalAnnotator]
 */
class ComposeColorReferenceAnnotatorTest {
  private val projectRule = AndroidProjectRule.onDisk()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  private val myFixture: JavaCodeInsightTestFixture by lazy { projectRule.fixture as JavaCodeInsightTestFixture }

  @Before
  fun setUp() {
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.stubComposableAnnotation(ComposeFqNames.root)
    myFixture.testDataPath = getComposePluginTestDataPath()
    myFixture.copyFileToProject("annotator/colors.xml", "res/values/colors.xml")
    myFixture.copyFileToProject("annotator/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML)
  }

  // Regression test for https://issuetracker.google.com/144560843
  @RunsInEdt
  @Test
  fun testColorReferenceNoLayout() {
    myFixture.loadNewFile(
      "src/com/example/MyViews.kt",
      // language=kotlin
      """
      package p1.p2

      import androidx.compose.runtime.Composable
      
      @Composable
      fun Foobar(required: Int) {
        val drawable = R.drawable.ic_tick
        val color = R.color.color1
      }
      """.trimIndent()
    )

    val icons = myFixture.findAllGutters()
    val colorGutterIconRenderer = icons.firstIsInstance<AndroidAnnotatorUtil.ColorRenderer>()
    assertThat((colorGutterIconRenderer.icon as MultipleColorIcon).colors).containsExactlyElementsIn(arrayOf(Color(63, 81, 181)))
  }
}

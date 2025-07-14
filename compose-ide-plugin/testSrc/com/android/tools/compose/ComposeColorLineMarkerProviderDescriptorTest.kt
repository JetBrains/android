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
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.ui.resourcemanager.rendering.MultipleColorIcon
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.AndroidAnnotatorUtil
import org.jetbrains.android.compose.addComposeRuntimeDep
import org.jetbrains.android.compose.addComposeUiGraphicsDep
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Color

/** Tests for [ComposeColorLineMarkerProviderDescriptor] */
class ComposeColorLineMarkerProviderDescriptorTest {
  @get:Rule val projectRule = AndroidProjectRule.onDisk().withKotlin()

  private val myFixture: JavaCodeInsightTestFixture by lazy { projectRule.fixture }

  @Before
  fun setUp() {
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.addComposeRuntimeDep()
    myFixture.addComposeUiGraphicsDep()
  }

  @Test
  fun testColorLong() {
    val psiFile =
      myFixture.addFileToProject(
        "src/com/android/test/A.kt",
        // language=kotlin
        """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xFFC20029)
        fun () {
          val primary = Color(0xA84A8A7B)
          val secondary = Color(0xFF4A8A7B)
          val primaryVariant = Color(color = 0xFF57AD28)
          val secondaryVariant = Color(color = 0x8057AD28)
        }
      }
      """
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    checkGutterIconInfos(
      listOf(
        Color(194, 0, 41, 255),
        Color(74, 138, 123, 168),
        Color(74, 138, 123, 255),
        Color(87, 173, 40, 255),
        Color(87, 173, 40, 128),
      )
    )
    setNewColor("Co|lor(0xFF4A8A7B)", Color(0xFFAABBCC.toInt()))
    setNewColor("Co|lor(color = 0xFF57AD28)", Color(0xFFAABBCC.toInt()))
    assertThat(myFixture.editor.document.text)
      .isEqualTo(
        // language=kotlin
        """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xFFC20029)
        fun () {
          val primary = Color(0xA84A8A7B)
          val secondary = Color(0xFFAABBCC)
          val primaryVariant = Color(color = 0xFFAABBCC)
          val secondaryVariant = Color(color = 0x8057AD28)
        }
      }
      """
          .trimIndent()
      )
  }

  @Test
  fun testColorWithLeadingZero() {
    val psiFile =
      myFixture.addFileToProject(
        "src/com/android/test/A.kt",
        // language=kotlin
        """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xFFFF0000)
      }
      """
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    checkGutterIconInfos(listOf(Color(255, 0, 0, 255)))
    setNewColor("Co|lor(0xFFFF0000)", Color(0x0DFF0000, true))
    assertThat(myFixture.editor.document.text)
      .isEqualTo(
        // language=kotlin
        """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0x0DFF0000)
      }
      """
          .trimIndent()
      )
  }

  @Test
  fun testColorInt() {
    val psiFile =
      myFixture.addFileToProject(
        "src/com/android/test/A.kt",
        // language=kotlin
        """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC20029)
        fun () {
          val primary = Color(0x4A8A7B)
          val secondary = Color(0x804A8A7B)
          val primaryVariant = Color(color = 0x57AD28)
          val secondaryVariant = Color(color = 0x4057AD28)
        }
      }
      """
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    checkGutterIconInfos(
      listOf(
        Color(194, 0, 41, 0),
        Color(74, 138, 123, 0),
        Color(74, 138, 123, 128),
        Color(87, 173, 40, 0),
        Color(87, 173, 40, 64),
      )
    )
    setNewColor("Co|lor(0x4A8A7B)", Color(0xFFAABBCC.toInt()))
    setNewColor("Co|lor(color = 0x57AD28)", Color(0xFFAABBCC.toInt()))
    assertThat(myFixture.editor.document.text)
      .isEqualTo(
        // language=kotlin
        """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC20029)
        fun () {
          val primary = Color(0xFFAABBCC)
          val secondary = Color(0x804A8A7B)
          val primaryVariant = Color(color = 0xFFAABBCC)
          val secondaryVariant = Color(color = 0x4057AD28)
        }
      }
      """
          .trimIndent()
      )
  }

  @Test
  fun testColorInt_X3() {
    val psiFile =
      myFixture.addFileToProject(
        "src/com/android/test/A.kt",
        // language=kotlin
        """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC2, 0x00, 0x29)
        fun () {
          val primary = Color(0x4A, 0x8A, 0x7B)
          val secondary = Color(170, 187, 204)
          val primaryVariant = Color(red = 0x57, green = 0xAD, blue = 0x28)
          val secondaryVariant = Color(green = 200, red = 180, blue = 120)
        }
      }
      """
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    checkGutterIconInfos(
      listOf(
        Color(194, 0, 41),
        Color(74, 138, 123),
        Color(170, 187, 204),
        Color(87, 173, 40),
        Color(180, 200, 120),
      )
    )
    setNewColor("Co|lor(0x4A, 0x8A, 0x7B)", Color(0xFFAABBCC.toInt()))
    setNewColor("Co|lor(170, 187, 204)", Color(0xFF406080.toInt()))
    setNewColor("Co|lor(red = 0x57, green = 0xAD, blue = 0x28)", Color(0xFFAABBCC.toInt()))
    setNewColor("Co|lor(green = 200, red = 180, blue = 120)", Color(0x80112233.toInt()))
    assertThat(myFixture.editor.document.text)
      .isEqualTo(
        // language=kotlin
        """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC2, 0x00, 0x29)
        fun () {
          val primary = Color(0xAA, 0xBB, 0xCC, 0xFF)
          val secondary = Color(64, 96, 128, 255)
          val primaryVariant = Color(red = 0xAA, green = 0xBB, blue = 0xCC, alpha = 0xFF)
          val secondaryVariant = Color(red = 17, green = 34, blue = 51, alpha = 255)
        }
      }
      """
          .trimIndent()
      )
  }

  @Test
  fun testColorInt_X4() {
    val psiFile =
      myFixture.addFileToProject(
        "src/com/android/test/A.kt",
        // language=kotlin
        """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC2, 0x00, 0x29, 0xFF)
        fun () {
          val primary = Color(0x4A, 0x8A, 0x7B, 0xFF)
          val secondary = Color(170, 187, 204, 255)
          val primaryVariant = Color(red = 0x57, green = 0xAD, blue = 0x28, alpha = 0xFF)
          val secondaryVariant = Color(green = 120, red = 64, alpha = 255, blue = 192)
        }
      }
      """
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    checkGutterIconInfos(
      listOf(
        Color(194, 0, 41),
        Color(74, 138, 123),
        Color(170, 187, 204),
        Color(87, 173, 40),
        Color(64, 120, 192),
      )
    )
    setNewColor("Co|lor(0x4A, 0x8A, 0x7B, 0xFF)", Color(0xFFAABBCC.toInt()))
    setNewColor("Co|lor(green = 120, red = 64, alpha = 255, blue = 192)", Color(0xFFAABBCC.toInt()))
    assertThat(myFixture.editor.document.text)
      .isEqualTo(
        // language=kotlin
        """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0xC2, 0x00, 0x29, 0xFF)
        fun () {
          val primary = Color(0xAA, 0xBB, 0xCC, 0xFF)
          val secondary = Color(170, 187, 204, 255)
          val primaryVariant = Color(red = 0x57, green = 0xAD, blue = 0x28, alpha = 0xFF)
          val secondaryVariant = Color(red = 170, green = 187, blue = 204, alpha = 255)
        }
      }
      """
          .trimIndent()
      )
  }

  @Test
  fun testColorFloat_X3() {
    val psiFile =
      myFixture.addFileToProject(
        "src/com/android/test/A.kt",
        // language=kotlin
        """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0.14f, 0.0f, 0.16f)
        fun () {
          val primary = Color(0.3f, 0.54f, 0.48f)
          val primary = Color(0.3f, 0.54f, 0.48f)
          val primaryVariant = Color(red = 0.34f, green = 0.68f, blue = 0.15f)
          val primaryVariant = Color(green = 0.68f, red = 0.34f, blue = 0.15f)
        }
      }
      """
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    checkGutterIconInfos(
      listOf(
        Color(36, 0, 41),
        Color(77, 138, 122),
        Color(77, 138, 122),
        Color(87, 173, 38),
        Color(87, 173, 38),
      )
    )
    setNewColor("Co|lor(0.3f, 0.54f, 0.48f)", Color(0xFFAABBCC.toInt()))
    setNewColor("Co|lor(green = 0.68f, red = 0.34f, blue = 0.15f)", Color(0xFFAABBCC.toInt()))
    assertThat(myFixture.editor.document.text)
      .isEqualTo(
        // language=kotlin
        """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0.14f, 0.0f, 0.16f)
        fun () {
          val primary = Color(0.667f, 0.733f, 0.8f, 1.0f)
          val primary = Color(0.3f, 0.54f, 0.48f)
          val primaryVariant = Color(red = 0.34f, green = 0.68f, blue = 0.15f)
          val primaryVariant = Color(red = 0.667f, green = 0.733f, blue = 0.8f, alpha = 1.0f)
        }
      }
      """
          .trimIndent()
      )
  }

  @Test
  fun testColorFloat_X4() {
    val psiFile =
      myFixture.addFileToProject(
        "src/com/android/test/A.kt",
        // language=kotlin
        """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0.194f, 0f, 0.41f, 0.5f)
        fun () {
          val primary = Color(0.74f, 0.138f, 0.3f, 0.845f)
          val primary = Color(0.74f, 0.138f, 0.3f, 0.845f)
          val primaryVariant = Color(red = 0.87f, green = 0.173f, blue = 0.4f, alpha = 0.25f)
          val primaryVariant = Color(alpha = 0.25f, green = 0.173f, blue = 0.4f, red = 0.87f)
        }
      }
      """
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    checkGutterIconInfos(
      listOf(
        Color(49, 0, 105, 128),
        Color(189, 35, 77, 215),
        Color(189, 35, 77, 215),
        Color(222, 44, 102, 64),
        Color(222, 44, 102, 64),
      )
    )
    setNewColor("Co|lor(0.74f, 0.138f, 0.3f, 0.845f)", Color(0xFFAABBCC.toInt()))
    setNewColor(
      "Co|lor(alpha = 0.25f, green = 0.173f, blue = 0.4f, red = 0.87f)",
      Color(0xFFAABBCC.toInt()),
    )
    assertThat(myFixture.editor.document.text)
      .isEqualTo(
        // language=kotlin
        """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      class A {
        val other = Color(0.194f, 0f, 0.41f, 0.5f)
        fun () {
          val primary = Color(0.667f, 0.733f, 0.8f, 1.0f)
          val primary = Color(0.74f, 0.138f, 0.3f, 0.845f)
          val primaryVariant = Color(red = 0.87f, green = 0.173f, blue = 0.4f, alpha = 0.25f)
          val primaryVariant = Color(red = 0.667f, green = 0.733f, blue = 0.8f, alpha = 1.0f)
        }
      }
      """
          .trimIndent()
      )
  }

  @Test
  fun testColorFloat_X4_ColorSpace() {
    // Note: We don't offer neither color preview nor picker for Color(Float, Float, Float, Float,
    // ColorSpace) function.
    val psiFile =
      myFixture.addFileToProject(
        "src/com/android/test/A.kt",
        // language=kotlin
        """
      package com.android.test
      import androidx.compose.ui.graphics.Color
      import androidx.compose.ui.graphics.colorspace.ColorSpaces
      class A {
        val other = Color(0.194f, 0f, 0.41f, 0.5f, ColorSpaces.LinearSrgb)
        fun () {
          val primary = Color(0.74f, 0.138f, 0.3f, 0.845f, ColorSpaces.LinearSrgb)
          val primary = Color(0f, 0f, 0f, 0f, ColorSpaces.LinearSrgb)
          val primaryVariant = Color(red = 0.87f, green = 0.173f, blue = 0.4f, alpha = 0.25f, colorSpace = ColorSpaces.LinearSrgb)
          val primaryVariant = Color(red = 1.0f, green = 1.0f, blue = 1.0f, alpha = 1.0f, colorSpace = ColorSpaces.LinearSrgb)
        }
      }
      """
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    // No gutter for color space.
    checkGutterIconInfos(listOf())
  }

  private fun setNewColor(window: String, newColor: Color) {
    val element = runInEdtAndGet { myFixture.moveCaret(window) }

    myFixture.doHighlighting()
    val highlightInfo = runReadAction {
      DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, myFixture.project).single {
        lineMarkerInfo ->
        lineMarkerInfo.navigationHandler is ColorIconRenderer && lineMarkerInfo.element == element
      }
    }

    runInEdtAndWait {
      val project = myFixture.project
      val setColorTask =
        (highlightInfo.navigationHandler as ColorIconRenderer).getSetColorTask()
          ?: return@runInEdtAndWait
      WriteCommandAction.runWriteCommandAction(
        project,
        "Change Color",
        null,
        { setColorTask.invoke(newColor) },
      )
    }
  }

  private fun checkGutterIconInfos(expectedColorIcons: List<Color>) {
    myFixture.doHighlighting()
    val highlightInfos = runReadAction {
      DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, myFixture.project)
        .filter { lineMarkerInfo -> lineMarkerInfo.navigationHandler is ColorIconRenderer }
        .sortedBy { it.startOffset }
    }

    assertThat(highlightInfos).hasSize(expectedColorIcons.size)
    highlightInfos.forEach {
      assertThat(it.icon).isNotNull()
      assertThat(it.navigationHandler).isNotNull()
    }

    assertThat(highlightInfos.map { (it.navigationHandler as ColorIconRenderer).color })
      .containsExactlyElementsIn(expectedColorIcons)
  }
}

/** Tests for [AndroidKotlinResourceExternalAnnotator] */
class ComposeColorReferenceAnnotatorTest {
  private val projectRule = AndroidProjectRule.onDisk()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  private val myFixture: JavaCodeInsightTestFixture by lazy {
    projectRule.fixture as JavaCodeInsightTestFixture
  }

  @Before
  fun setUp() {
    (myFixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    myFixture.addComposeRuntimeDep()
    myFixture.testDataPath = getComposePluginTestDataPath()
    myFixture.copyFileToProject("annotator/colors.xml", "res/values/colors.xml")
    myFixture.copyFileToProject(
      "annotator/AndroidManifest.xml",
      SdkConstants.FN_ANDROID_MANIFEST_XML,
    )
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
      """
        .trimIndent(),
    )

    val icons = myFixture.findAllGutters()
    val colorGutterIconRenderer = icons.first { it is AndroidAnnotatorUtil.ColorRenderer }
    assertThat((colorGutterIconRenderer.icon as MultipleColorIcon).colors)
      .containsExactlyElementsIn(arrayOf(Color(63, 81, 181)))
  }
}

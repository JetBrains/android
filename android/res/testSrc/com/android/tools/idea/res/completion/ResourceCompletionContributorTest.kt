/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.res.completion

import com.android.SdkConstants
import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.res.addAarDependency
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addManifest
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.JBColor
import com.intellij.util.ui.ImageUtil
import java.awt.Color
import java.awt.image.BufferedImage
import javax.swing.Icon
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private val COLORS = mapOf("red" to JBColor.RED, "green" to JBColor.GREEN, "blue" to JBColor.BLUE)

/** Tests for [ResourceCompletionContributor]. */
@RunWith(JUnit4::class)
@RunsInEdt
class ResourceCompletionContributorTest {
  @get:Rule val projectRule = AndroidProjectRule.withSdk().onEdt()

  @get:Rule val restoreFlagRule = FlagRule(StudioFlags.RENDER_DRAWABLES_IN_AUTOCOMPLETE_ENABLED)

  private val fixture by lazy { projectRule.fixture }
  private val module by lazy { projectRule.projectRule.module }

  @Before
  fun setUp() {
    StudioFlags.RENDER_DRAWABLES_IN_AUTOCOMPLETE_ENABLED.override(true)
    StudioFlags.RENDER_COLORS_IN_AUTOCOMPLETE_ENABLED.override(true)
    addManifest(fixture)
    val fileName = "res/drawable/my_great_%s_icon.xml"
    // language=XML
    val circle =
      """
      <vector android:height="24dp" android:width="24dp" android:viewportHeight="24" android:viewportWidth="24" android:tint="#%X"
          xmlns:android="http://schemas.android.com/apk/res/android">
        <path android:fillColor="@android:color/white" android:pathData="M12,2C6.47,2 2,6.47 2,12s4.47,10 10,10 10,-4.47 10,-10S17.53,2 12,2z"/>
      </vector>
      """
        .trimIndent()

    COLORS.forEach {
      fixture.addFileToProject(fileName.format(it.key), circle.format(it.value.rgb))
    }

    val colorsXml =
      COLORS.map { (key, value) ->
        "<color name=\"${key}\">#${Integer.toHexString(value.rgb)}</color>"
      }
    val contents =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
        ${colorsXml.joinToString("\n        ")}
      </resources>
      """
        .trimIndent()

    fixture.addFileToProject("res/values/colors.xml", contents)

    // Add some private resources
    addAarDependency(fixture, module, "aarLib", "com.example.aarLib") { resDir ->
      resDir.parentFile
        .resolve(SdkConstants.FN_RESOURCE_TEXT)
        .writeText(
          """
          int color publicColor 0x7f010001
          int color privateColor 0x7f010002
          """
            .trimIndent()
        )
      resDir.parentFile
        .resolve(SdkConstants.FN_PUBLIC_TXT)
        .writeText(
          """
          color publicColor
          """
            .trimIndent()
        )
      resDir
        .resolve("values/colors.xml")
        .writeText(
          // language=XML
          """
          <resources>
            <color name="publicColor">#008577</color>
            <color name="privateColor">#DEADBE</color>
          </resources>
          """
            .trimIndent()
        )
    }
    projectRule.projectRule.waitForResourceRepositoryUpdates()
  }

  @Test
  fun drawableCompletion_java() {
    val file =
      fixture.addFileToProject(
        "/src/com/example/Foo.java",
        // language=java
        """
        package com.example;
        public class Foo {
          public void example() {
            int foo = R.drawable.my_gre${caret}
          }
        }
        """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val results = fixture.completeBasic()
    assertThat(results).hasLength(COLORS.size)

    // All of these should not have a cached icon yet
    val defaultIcons = results.mapNotNull { it.quickRenderedIcon() }.distinct()
    assertThat(defaultIcons).hasSize(1) // Just one default icon.

    // The expensive renderer should work, though.
    results.forEach { assertThat(it.expensiveRenderer).isNotNull() }
    val renderedIcons = results.mapNotNull { it.slowRenderedIcon() }.distinct()
    assertThat(renderedIcons.toColors()).isEqualTo(results.toExpectedColors())

    // Repeat the completion to make new LookupElements that get the Icon set in the constructor.
    val moreResults = fixture.completeBasic()
    assertThat(moreResults).hasLength(COLORS.size)

    // We cannot just check that the cached icons are the same objects as the rendered
    // icons due to concurrency issues in the cache.
    moreResults.forEach { assertThat(it.expensiveRenderer).isNull() }
    val cachedIcons = moreResults.mapNotNull { it.quickRenderedIcon() }.distinct()
    assertThat(cachedIcons.toColors()).isEqualTo(moreResults.toExpectedColors())
  }

  @Test
  fun drawableCompletion_kotlin() {
    val file =
      fixture.addFileToProject(
        "/src/com/example/Foo.kt",
        // language=kotlin
        """
        package com.example
        class Foo {
          fun example() {
            val foo = R.drawable.my_gre${caret}
          }
        }
        """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val results = fixture.completeBasic()
    assertThat(results).hasLength(COLORS.size)

    // All of these should not have a cached icon yet
    val defaultIcons = results.mapNotNull { it.quickRenderedIcon() }.distinct()
    assertThat(defaultIcons).hasSize(1) // Just one default icon.

    // The expensive renderer should work, though.
    results.forEach { assertThat(it.expensiveRenderer).isNotNull() }
    val renderedIcons = results.mapNotNull { it.slowRenderedIcon() }.distinct()
    assertThat(renderedIcons.toColors()).isEqualTo(results.toExpectedColors())

    // Repeat the completion to make new LookupElements that get the Icon set in the constructor.
    val moreResults = fixture.completeBasic()
    assertThat(moreResults).hasLength(COLORS.size)

    // We cannot just check that the cached icons are the same objects as the rendered
    // icons due to concurrency issues in the cache.
    moreResults.forEach { assertThat(it.expensiveRenderer).isNull() }
    val cachedIcons = moreResults.mapNotNull { it.quickRenderedIcon() }.distinct()
    assertThat(cachedIcons.toColors()).isEqualTo(moreResults.toExpectedColors())
  }

  @Test
  fun colorCompletion_java() {
    val file =
      fixture.addFileToProject(
        "/src/com/example/Foo.java",
        // language=java
        """
        package com.example;
        public class Foo {
          public void example() {
            int foo = R.color.${caret}
          }
        }
        """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val results =
      fixture.completeBasic().filter { result ->
        COLORS.entries.any { it.key in result.lookupString }
      }
    assertThat(results).hasSize(COLORS.size)

    for (result in results) {
      val expectedColor = COLORS.entries.first { it.key in result.lookupString }.value
      with(LookupElementPresentation().also(result::renderElement)) {
        assertThat(tailText).isEqualTo(" (${expectedColor.toHexString()})")
        assertThat(icon?.sampleMiddlePoint()).isEqualTo(expectedColor.rgb)
      }
    }
  }

  @Test
  fun colorCompletion_kotlin() {
    val file =
      fixture.addFileToProject(
        "/src/com/example/Foo.kt",
        // language=kotlin
        """
        package com.example
        class Foo {
          fun example() {
            val foo = R.color.${caret}
          }
        }
        """
          .trimIndent(),
      )
    fixture.configureFromExistingVirtualFile(file.virtualFile)

    val results =
      fixture.completeBasic().filter { result ->
        COLORS.entries.any { it.key in result.lookupString }
      }
    assertThat(results).hasSize(COLORS.size)

    for (result in results) {
      val expectedColor = COLORS.entries.first { it.key in result.lookupString }.value
      with(LookupElementPresentation().also(result::renderElement)) {
        assertThat(tailText).isEqualTo(" (${expectedColor.toHexString()})")
        assertThat(icon?.sampleMiddlePoint()).isEqualTo(expectedColor.rgb)
      }
    }
  }

  @Test
  fun privateResourcesFiltered_java() {
    val file =
      fixture
        .addFileToProject(
          "src/com/example/Foo.java",
          """
          package com.example;
          class Foo {
            public void bar() {
              int color = R.color.col
            }
          }
          """
            .trimIndent(),
        )
        .virtualFile

    fixture.openFileInEditor(file)
    fixture.moveCaret("R.color.col|")
    val lookupElements = fixture.completeBasic().toList()
    assertThat(lookupElements.map(LookupElement::getLookupString)).containsExactly("publicColor")
  }

  @Test
  fun privateResourcesFiltered_withPackage_java() {
    val file =
      fixture
        .addFileToProject(
          "src/com/example/Foo.java",
          """
          package com.example;
          class Foo {
            public void bar() {
              int color = com.example.R.color.col
            }
          }
          """
            .trimIndent(),
        )
        .virtualFile

    fixture.openFileInEditor(file)
    fixture.moveCaret("R.color.col|")
    val lookupElements = fixture.completeBasic().toList()
    assertThat(lookupElements.map(LookupElement::getLookupString)).containsExactly("publicColor")
  }

  @Test
  fun privateResourcesNotFiltered_java() {
    val file =
      fixture
        .addFileToProject(
          "src/com/example/Foo.java",
          """
          package com.example;
          class Foo {
            public void bar() {
              int color = com.example.aarLib.R.color.col
            }
          }
          """
            .trimIndent(),
        )
        .virtualFile
    fixture.openFileInEditor(file)
    fixture.moveCaret("R.color.col|")
    val lookupElements = fixture.completeBasic().toList()
    assertThat(lookupElements.map(LookupElement::getLookupString))
      .containsExactly("publicColor", "privateColor")
  }

  @Test
  fun privateResourcesFiltered_kotlin() {
    val file =
      fixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          """
          package com.example
          fun bar() {
            val color = R.color.col
          }
          """
            .trimIndent(),
        )
        .virtualFile

    fixture.openFileInEditor(file)
    fixture.moveCaret("R.color.col|")
    val lookupElements = fixture.completeBasic().toList()
    assertThat(lookupElements.map(LookupElement::getLookupString)).containsExactly("publicColor")
  }

  @Test
  fun privateResourcesFiltered_withPackage_kotlin() {
    val file =
      fixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          """
          package com.example
          fun bar() {
            val color = com.example.R.color.col
          }
          """
            .trimIndent(),
        )
        .virtualFile

    fixture.openFileInEditor(file)
    fixture.moveCaret("R.color.col|")
    val lookupElements = fixture.completeBasic().toList()
    assertThat(lookupElements.map(LookupElement::getLookupString)).containsExactly("publicColor")
  }

  @Test
  fun privateResourcesNotFiltered_kotlin() {
    val file =
      fixture
        .addFileToProject(
          "src/com/example/Foo.kt",
          """
          package com.example
          fun bar() {
            val color = com.example.aarLib.R.color.col
          }
          """
            .trimIndent(),
        )
        .virtualFile

    fixture.openFileInEditor(file)
    fixture.moveCaret("R.color.col|")
    val lookupElements = fixture.completeBasic().toList()
    assertThat(lookupElements.map(LookupElement::getLookupString))
      .containsExactly("publicColor", "privateColor")
  }

  private fun Array<LookupElement>.toExpectedColors(): List<Int> = map { elt ->
    COLORS.entries.first { elt.lookupString.contains(it.key) }.value.rgb
  }

  private fun Iterable<Icon>.toColors(): List<Int> = map { it.sampleMiddlePoint() }

  private fun LookupElement.quickRenderedIcon(): Icon? {
    val pres = LookupElementPresentation()
    renderElement(pres)
    return pres.icon
  }

  private fun LookupElement.slowRenderedIcon(): Icon? {
    val pres = LookupElementPresentation()
    @Suppress("unchecked_cast")
    (expensiveRenderer as? LookupElementRenderer<LookupElement>)?.renderElement(this, pres)
    return pres.icon
  }

  private fun Icon.sampleMiddlePoint(): Int {
    val bufferedImage = ImageUtil.createImage(iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = bufferedImage.createGraphics()
    paintIcon(null, graphics, 0, 0)
    graphics.dispose()
    return bufferedImage.getRGB(iconWidth / 2, iconHeight / 2)
  }

  private fun Color.toHexString(): String = "#${Integer.toHexString(rgb).uppercase()}"
}

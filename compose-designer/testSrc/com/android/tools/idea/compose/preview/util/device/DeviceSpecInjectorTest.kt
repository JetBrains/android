/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util.device

import com.android.tools.idea.compose.annotator.registerLanguageExtensionPoint
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecParserDefinition
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.injection.general.LanguageInjectionContributor
import com.intellij.lang.injection.general.LanguageInjectionPerformer
import com.intellij.testFramework.fixtures.InjectionTestFixture
import kotlin.test.assertEquals
import kotlin.test.assertFails
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubPreviewAnnotation
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class DeviceSpecInjectorTest {
  @get:Rule val rule = AndroidProjectRule.inMemory()

  val fixture
    get() = rule.fixture

  private val injectionFixture: InjectionTestFixture
    get() = InjectionTestFixture(fixture)

  @Before
  fun setup() {
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.override(true)
    fixture.stubPreviewAnnotation()
    fixture.stubComposableAnnotation()
    fixture.registerLanguageExtensionPoint(
      LanguageParserDefinitions.INSTANCE,
      DeviceSpecParserDefinition(),
      DeviceSpecLanguage
    )
    fixture.registerLanguageExtensionPoint(
      LanguageInjectionContributor.INJECTOR_EXTENSION,
      DeviceSpecInjectionContributor(),
      KotlinLanguage.INSTANCE
    )
    fixture.registerLanguageExtensionPoint(
      LanguageInjectionPerformer.INJECTOR_EXTENSION,
      DeviceSpecInjectionPerformer(),
      KotlinLanguage.INSTANCE
    )
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.clearOverride()
  }

  @Test
  fun injectedForDeviceParameter() {
    rule.fixture.configureByText(
      "test.kt",
      // language=kotlin
      """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview(device = "id:device$caret name")
        @Composable
        fun myFun() {}
      """.trimIndent()
    )
    runReadAction { injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id) }
  }

  @Test
  fun notInjectedForNonDeviceParameters() {
    assertFailsOnPreviewParameter("name")
    assertFailsOnPreviewParameter("group")
    assertFailsOnPreviewParameter("locale")
  }

  @Test
  fun injectedInConcatenatedExpressionForDeviceParameter() {
    rule.fixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        const val heightDp = "841dp"
        const val specPref = "spec:"
        const val heightPx = "1900px"

        @Preview(device = "spec:width=673.5dp," + "height=" + heightDp + "" + ""${'"'},chinSize=11dp""${'"'})
        @Preview(device = specPref + "width=10dp,height=" + heightDp)
        @Preview(device = "spec:width=1080px," + "height=" + heightPx)
        @Composable
        fun preview1() {}
      """.trimIndent()
    )
    val injectedElementsAndTexts = runReadAction {
      injectionFixture.getAllInjections().map { Pair(it.first.text, it.second.text) }
    }
    assertEquals(3, injectedElementsAndTexts.size)
    // Assert the text of the elements marked for Injection
    assertEquals(""""spec:width=673.5dp,"""", injectedElementsAndTexts[0].first)
    assertEquals(""""width=10dp,height="""", injectedElementsAndTexts[1].first)
    assertEquals(""""spec:width=1080px,"""", injectedElementsAndTexts[2].first)

    // Assert the contents of the Injected file, should reflect the resolved text in the `device`
    // parameter
    assertEquals(
      "spec:width=673.5dp,height=841dp,chinSize=11dp",
      injectedElementsAndTexts[0].second
    )
    assertEquals("spec:width=10dp,height=841dp", injectedElementsAndTexts[1].second)
    assertEquals("spec:width=1080px,height=1900px", injectedElementsAndTexts[2].second)
  }

  @Test
  fun onlyOneExpressionInjectedInConcatenatedDeviceSpec() {
    rule.fixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        const val heightPx = "1900px"

        @Preview(device = "spec$caret:width=1080px," + "height=" + heightPx)
        @Composable
        fun preview1() {}
      """.trimIndent()
    )
    runReadAction { injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id) }

    rule.fixture.configureByText(
      KotlinFileType.INSTANCE,
      // language=kotlin
      """
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        const val heightPx = "1900px"

        @Preview(device = "spec:width=1080px," + "height$caret=" + heightPx)
        @Composable
        fun preview1() {}
      """.trimIndent()
    )
    runReadAction {
      assertFails { injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id) }
    }
  }

  private fun assertFailsOnPreviewParameter(parameterName: String) {
    require(parameterName != "device")
    rule.fixture.configureByText(
      "test.kt",
      // language=kotlin
      """
        package example
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.Composable

        @Preview($parameterName = "id:device$caret name")
        @Composable
        fun myFun() {}
      """.trimIndent()
    )
    runReadAction {
      assertFails { injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id) }
    }
  }
}

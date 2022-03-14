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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.LanguageInjector
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubPreviewAnnotation
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFails

internal class DeviceSpecInjectorTest {
  @get:Rule
  val rule = AndroidProjectRule.inMemory()

  val fixture get() = rule.fixture

  val injectionFixture: InjectionTestFixture
    get() = InjectionTestFixture(fixture)

  @Before
  fun setup() {
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.override(true)
    fixture.stubPreviewAnnotation()
    fixture.stubComposableAnnotation()
    fixture.registerLanguageExtensionPoint(LanguageParserDefinitions.INSTANCE, DeviceSpecParserDefinition(), DeviceSpecLanguage)
    ApplicationManager.getApplication().extensionArea.getExtensionPoint(LanguageInjector.EXTENSION_POINT_NAME).registerExtension(
      DeviceSpecInjector(),
      fixture.testRootDisposable
    )
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.clearOverride()
  }

  @Test
  fun languageIsInjectedInDevice() {
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
    runReadAction {
      injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id)
    }
  }

  @Test
  fun languageNotInjectedInNonDevice() {
    assertFailsOnPreviewParameter("name")
    assertFailsOnPreviewParameter("group")
    assertFailsOnPreviewParameter("locale")
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
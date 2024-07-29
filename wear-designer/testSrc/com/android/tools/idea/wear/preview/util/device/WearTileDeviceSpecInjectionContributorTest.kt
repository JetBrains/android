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
package com.android.tools.idea.wear.preview.util.device

import com.android.tools.idea.preview.util.device.DeviceSpecLanguage
import com.android.tools.idea.testing.caret
import com.android.tools.idea.wear.preview.WearTileProjectRule
import com.intellij.lang.injection.general.LanguageInjectionContributor
import com.intellij.openapi.application.runReadAction
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WearTileDeviceSpecInjectionContributorTest {

  @get:Rule val projectRule = WearTileProjectRule()

  private val fixture
    get() = projectRule.fixture

  private val injectionFixture: InjectionTestFixture
    get() = InjectionTestFixture(fixture)

  private val injectionContributor = WearTileDeviceSpecInjectionContributor()

  @Before
  fun setup() {
    fixture.addFileToProject(
      "src/invalid/Preview.kt",
      // language=kotlin
      """
          package invalid

          annotation class Preview(val device: String = "")
        """
        .trimIndent(),
    )

    LanguageInjectionContributor.INJECTOR_EXTENSION.addExplicitExtension(
      KotlinLanguage.INSTANCE,
      injectionContributor,
    )
  }

  @Test
  fun testInjected_kotlin() {
    fixture.configureByText(
      "Test.kt",
      // language=kotlin
      """
        import androidx.wear.tiles.tooling.preview.Preview

        @Preview(device = "tile preview $caret annotation")
        fun validPreview() {
        }
      """
        .trimIndent(),
    )

    runReadAction { injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id) }

    fixture.configureByText(
      "Test.kt",
      // language=kotlin
      """
        @invalid.Preview(device = "invalid $caret annotation")
        fun invalidPreview() {
        }
      """
        .trimIndent(),
    )

    runReadAction { injectionFixture.assertInjectedLangAtCaret(null) }
  }

  @Test
  fun testInjected_java() {
    fixture.configureByText(
      "Test.java",
      // language=JAVA
      """
        import androidx.wear.tiles.tooling.preview.Preview;

        class Test {
          @Preview(device = "tile preview $caret annotation")
          void validPreview() {
          }
        }
      """
        .trimIndent(),
    )

    runReadAction { injectionFixture.assertInjectedLangAtCaret(DeviceSpecLanguage.id) }

    fixture.configureByText(
      "Test.java",
      // language=JAVA
      """
        import androidx.wear.tiles.tooling.preview.Preview;

        class Test {
          @invalid.Preview(device = "invalid $caret annotation")
          void invalidPreview() {
          }
        }
      """
        .trimIndent(),
    )

    runReadAction { injectionFixture.assertInjectedLangAtCaret(null) }
  }
}

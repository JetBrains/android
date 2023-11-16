/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.editors.strings

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.loadNewFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.Rule
import org.junit.Test

class TranslationsNotificationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val myFixture: CodeInsightTestFixture by lazy { projectRule.fixture }

  @Test
  fun checkStringsXml() {
    val psiFile = myFixture.loadNewFile(
      "src/res/values/strings.xml",
      // language=xml
      """
      <resources>
          <string name="key_1">key_2_default</string>
      </resources>
      """.trimIndent()
    )
    assertThat(canViewTranslations(psiFile)).isTrue()
  }

  @Test
  fun checkColorXml() {
    val psiFile = myFixture.loadNewFile(
      "src/res/values/colors.xml",
      // language=xml
      """
      <resources>
          <color name="color2">#008577</color>
      </resources>
      """.trimIndent()
    )
    assertThat(canViewTranslations(psiFile)).isFalse()
  }

  @Test
  fun checkMiscXml() {
    val psiFile = myFixture.loadNewFile(
      "src/res/values/misc.xml",
      // language=xml
      """
      <resources>
          <color name="color2">#008577</color>
          <string name="key_2">key_2_default</string>
      </resources>
      """.trimIndent()
    )
    assertThat(canViewTranslations(psiFile)).isTrue()
  }

  private fun canViewTranslations(psiFile: PsiFile): Boolean {
    return runReadAction { StringResourceEditorProvider.canViewTranslations(projectRule.project, psiFile.virtualFile) }
  }
}
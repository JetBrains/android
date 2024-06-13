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
package com.android.tools.compose.templates

import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.loadNewFile
import com.intellij.codeInsight.template.impl.InvokeTemplateAction
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

class AndroidComposePreviewTest : JavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.testRootDisposable)
    TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
  }

  fun testPrevTemplate() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      $caret
      """
        .trimIndent(),
    )

    val template = TemplateSettings.getInstance().getTemplate("prev", "AndroidComposePreview")
    InvokeTemplateAction(template, myFixture.editor, project, HashSet()).perform()

    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      @androidx.compose.ui.tooling.preview.Preview
      @androidx.compose.runtime.Composable
      private fun () {
          
      }
      """
        .trimIndent()
    )
  }

  fun testPrevColTemplate() {
    myFixture.loadNewFile(
      "src/com/example/Test.kt",
      // language=kotlin
      """
      package com.example

      $caret
      """
        .trimIndent(),
    )

    val template = TemplateSettings.getInstance().getTemplate("prevCol", "AndroidComposePreview")
    InvokeTemplateAction(template, myFixture.editor, project, HashSet()).perform()

    myFixture.checkResult(
      // language=kotlin
      """
      package com.example

      class  : CollectionPreviewParameterProvider<>(listOf())
      """
        .trimIndent()
    )
  }
}

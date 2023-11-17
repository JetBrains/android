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
package com.android.tools.compose.code.state

import com.android.tools.compose.ComposeBundle
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings
import com.intellij.psi.PsiFile
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests the [EnableComposeStateReadInlayHintsAction]. */
@RunWith(JUnit4::class)
class EnableComposeStateReadInlayHintsActionTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val project by lazy { projectRule.project }
  private val fixture by lazy { projectRule.fixture }
  private var originalHintsSetting = false
  private lateinit var psiFile: PsiFile

  @Before
  fun setUp() {
    originalHintsSetting = areHintsAvailable()

    // Turns out we need a file to even call the API, so make one.
    psiFile = fixture.configureByText("foo.txt", "foo")
  }

  @After
  fun tearDown() {
    setHintsAvailable(originalHintsSetting)
  }

  @Test
  fun getText() {
    assertThat(EnableComposeStateReadInlayHintsAction.text)
      .isEqualTo(ComposeBundle.message("state.read.inlay.provider.enable"))
  }

  @Test
  fun isAvailable() {
    setHintsAvailable(false)
    assertThat(EnableComposeStateReadInlayHintsAction.isAvailable(project, fixture.editor, psiFile))
      .isTrue()
    setHintsAvailable(true)
    assertThat(EnableComposeStateReadInlayHintsAction.isAvailable(project, fixture.editor, psiFile))
      .isFalse()
    setHintsAvailable(false)
    assertThat(EnableComposeStateReadInlayHintsAction.isAvailable(project, fixture.editor, psiFile))
      .isTrue()
    setHintsAvailable(true)
    assertThat(EnableComposeStateReadInlayHintsAction.isAvailable(project, fixture.editor, psiFile))
      .isFalse()
  }

  @Test
  fun invoke() {
    setHintsAvailable(false)
    EnableComposeStateReadInlayHintsAction.invoke(project, fixture.editor, psiFile)
    assertThat(areHintsAvailable()).isTrue()
    setHintsAvailable(false)
    EnableComposeStateReadInlayHintsAction.invoke(project, fixture.editor, psiFile)
    assertThat(areHintsAvailable()).isTrue()
  }

  private fun setHintsAvailable(enabled: Boolean) {
    DeclarativeInlayHintsSettings.getInstance()
      .setProviderEnabled(ComposeStateReadInlayHintsProvider.PROVIDER_ID, enabled)
  }

  private fun areHintsAvailable() =
    DeclarativeInlayHintsSettings.getInstance()
      .isProviderEnabled(ComposeStateReadInlayHintsProvider.PROVIDER_ID) ?: false
}

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
package com.android.tools.idea.logcat

import com.android.tools.idea.logcat.filters.parser.LogcatFilterFileType
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [FilterTextField]
 */
@RunsInEdt
class FilterTextFieldTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  @Test
  fun constructor() {
    val filterTextField = FilterTextField(projectRule.project, FakeLogcatPresenter(), text = "text")

    assertThat(filterTextField.text).isEqualTo("text")
    assertThat(filterTextField.fileType).isEqualTo(LogcatFilterFileType)
  }

  @Test
  fun createEditor_putsUserData() {
    val logcatPresenter = FakeLogcatPresenter()
    val filterTextField = FilterTextField(projectRule.project, logcatPresenter, text = "")

    val editor = filterTextField.createEditor()

    assertThat(editor.getUserData(TAGS_PROVIDER_KEY)).isEqualTo(logcatPresenter)
    assertThat(editor.getUserData(PACKAGE_NAMES_PROVIDER_KEY)).isEqualTo(logcatPresenter)

    EditorFactory.getInstance().releaseEditor(editor)
  }
}
/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.sqlite.settings

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Tests for [ClassPickerCompletionContributor] */
class ClassPickerCompletionContributorTest {
  private val projectRule = AndroidProjectRule.inMemory()

  @get:Rule val chain: RuleChain = RuleChain(projectRule)

  private val fixture: CodeInsightTestFixture by lazy(projectRule::fixture)

  @Before
  fun setUp() {
    projectRule.setupClasses()
  }

  @Test
  fun complete_driver() {
    fixture.configure("", "androidx.sqlite.SQLiteDriver")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings)
      .containsExactly("com.app.SQLiteDriver", "org.app.SQLiteDriver")
  }

  @Test
  fun complete_connection() {
    fixture.configure("", "androidx.sqlite.SQLiteConnection")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings)
      .containsExactly("com.app.SQLiteConnection", "org.app.SQLiteConnection")
  }

  @Test
  fun complete_prefix() {
    fixture.configure("org$caret", "androidx.sqlite.SQLiteConnection")

    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings).isNull()
    assertThat(fixture.editor.document.text).isEqualTo("org.app.SQLiteConnection")
  }
}

private fun CodeInsightTestFixture.configure(text: String, base: String) {
  configureByText(PlainTextFileType.INSTANCE, text)
  // This can't be done in the setUp() method because the editor is only created when the fixture is
  // configured.
  editor.putUserData(ClassPicker.BASE_CLASS_KEY, base)
}

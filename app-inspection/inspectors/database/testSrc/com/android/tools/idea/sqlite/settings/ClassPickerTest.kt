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

import com.android.tools.adtui.swing.getDescendant
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.TreeClassChooser
import com.intellij.ide.util.TreeClassChooserFactory
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.ProjectScope
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests for [ClassPicker] */
@RunsInEdt
class ClassPickerTest {
  private val projectRule = AndroidProjectRule.inMemory()
  private val project
    get() = projectRule.project

  private val disposable
    get() = projectRule.testRootDisposable

  @get:Rule val chain: RuleChain = RuleChain(projectRule, EdtRule())

  @Before
  fun setUp() {
    projectRule.setupClasses()
  }

  @Test
  fun empty_noError() {
    val picker = ClassPicker(project, "androidx.sqlite.SQLiteDriver")

    assertThat(picker.errorLabel().isVisible).isFalse()
  }

  @Test
  fun noClass_error() {
    val picker = ClassPicker(project, "androidx.sqlite.SQLiteDriver")

    picker.text = "UnknownClass"

    assertThat(picker.errorLabel().isVisible).isTrue()
    assertThat(picker.errorLabel().text).isEqualTo("Class not found in project.")
  }

  @Test
  fun wrongType_error() {
    val picker = ClassPicker(project, "androidx.sqlite.SQLiteDriver")

    picker.text = "org.app.SQLiteConnection"

    assertThat(picker.errorLabel().isVisible).isTrue()
    assertThat(picker.errorLabel().text).isEqualTo("Class is not a SQLiteDriver.")
  }

  @Test
  fun rightType_noError() {
    val picker = ClassPicker(project, "androidx.sqlite.SQLiteDriver")

    picker.text = "org.app.SQLiteDriver"

    assertThat(picker.errorLabel().isVisible).isFalse()
  }

  @Test
  fun fixError_removesError() {
    val picker = ClassPicker(project, "androidx.sqlite.SQLiteDriver")
    picker.text = "UnknownClass"
    assertThat(picker.errorLabel().isVisible).isTrue()

    picker.text = "org.app.SQLiteDriver"

    assertThat(picker.errorLabel().isVisible).isFalse()
  }

  @Test
  fun clickBrowse() {
    val mockChooserFactory = mock<TreeClassChooserFactory>()
    val mockChooser = mock<TreeClassChooser>()
    project.replaceService(TreeClassChooserFactory::class.java, mockChooserFactory, disposable)
    whenever(
        mockChooserFactory.createInheritanceClassChooser(
          eq("Select SQLiteDriver Class"),
          eq(ProjectScope.getAllScope(project)),
          eq(findClass("androidx.sqlite.SQLiteDriver")),
          anyOrNull(),
        )
      )
      .thenReturn(mockChooser)
    whenever(mockChooser.selected).thenReturn(findClass("com.app.SQLiteDriver"))
    val picker = ClassPicker(project, "androidx.sqlite.SQLiteDriver")

    picker.browseButton().doClick()

    verify(mockChooser).showDialog()
    assertThat(picker.text).isEqualTo("com.app.SQLiteDriver")
  }

  @Test
  fun enabled_propagates() {
    val picker = ClassPicker(project, "androidx.sqlite.SQLiteDriver")

    picker.isEnabled = false
    assertThat(picker.textComponent().isEnabled).isFalse()

    picker.isEnabled = true
    assertThat(picker.textComponent().isEnabled).isTrue()
  }

  private fun findClass(name: String): PsiClass? {
    val facade = JavaPsiFacade.getInstance(project)
    val scope = ProjectScope.getAllScope(project)
    return facade.findClass(name, scope)
  }
}

private fun ClassPicker.textComponent() = getDescendant<JComponent> { it.name == "textComponent" }

private fun ClassPicker.errorLabel() = getDescendant<JLabel> { it.name == "errorLabel" }

private fun ClassPicker.browseButton() = getDescendant<JButton>()

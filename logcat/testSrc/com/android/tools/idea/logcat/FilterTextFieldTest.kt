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

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.logcat.FilterTextField.Companion.HISTORY_PROPERTY_NAME
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_ENTER
import javax.swing.ComboBoxModel
import javax.swing.JLabel
import javax.swing.KeyStroke

/**
 * Tests for [FilterTextField]
 */
@RunsInEdt
class FilterTextFieldTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  private val properties by lazy { PropertiesComponent.getInstance() }

  @After
  fun tearDown() {
    properties.setValues(HISTORY_PROPERTY_NAME, null)
  }

  @Test
  fun constructor_setsText() {
    val filterTextField = FilterTextField(projectRule.project, FakeLogcatPresenter(), initialText = "text")

    assertThat(filterTextField.text).isEqualTo("text")
  }

  @Test
  fun constructor_setsHistory() {
    properties.setValues(HISTORY_PROPERTY_NAME, arrayOf("foo", "bar"))
    val filterTextField = FilterTextField(projectRule.project, FakeLogcatPresenter(), initialText = "text")

    assertThat(filterTextField.model.getItems()).containsExactly(
      "text",
      "bar",
      "foo",
    ).inOrder()
  }

  @Test
  fun constructor_emptyText() {
    properties.setValues(HISTORY_PROPERTY_NAME, arrayOf("foo", "bar"))
    val filterTextField = FilterTextField(projectRule.project, FakeLogcatPresenter(), initialText = "")

    assertThat(filterTextField.text).isEqualTo("")
    assertThat(filterTextField.model.getItems()).containsExactly(
      "bar",
      "foo",
    ).inOrder()
  }

  @Test
  fun createEditor_putsUserData() {
    val editorFactory = EditorFactory.getInstance()
    val logcatPresenter = FakeLogcatPresenter()
    val filterTextField = FilterTextField(projectRule.project, logcatPresenter, initialText = "")
    filterTextField.addNotify() // Creates editor

    val editor = filterTextField.getEditorEx()

    assertThat(editor.getUserData(TAGS_PROVIDER_KEY)).isEqualTo(logcatPresenter)
    assertThat(editor.getUserData(PACKAGE_NAMES_PROVIDER_KEY)).isEqualTo(logcatPresenter)
    editorFactory.releaseEditor(editor)
  }

  @Test
  fun pressEnter_addsToHistory() {
    val filterTextField = FilterTextField(projectRule.project, FakeLogcatPresenter(), initialText = "")
    filterTextField.addNotify() // Creates editor
    filterTextField.text = "foo"

    filterTextField.processKeyBinding(
      KeyStroke.getKeyStroke('\n'),
      KeyEvent(filterTextField, 1, 0, 0, VK_ENTER, '\n'),
      condition = 0,
      pressed = true)

    assertThat(filterTextField.model.getItems()).containsExactly("foo")
  }

  @Test
  fun openPopup_addsToHistory() {
    val filterTextField = FilterTextField(projectRule.project, FakeLogcatPresenter(), initialText = "")
    filterTextField.addNotify() // Creates editor
    filterTextField.text = "foo"

    filterTextField.firePopupMenuWillBecomeVisible()

    assertThat(filterTextField.model.getItems()).containsExactly("foo")
  }

  @Test
  fun history_size() {
    val filterTextField = FilterTextField(projectRule.project, FakeLogcatPresenter(), initialText = "", maxHistorySize = 3)
    filterTextField.addNotify() // Creates editor

    for (text in listOf("foo1", "foo2", "foo3", "foo4")) {
      filterTextField.text = text
      filterTextField.firePopupMenuWillBecomeVisible()
    }

    assertThat(filterTextField.model.getItems()).containsExactly(
      "foo4",
      "foo3",
      "foo2",
    ).inOrder()
  }

  @Test
  fun history_bubbles() {
    val filterTextField = FilterTextField(projectRule.project, FakeLogcatPresenter(), initialText = "", maxHistorySize = 3)
    filterTextField.addNotify() // Creates editor

    for (text in listOf("foo1", "foo2", "foo3", "foo1")) {
      filterTextField.text = text
      filterTextField.firePopupMenuWillBecomeVisible()
    }

    assertThat(filterTextField.model.getItems()).containsExactly(
      "foo1",
      "foo3",
      "foo2",
    ).inOrder()
  }

  @Test
  fun clickClear() {
    val filterTextField = FilterTextField(projectRule.project, FakeLogcatPresenter(), initialText = "foo")
    filterTextField.size = Dimension(100, 100)
    val fakeUi = FakeUi(filterTextField)
    val clearButton = fakeUi.getComponent<JLabel> { true }

    fakeUi.clickOn(clearButton)

    assertThat(filterTextField.text).isEmpty()
  }
}

private fun <T> ComboBoxModel<T>.getItems(): List<T> {
  val list = mutableListOf<T>()
  for (i in 0 until size) {
    list.add(getElementAt(i))
  }
  return list
}
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
package com.android.tools.idea.logcat

import com.android.tools.idea.logcat.util.createLogcatEditor
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.editorActions.TextBlockTransferable
import com.intellij.execution.ui.ConsoleViewContentType.USER_INPUT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnAction.ACTIONS_KEY
import com.intellij.openapi.actionSystem.CommonShortcuts.ENTER
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_BACKSPACE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_DELETE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_PASTE
import com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_TAB
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.ui.ClientProperty
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.awt.event.KeyEvent

/**
 * Tests for [UserInputHandlers]
 */
@RunsInEdt
class UserInputHandlersTest {
  private val projectRule = ProjectRule()

  @get:Rule
  val rule = RuleChain(projectRule, EdtRule())

  private val editor by lazy { createLogcatEditor(projectRule.project) }

  @After
  fun tearDown() {
    EditorFactory.getInstance().releaseEditor(editor)
  }

  @Test
  fun typeText_appendsToEnd() {
    UserInputHandlers(editor).install()

    editor.caretModel.moveToOffset(0)
    editor.append("line1\n")
    editor.type("foo")

    assertThat(editor.document.text).isEqualTo("line1\nfoo")
    assertThat(editor.getUserInputAreas()).containsExactly("foo")
  }

  @Test
  fun typeText_multipleRegions() {
    UserInputHandlers(editor).install()

    editor.caretModel.moveToOffset(0)
    editor.append("line1\n")
    editor.type("foo")
    editor.append("line2\n")
    editor.type("bar")

    assertThat(editor.document.text).isEqualTo("line1\nfooline2\nbar")
    assertThat(editor.getUserInputAreas()).containsExactly("foo", "bar")
  }

  @Test
  fun typeText_replacesSelection() {
    UserInputHandlers(editor).install()

    editor.type("12345")
    editor.selectionModel.setSelection(2, 3)
    editor.type("-foo-")

    assertThat(editor.document.text).isEqualTo("12-foo-45")
    assertThat(editor.getUserInputAreas()).containsExactly("12-foo-45")
  }

  @Test
  fun typeText_selectionNotInUserInput_appendsToEnd() {
    UserInputHandlers(editor).install()

    editor.append("123")
    editor.type("456")
    editor.selectionModel.setSelection(2, 4)
    editor.type("-foo-")

    assertThat(editor.document.text).isEqualTo("123456-foo-")
    assertThat(editor.getUserInputAreas()).containsExactly("456-foo-")
  }

  @Test
  fun typeEnter() {
    UserInputHandlers(editor).install()

    editor.append("line1\n")
    editor.type("foo")
    editor.pressEnter()

    assertThat(editor.document.text).isEqualTo("line1\nfoo\n")
    assertThat(editor.getUserInputAreas()).containsExactly("foo\n")
  }

  @Test
  fun typeEnter_inSelection() {
    UserInputHandlers(editor).install()

    editor.type("12345")
    editor.selectionModel.setSelection(2, 3)
    editor.pressEnter()

    assertThat(editor.document.text).isEqualTo("12\n45")
    assertThat(editor.getUserInputAreas()).containsExactly("12\n45")
  }

  @Test
  fun typeTab() {
    UserInputHandlers(editor).install()

    editor.append("line1\n")
    editor.type("foo")
    editor.pressTab()
    editor.type("bar")

    assertThat(editor.document.text).isEqualTo("line1\nfoo\tbar")
    assertThat(editor.getUserInputAreas()).containsExactly("foo\tbar")
  }

  @Test
  fun typeTab_inSelection() {
    UserInputHandlers(editor).install()

    editor.type("12345")
    editor.selectionModel.setSelection(2, 3)
    editor.pressTab()

    assertThat(editor.document.text).isEqualTo("12\t45")
    assertThat(editor.getUserInputAreas()).containsExactly("12\t45")
  }

  @Test
  fun typeBackspace_atEnd() {
    UserInputHandlers(editor).install()

    editor.type("12345")
    editor.pressBackspace()

    assertThat(editor.document.text).isEqualTo("1234")
    assertThat(editor.getUserInputAreas()).containsExactly("1234")
  }

  @Test
  fun typeBackspace_inMiddle() {
    UserInputHandlers(editor).install()

    editor.type("12345")
    editor.caretModel.moveToOffset(3)
    editor.pressBackspace()

    assertThat(editor.document.text).isEqualTo("1245")
    assertThat(editor.getUserInputAreas()).containsExactly("1245")
  }

  @Test
  fun typeBackspace_atStart() {
    UserInputHandlers(editor).install()

    editor.append("text")
    editor.type("12345")
    editor.caretModel.moveToOffset(4)
    editor.pressBackspace()

    assertThat(editor.document.text).isEqualTo("text12345")
    assertThat(editor.getUserInputAreas()).containsExactly("12345")
  }

  @Test
  fun typeBackspace_notInUserInput() {
    UserInputHandlers(editor).install()

    editor.append("text")
    editor.type("12345")
    editor.caretModel.moveToOffset(2)
    editor.pressBackspace()

    assertThat(editor.document.text).isEqualTo("text12345")
    assertThat(editor.getUserInputAreas()).containsExactly("12345")
  }

  @Test
  fun typeBackspace_inSelection() {
    UserInputHandlers(editor).install()

    editor.type("12345")
    editor.selectionModel.setSelection(2, 4)
    editor.pressBackspace()

    assertThat(editor.document.text).isEqualTo("125")
    assertThat(editor.getUserInputAreas()).containsExactly("125")
  }

  @Test
  fun typeBackspace_selectionNotInUserInput() {
    UserInputHandlers(editor).install()

    editor.append("text")
    editor.type("12345")
    editor.selectionModel.setSelection(2, 7)
    editor.pressBackspace()

    assertThat(editor.document.text).isEqualTo("text12345")
    assertThat(editor.getUserInputAreas()).containsExactly("12345")
  }

  @Test
  fun typeDelete_atStart() {
    UserInputHandlers(editor).install()

    editor.type("12345")
    editor.caretModel.moveToOffset(0)
    editor.pressDelete()

    assertThat(editor.document.text).isEqualTo("2345")
    assertThat(editor.getUserInputAreas()).containsExactly("2345")
  }

  @Test
  fun typeDelete_inMiddle() {
    UserInputHandlers(editor).install()

    editor.type("12345")
    editor.caretModel.moveToOffset(3)
    editor.pressDelete()

    assertThat(editor.document.text).isEqualTo("1235")
    assertThat(editor.getUserInputAreas()).containsExactly("1235")
  }

  @Test
  fun typeDelete_atEnd() {
    UserInputHandlers(editor).install()

    editor.append("text")
    editor.type("12345")
    editor.pressDelete()

    assertThat(editor.document.text).isEqualTo("text12345")
    assertThat(editor.getUserInputAreas()).containsExactly("12345")
  }

  @Test
  fun typeDelete_notInUserInput() {
    UserInputHandlers(editor).install()

    editor.append("text")
    editor.type("12345")
    editor.caretModel.moveToOffset(2)
    editor.pressDelete()

    assertThat(editor.document.text).isEqualTo("text12345")
    assertThat(editor.getUserInputAreas()).containsExactly("12345")
  }

  @Test
  fun typeDelete_inSelection() {
    UserInputHandlers(editor).install()

    editor.type("12345")
    editor.selectionModel.setSelection(2, 4)
    editor.pressDelete()

    assertThat(editor.document.text).isEqualTo("125")
    assertThat(editor.getUserInputAreas()).containsExactly("125")
  }

  @Test
  fun typeDelete_selectionNotInUserInput() {
    UserInputHandlers(editor).install()

    editor.append("text")
    editor.type("12345")
    editor.selectionModel.setSelection(2, 7)
    editor.pressDelete()

    assertThat(editor.document.text).isEqualTo("text12345")
    assertThat(editor.getUserInputAreas()).containsExactly("12345")
  }

  @Test
  fun paste() {
    UserInputHandlers(editor).install()

    editor.append("line1\n")
    editor.paste("foo")

    assertThat(editor.document.text).isEqualTo("line1\nfoo")
    assertThat(editor.getUserInputAreas()).containsExactly("foo")
  }

  @Test
  fun paste_inSelection() {
    UserInputHandlers(editor).install()

    editor.type("12345")
    editor.selectionModel.setSelection(2, 3)
    editor.paste("foo")

    assertThat(editor.document.text).isEqualTo("12foo45")
    assertThat(editor.getUserInputAreas()).containsExactly("12foo45")
  }

  private fun EditorEx.getActions() =
    ClientProperty.get(contentComponent, ACTIONS_KEY) ?: throw IllegalStateException("ACTIONS_KEY not found")

  private fun EditorEx.pressEnter() {
    getActions().first { it.shortcutSet == ENTER }.actionPerformed(TestActionEvent())
  }

  private fun EditorEx.pressTab() {
    getActions().performAction(ACTION_EDITOR_TAB)
  }

  private fun EditorEx.pressBackspace() {
    getActions().performAction(ACTION_EDITOR_BACKSPACE)
  }

  private fun EditorEx.pressDelete() {
    getActions().performAction(ACTION_EDITOR_DELETE)
  }

  private fun EditorEx.paste(text: String) {
    CopyPasteManager.getInstance().setContents(TextBlockTransferable(text, emptyList(), null))
    getActions().performAction(ACTION_EDITOR_PASTE)
  }

  private fun EditorEx.getUserInputAreas(): List<String> {
    val markupModel = DocumentMarkupModel.forDocument(document, projectRule.project, false)
    return markupModel.allHighlighters.filter { it.textAttributesKey == USER_INPUT.attributesKey }
      .map { document.text.substring(it.startOffset, it.endOffset) }
  }
}

private fun EditorEx.type(text: String) {
  text.forEach { char ->
    contentComponent.keyListeners.forEach {
      it.keyTyped(KeyEvent(contentComponent, 0, 0, 0, 0, char))
    }
  }
}

private fun EditorEx.append(text: String) {
  document.insertString(document.textLength, text)
}

private fun List<AnAction>.performAction(id: String) {
  val shortcuts = KeymapManager.getInstance().activeKeymap.getShortcuts(id)
  first { it.shortcutSet.shortcuts.contentEquals(shortcuts) }.actionPerformed(TestActionEvent())
}
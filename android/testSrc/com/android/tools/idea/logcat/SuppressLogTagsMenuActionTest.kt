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

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import javax.swing.JButton
import javax.swing.JCheckBox

class SuppressLogTagsMenuActionTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  @get:Rule
  val mockitoRule: MockitoRule = MockitoJUnit.rule()

  @Mock
  private lateinit var mockRefresh: Runnable

  private lateinit var preferences: AndroidLogcatGlobalPreferences

  private val suppressLogTagsMenuAction by lazy { SuppressLogTagsMenuAction(mockRefresh) }

  @Before
  fun setUp() {
    enableHeadlessDialogs(projectRule.fixture.testRootDisposable)

    preferences = ApplicationManager.getApplication().getService(AndroidLogcatGlobalPreferences::class.java)
  }

  @After
  fun tearDown() {
    preferences.suppressedLogTags.clear()
  }

  @Test
  fun actionPerformed_noSelection() {
    val
      logcatText = """
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag1: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag2: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag3: This is a sample message"
            """.trimIndent()
    val event = createEvent(logcatText, offset = logcatText.length / 2)

    suppressLogTagsMenuAction.actionPerformed(event)

    assertThat(preferences.suppressedLogTags).containsExactly("Tag2")
    verify(mockRefresh).run()
  }

  @Test
  fun actionPerformed_selectionWithSingleTag() {
    val
      logcatText = """
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag1: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag1: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag1: This is a sample message"
            """.trimIndent()
    val event = createEvent(logcatText, selectionStart = 5, selectionEnd = logcatText.length - 5)

    suppressLogTagsMenuAction.actionPerformed(event)

    assertThat(preferences.suppressedLogTags).containsExactly("Tag1")
    verify(mockRefresh).run()
  }

  @RunsInEdt
  @Test
  fun actionPerformed_multipleTags_showsDialog() {
    val
      logcatText = """
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag1: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag2: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag3: This is a sample message"
            """.trimIndent()
    val event = createEvent(logcatText, selectionStart = 5, selectionEnd = logcatText.length - 5)

    createModalDialogAndInteractWithIt({ suppressLogTagsMenuAction.actionPerformed(event) }) { dialog ->
      assertThat(getCheckBoxes(dialog).map { it.text }).containsExactly("Tag1", "Tag2", "Tag3").inOrder()
    }
  }

  @RunsInEdt
  @Test
  fun actionPerformed_apply() {
    val
      logcatText = """
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag1: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag2: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag3: This is a sample message"
            """.trimIndent()
    val event = createEvent(logcatText, selectionStart = 5, selectionEnd = logcatText.length - 5)

    createModalDialogAndInteractWithIt({ suppressLogTagsMenuAction.actionPerformed(event) }) { dialog ->
      clickButton(dialog, "OK")
    }

    assertThat(preferences.suppressedLogTags).containsExactly("Tag1", "Tag2", "Tag3")
    verify(mockRefresh).run()
  }

  @RunsInEdt
  @Test
  fun actionPerformed_cancel() {
    val
      logcatText = """
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag1: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag2: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag3: This is a sample message"
            """.trimIndent()
    val event = createEvent(logcatText, selectionStart = 5, selectionEnd = logcatText.length - 5)

    createModalDialogAndInteractWithIt({ suppressLogTagsMenuAction.actionPerformed(event) }) { dialog ->
      clickButton(dialog, "Cancel")
    }

    assertThat(preferences.suppressedLogTags).isEmpty()
    verify(mockRefresh, never()).run()
  }

  @RunsInEdt
  @Test
  fun actionPerformed_unselectAllAndApply() {
    val
      logcatText = """
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag1: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag2: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag3: This is a sample message"
            """.trimIndent()
    val event = createEvent(logcatText, selectionStart = 5, selectionEnd = logcatText.length - 5)

    createModalDialogAndInteractWithIt({ suppressLogTagsMenuAction.actionPerformed(event) }) { dialog ->
      getCheckBoxes(dialog).forEach { it.isSelected = false }
      clickButton(dialog, "OK")
    }

    assertThat(preferences.suppressedLogTags).isEmpty()
    verify(mockRefresh, never()).run()
  }

  @RunsInEdt
  @Test
  fun actionPerformed_unselectSomeAndApply() {
    val
      logcatText = """
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag1: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag2: This is a sample message"
            "2018-02-06 14:16:28.555 123-456/com.android.sample I/Tag3: This is a sample message"
            """.trimIndent()
    val event = createEvent(logcatText, selectionStart = 1, selectionEnd = logcatText.length - 10)

    createModalDialogAndInteractWithIt({ suppressLogTagsMenuAction.actionPerformed(event) }) { dialog ->
      getCheckBoxes(dialog).first { it.text == "Tag2" }.isSelected = false
      clickButton(dialog, "OK")
    }

    assertThat(preferences.suppressedLogTags).containsExactly("Tag1", "Tag3")
    verify(mockRefresh).run()
  }

  private fun createEvent(logcatText: String, offset: Int): AnActionEvent {
    val event = createEvent(logcatText)
    val editor = event.getData(CommonDataKeys.EDITOR)!!

    `when`(editor.selectionModel.hasSelection()).thenReturn(false)
    `when`(editor.caretModel.offset).thenReturn(offset)

    return event
  }

  private fun createEvent(logcatText: String, selectionStart: Int, selectionEnd: Int): AnActionEvent {
    val event = createEvent(logcatText)
    val selectionModel = event.getData(CommonDataKeys.EDITOR)!!.selectionModel

    `when`(selectionModel.hasSelection()).thenReturn(true)
    `when`(selectionModel.selectionStart).thenReturn(selectionStart)
    `when`(selectionModel.selectionEnd).thenReturn(selectionEnd)

    return event
  }

  private fun createEvent(logcatText: String): AnActionEvent {
    val event = mock(AnActionEvent::class.java)
    val editor = mock(Editor::class.java)
    val selectionModel = mock(SelectionModel::class.java)
    val caretModel = mock(CaretModel::class.java)

    `when`(event.getData(CommonDataKeys.EDITOR)).thenReturn(editor)
    `when`(event.getData(CommonDataKeys.PROJECT)).thenReturn(projectRule.project)
    `when`(editor.document).thenReturn(DocumentImpl(logcatText))
    `when`(editor.selectionModel).thenReturn(selectionModel)
    `when`(editor.caretModel).thenReturn(caretModel)

    return event
  }

  private fun getCheckBoxes(dialog: DialogWrapper): List<JCheckBox> {
    return TreeWalker(dialog.rootPane).descendants().filterIsInstance<JCheckBox>()
  }

  private fun clickButton(dialog: DialogWrapper, label: String) {
    TreeWalker(dialog.rootPane).descendants().filterIsInstance<JButton>().first { it.text == label }.doClick()
  }
}
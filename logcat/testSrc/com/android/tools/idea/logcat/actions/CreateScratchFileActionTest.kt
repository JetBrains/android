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
package com.android.tools.idea.logcat.actions

import com.android.tools.idea.logcat.actions.CreateScratchFileAction.Companion.findEmbeddedData
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.LOGCAT_MESSAGE_KEY
import com.android.tools.idea.logcat.testing.LogcatEditorRule
import com.android.tools.idea.logcat.util.logcatMessage
import com.google.common.truth.Truth.assertThat
import com.intellij.json.JsonLanguage
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail

/**
 * Tests for [CreateScratchFileAction]
 */
@RunsInEdt
class CreateScratchFileActionTest {
  private val projectRule = ProjectRule()
  private val logcatEditorRule = LogcatEditorRule(projectRule)

  @get:Rule
  val rule = RuleChain(projectRule, logcatEditorRule, EdtRule())

  private val project get() = projectRule.project
  private val editor get() = logcatEditorRule.editor

  /**
   * RangeMarker's are kept in the Document as weak reference (see IntervalTreeImpl#createGetter) so we need to keep them alive as long as
   * they are valid.
   */
  private val markers = mutableListOf<RangeMarker>()

  @Test
  fun update_noMetadata_notVisible() {
    val event = testActionEvent(editor)
    editor.putLogcatMessage(logcatMessage(message = "A message with no data"))
    val action = CreateScratchFileAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isFalse()
  }

  @Test
  fun update_withJson() {
    val event = testActionEvent(editor)
    editor.putLogcatMessage(logcatMessage(message = """A message with json { "name": "foo" } and some trailing text"""))
    val action = CreateScratchFileAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo("Create a Scratch File from JSON")
    assertThat(event.presentation.icon).isSameAs(JsonLanguage.INSTANCE.associatedFileType?.icon)
  }

  @Test
  fun update_withXml() {
    val event = testActionEvent(editor)
    editor.putLogcatMessage(logcatMessage(message = """A message with xml <top attr="foo"> <sub/> </top> and some trailing text"""))
    val action = CreateScratchFileAction()

    action.update(event)

    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo("Create a Scratch File from XML")
    assertThat(event.presentation.icon).isSameAs(XMLLanguage.INSTANCE.associatedFileType?.icon)
  }

  @Test
  fun findEmbeddedData_json() {
    val event = testActionEvent(editor)
    editor.putLogcatMessage(logcatMessage(message = """A message with json { "name": "foo" } and some trailing text"""))

    val (text, language) = event.findEmbeddedData() ?: fail("Expect to find embedded data but none found")

    assertThat(text).isEqualTo("""{ "name": "foo" }""")
    assertThat(language).isSameAs(JsonLanguage.INSTANCE)
  }

  @Test
  fun findEmbeddedData_xml() {
    val event = testActionEvent(editor)
    editor.putLogcatMessage(logcatMessage(message = """A message with xml <top attr="foo"> <sub/> </top> and some trailing text"""))

    val (text, language) = event.findEmbeddedData() ?: fail("Expect to find embedded data but none found")

    assertThat(text).isEqualTo("""<top attr="foo"> <sub/> </top>""")
    assertThat(language).isSameAs(XMLLanguage.INSTANCE)
  }

  private fun testActionEvent(editor: EditorEx): TestActionEvent {
    return TestActionEvent(MapDataContext().apply {
      put(PROJECT, project)
      put(EDITOR, editor)
    })
  }

  private fun EditorEx.putLogcatMessage(message: LogcatMessage) {
    document.setText("foo") // it doesn't really matter what the text is
    caretModel.moveToOffset(0)
    document.createRangeMarker(0, 3).apply {
      putUserData(LOGCAT_MESSAGE_KEY, message)
      markers.add(this)
    }
  }
}

/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.sqlite.ui

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.lang.androidSql.AndroidSqlLanguage
import com.android.tools.idea.sqlite.ui.sqliteEvaluator.QueryHistoryView
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.EditorTextField
import com.intellij.ui.EditorTextFieldProvider
import com.intellij.ui.components.JBList
import java.awt.Point
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class QueryHistoryViewTest : LightPlatformTestCase() {
  private lateinit var editorTextField: EditorTextField
  private lateinit var queryHistoryView: QueryHistoryView

  override fun setUp() {
    super.setUp()
    editorTextField =
      EditorTextFieldProvider.getInstance()
        .getEditorField(AndroidSqlLanguage.INSTANCE, project, emptyList())
    queryHistoryView = QueryHistoryView(editorTextField)
  }

  fun testSetQueryHistoryUpdatesList() {
    // Prepare
    val list =
      TreeWalker(queryHistoryView.component).descendants().filterIsInstance<JBList<*>>().first()

    // Act
    queryHistoryView.setQueryHistory(listOf("query1", "query2"))

    // Assert
    assertThat(list.model.size)
    assertThat(list.model.getElementAt(0)).isEqualTo("query1")
    assertThat(list.model.getElementAt(1)).isEqualTo("query2")

    // Act
    queryHistoryView.setQueryHistory(listOf("query3", "query4"))

    // Assert
    assertThat(list.model.size).isEqualTo(2)
    assertThat(list.model.getElementAt(0)).isEqualTo("query3")
    assertThat(list.model.getElementAt(1)).isEqualTo("query4")
  }

  fun testSelectListItemUpdatesEditorText() {
    // Prepare
    val list =
      TreeWalker(queryHistoryView.component).descendants().filterIsInstance<JBList<*>>().first()
    queryHistoryView.setQueryHistory(listOf("query1", "query_2"))
    editorTextField.text = "default text"

    // Act
    list.selectedIndex = 0

    // Assert
    assertThat(editorTextField.text).isEqualTo("query1")

    // Act
    list.selectedIndex = 1

    // Assert
    assertThat(editorTextField.text).isEqualTo("query_2")
  }

  fun testEditorTextRestoredWhenListLosesFocus() {
    // Prepare
    val list =
      TreeWalker(queryHistoryView.component).descendants().filterIsInstance<JBList<*>>().first()
    queryHistoryView.setQueryHistory(listOf("query1", "query_2"))

    list.selectedIndex = 0
    assertThat(editorTextField.text).isEqualTo("query1")

    // Act
    list.focusListeners.forEach { it.focusLost(mock()) }

    // Assert
    assertThat(editorTextField.text).isEqualTo("")
  }

  fun testEnterSetsEditorText() {
    // Prepare
    val list =
      TreeWalker(queryHistoryView.component).descendants().filterIsInstance<JBList<*>>().first()
    val ui = FakeUi(list)
    queryHistoryView.setQueryHistory(listOf("query1", "query_2"))

    list.selectedIndex = 0
    assertThat(editorTextField.text).isEqualTo("query1")

    // Act
    ui.keyboard.setFocus(list)
    ui.keyboard.pressAndRelease(KeyEvent.VK_ENTER)
    // remove focus from list
    list.focusListeners.forEach { it.focusLost(mock()) }

    // Assert
    assertThat(editorTextField.text).isEqualTo("query1")
  }

  fun testListItemSelectedOnMouseHover() {
    // Prepare
    val list =
      TreeWalker(queryHistoryView.component).descendants().filterIsInstance<JBList<*>>().first()
    queryHistoryView.setQueryHistory(listOf("query1", "query_2"))

    assertThat(list.selectedIndex).isEqualTo(-1)

    val mouseEvent = mock<MouseEvent>()
    whenever(mouseEvent.point).thenReturn(Point(0, 0))

    // Act
    list.mouseMotionListeners.forEach { it.mouseMoved(mouseEvent) }

    // Assert
    assertThat(list.selectedIndex).isEqualTo(0)

    // Act
    // remove focus from list
    list.focusListeners.forEach { it.focusLost(mock()) }

    // Assert
    assertThat(list.selectedIndex).isEqualTo(-1)
  }
}

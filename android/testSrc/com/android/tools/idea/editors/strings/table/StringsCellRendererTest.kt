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
package com.android.tools.idea.editors.strings.table

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.editors.strings.StringResourceEditor
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.DEFAULT_VALUE_COLUMN
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.KEY_COLUMN
import com.android.tools.idea.gradle.structure.configurables.ui.properties.renderAnyTo
import com.google.common.truth.Truth.assertThat
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.ERROR_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.STYLE_WAVED
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.awt.Font
import javax.swing.JTable

private const val STRING_VALUE = "Hey, I'm a String value!"
private const val PROBLEM = "Big time problem"
private const val FROZEN_COLUMN_COUNT = 27
private const val SOME_COLUMN = 15
private val CELL_ERROR_ATTRIBUTES = SimpleTextAttributes(STYLE_WAVED, JBColor.red)

@RunWith(JUnit4::class)
class StringsCellRendererTest {
  private val renderer = StringsCellRenderer()
  private val tableFont = Font(Font.MONOSPACED, Font.PLAIN, 36)
  private val frozenColumnTable: FrozenColumnTable<StringResourceTableModel> = mock()
  private val frozenSubTable: SubTable<StringResourceTableModel> = mock()
  private val scrollableSubTable: SubTable<StringResourceTableModel> = mock()
  private val model: StringResourceTableModel = mock()


  @Before
  fun setUp() {
    whenever(frozenColumnTable.frozenTable).thenReturn(frozenSubTable)
    whenever(frozenColumnTable.scrollableTable).thenReturn(scrollableSubTable)
    whenever(frozenSubTable.frozenColumnTable).thenReturn(frozenColumnTable)
    whenever(scrollableSubTable.frozenColumnTable).thenReturn(frozenColumnTable)
    whenever(frozenColumnTable.font).thenReturn(tableFont)
    whenever(scrollableSubTable.font).thenReturn(tableFont)
    whenever(frozenColumnTable.model).thenReturn(model)
    whenever(frozenColumnTable.frozenColumnCount).thenReturn(FROZEN_COLUMN_COUNT)
    doAnswer { it.getArgument<Int>(0) }.whenever(frozenColumnTable).convertColumnIndexToModel(any())
  }

  @Test
  fun doesNothingIfValueIsNotString() {
    val notAStringValue = 3
    renderer.font = tableFont
    assertThat(renderer.font).isEqualTo(tableFont)

    renderer.getTableCellRendererComponent(frozenSubTable, notAStringValue, SOME_COLUMN)

    assertThat(renderer.font).isNull()
    assertThat(renderer.toolTipText).isNull()
    verify(frozenSubTable, never()).convertRowIndexToModel(any())
    verify(frozenSubTable, never()).convertColumnIndexToModel(any())
    verify(frozenSubTable, never()).model

    verifyNoInteractions(frozenColumnTable, scrollableSubTable)
  }

  @Test
  fun updatesFont() {
    renderer.getTableCellRendererComponent(frozenSubTable, STRING_VALUE, SOME_COLUMN)

    assertThat(renderer.font).isEqualTo(StringResourceEditor.getFont(tableFont))
  }

  @Test
  fun updatesToolTipText() {
    whenever(model.getCellProblem(any(), any())).thenReturn(PROBLEM)

    renderer.getTableCellRendererComponent(frozenSubTable, STRING_VALUE, SOME_COLUMN)

    assertThat(renderer.toolTipText).isEqualTo(PROBLEM)
  }

  @Test
  fun nullProblem_frozenTable() {
    renderer.getTableCellRendererComponent(frozenSubTable, STRING_VALUE, DEFAULT_VALUE_COLUMN)

    assertThat(renderer.font).isEqualTo(StringResourceEditor.getFont(tableFont))
    assertThat(renderer.toolTipText).isNull()
    assertThat(renderer.iterator(1).fragment).isEqualTo(STRING_VALUE)
    assertThat(renderer.iterator(1).textAttributes).isEqualTo(REGULAR_ATTRIBUTES)
    verify(model).getCellProblem(0, DEFAULT_VALUE_COLUMN)
  }

  @Test
  fun nullProblem_scrollableTable() {
    renderer.getTableCellRendererComponent(scrollableSubTable, STRING_VALUE, SOME_COLUMN)

    assertThat(renderer.font).isEqualTo(StringResourceEditor.getFont(tableFont))
    assertThat(renderer.toolTipText).isNull()
    assertThat(renderer.iterator(1).fragment).isEqualTo(STRING_VALUE)
    assertThat(renderer.iterator(1).textAttributes).isEqualTo(REGULAR_ATTRIBUTES)
    verify(model).getCellProblem(0, SOME_COLUMN + FROZEN_COLUMN_COUNT)
  }

  @Test
  fun problem_keyColumn_frozenTable() {
    whenever(model.getCellProblem(0, KEY_COLUMN)).thenReturn(PROBLEM)

    renderer.getTableCellRendererComponent(frozenSubTable, STRING_VALUE, KEY_COLUMN)

    assertThat(renderer.font).isEqualTo(StringResourceEditor.getFont(tableFont))
    assertThat(renderer.toolTipText).isEqualTo(PROBLEM)
    assertThat(renderer.iterator(1).fragment).isEqualTo(STRING_VALUE)
    assertThat(renderer.iterator(1).textAttributes).isEqualTo(ERROR_ATTRIBUTES)
  }

  @Test
  fun problem_defaultColumn_frozenTable() {
    whenever(model.getCellProblem(0, DEFAULT_VALUE_COLUMN)).thenReturn(PROBLEM)

    renderer.getTableCellRendererComponent(frozenSubTable, STRING_VALUE, DEFAULT_VALUE_COLUMN)

    assertThat(renderer.font).isEqualTo(StringResourceEditor.getFont(tableFont))
    assertThat(renderer.toolTipText).isEqualTo(PROBLEM)
    assertThat(renderer.iterator(1).fragment).isEqualTo(STRING_VALUE)
    assertThat(renderer.iterator(1).textAttributes).isEqualTo(CELL_ERROR_ATTRIBUTES)
  }

  @Test
  fun problem_scrollableTable() {
    whenever(model.getCellProblem(0, SOME_COLUMN + FROZEN_COLUMN_COUNT)).thenReturn(PROBLEM)

    renderer.getTableCellRendererComponent(scrollableSubTable, STRING_VALUE, SOME_COLUMN)

    assertThat(renderer.font).isEqualTo(StringResourceEditor.getFont(tableFont))
    assertThat(renderer.toolTipText).isEqualTo(PROBLEM)
    assertThat(renderer.iterator(1).fragment).isEqualTo(STRING_VALUE)
    assertThat(renderer.iterator(1).textAttributes).isEqualTo(CELL_ERROR_ATTRIBUTES)
  }

  @Test
  fun clippedText() {
    val multilineString = "Hey, I'm a\nmultiline String value!"
    renderer.getTableCellRendererComponent(frozenSubTable, multilineString, SOME_COLUMN)
    val expectedText = "${multilineString.split('\n').first()}[...]"
    assertThat(renderer.iterator(1).fragment).isEqualTo(expectedText)
  }

  /**
   * Sets some default values we don't want to change in the test. Specifically:
   *
   * - We don't want the cell selected, otherwise the effect of the renderer is altered
   * - We don't care about the row, so it is always set to zero
   * - We don't care about whether it has focus, so just make that true
   */
  private fun StringsCellRenderer.getTableCellRendererComponent(table: JTable, value: Any?, viewColumnIndex: Int) =
    getTableCellRendererComponent(table, value, /* isSelected = */ false, /* hasFocus = */ true, /* row = */ 0, viewColumnIndex)
}

/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.idea.editors.strings.CopyAllSelectedAction
import com.intellij.testFramework.ApplicationRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.event.ActionEvent
import javax.swing.JMenuItem
import javax.swing.table.DefaultTableModel

class CopyAllSelectedActionTest {
  @get:Rule val applicationRule = ApplicationRule()
  private lateinit var frozenColumnTable: FrozenColumnTable<*>

  @Before
  fun setUp() {
    frozenColumnTable = FrozenColumnTable(
      DefaultTableModel(
        arrayOf(
          arrayOf("east", "app/src/main/res", false, "east"),
          arrayOf("west", "app/src/main/res", false, "west"),
          arrayOf("north", "app/src/main/res", false, "north"),
        ),
        arrayOf("Key", "Resource Folder", "Untranslatable", "Default Value"),
      ),
      4,
    ).apply {
      frozenTable.createDefaultColumnsFromModel()
      scrollableTable.createDefaultColumnsFromModel()
    }
  }

  @Test
  fun copyWithoutSelection() {
    val onCopy: (String) -> Unit = {
      fail("Copy not expected. Received $it")
    }
    val copyAction = CopyAllSelectedAction.forTesting(frozenColumnTable, onCopy)
    val copyMenuItem = JMenuItem()
    copyAction.update(copyMenuItem)
    assertFalse(copyMenuItem.isVisible)

    copyAction.actionPerformed(ActionEvent("source", 1, ""))
  }

  @Test
  fun copyContents() {
    var clipboard: String = ""
    val onCopy: (String) -> Unit = {
      clipboard = it
    }
    val copyAction = CopyAllSelectedAction.forTesting(frozenColumnTable, onCopy)
    frozenColumnTable.setRowSelectionInterval(0, 0)

    val copyMenuItem = JMenuItem()
    copyAction.update(copyMenuItem)
    assertTrue(copyMenuItem.isVisible)

    copyAction.actionPerformed(ActionEvent("source", 1, ""))
    assertEquals("east\tapp/src/main/res\tfalse\teast\n", clipboard)

    // Multi selection copy
    frozenColumnTable.setRowSelectionInterval(0, 1)

    copyAction.update(copyMenuItem)
    assertTrue(copyMenuItem.isVisible)

    copyAction.actionPerformed(ActionEvent("source", 1, ""))
    assertEquals("east\tapp/src/main/res\tfalse\teast\nwest\tapp/src/main/res\tfalse\twest\n", clipboard)
  }
}

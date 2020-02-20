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
package com.android.tools.idea.tests.gui.framework.fixture.properties

import com.android.tools.property.ptable2.PTableItem
import com.android.tools.property.ptable2.impl.PTableImpl
import org.fest.swing.core.Robot
import org.fest.swing.edt.GuiQuery
import org.fest.swing.fixture.JTableFixture

/**
 * Fixture for a table inside a section in the properties panel.
 *
 * These fixtures are created within the fixture for the properties panel.
 */
class PTableFixture(robot: Robot, private val table: PTableImpl) : JTableFixture(robot, table) {

  /**
   * Return the item at the specified row.
   */
  fun item(row: Int): PTableItem =
    GuiQuery.getNonNull { table.item(row) }

  /**
   * Find a row with an item of the specified name..
   */
  fun findRowOf(name: String): Int =
    GuiQuery.getNonNull { findRowInTable(name) }

  /**
   * Supply focus to the table.
   */
  fun focusAndWaitForFocusGain(): PTableFixture =
    apply { driver().focusAndWaitForFocusGain(target()) }

  private fun findRowInTable(name: String): Int =
    (0 until table.rowCount).find { table.item(it).name == name } ?: -1
}

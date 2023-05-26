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


import com.android.ide.common.resources.Locale
import com.android.tools.idea.editors.strings.table.filter.StringResourceTableColumnFilter
import com.android.tools.idea.editors.strings.table.filter.StringResourceTableRowFilter
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.fail

/** Test [StringResourceTable]. */
@RunWith(JUnit4::class)
class StringResourceTableTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  // This requires ActionManager to be set up before it is constructed.
  private lateinit var stringResourceTable: StringResourceTable

  @Before
  fun setUp() {
    stringResourceTable = StringResourceTable()
    stringResourceTable.createDefaultColumnsFromModel()
  }

  @Test
  fun constructor() {
    assertThat(stringResourceTable.frozenColumnCount).isEqualTo(4)
    assertThat(stringResourceTable.listeners).isEmpty()
    assertThat(stringResourceTable.rowSorter).isNotNull()
    assertThat(stringResourceTable.rowSorter!!.rowFilter).isNull()
    assertThat(stringResourceTable.columnFilter).isNull()
  }

  @Test
  fun setAndGetRowFilter() {
    val rowFilter = object : StringResourceTableRowFilter() {
      override fun getDescription() = fail("Not called")
      override fun include(entry: Entry<out StringResourceTableModel, out Int>?) = fail("Not called")
    }
    stringResourceTable.rowFilter = rowFilter

    assertThat(stringResourceTable.rowFilter).isEqualTo(rowFilter)

    stringResourceTable.rowFilter = null

    assertThat(stringResourceTable.rowFilter).isNull()
  }

  @Test
  fun setAndGetColumnFilter() {
    val columnFilter = object : StringResourceTableColumnFilter {
      override fun getDescription() = fail("Not called")
      override fun include(locale: Locale) = fail("Not called")
    }
    stringResourceTable.columnFilter = columnFilter

    assertThat(stringResourceTable.columnFilter).isEqualTo(columnFilter)

    stringResourceTable.columnFilter = null

    assertThat(stringResourceTable.columnFilter).isNull()
  }

  @Test
  fun includeColumn_noFilter() {
    // With no column filter, all columns should be included. Multiply by 2 to get past FIXED_COLUMN_COUNT
    // below which include _always_ returns true.
    (0 until StringResourceTableModel.FIXED_COLUMN_COUNT * 2).forEach {
      assertThat(stringResourceTable.includeColumn(it)).isTrue()
    }
  }
}
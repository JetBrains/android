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
package com.android.tools.idea.editors.strings.table.filter

import com.android.tools.idea.editors.strings.table.StringResourceTableModel
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.DEFAULT_VALUE_COLUMN
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.UNTRANSLATABLE_COLUMN
import com.google.common.truth.Truth.assertThat
import javax.swing.RowFilter.Entry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock

/** Tests the [TextRowFilter] class. */
@RunWith(JUnit4::class)
class TextRowFilterTest {
  private val model: StringResourceTableModel = mock()
  private val items = Array<Any>(DEFAULT_VALUE_COLUMN + 5) { "" }
  private val entry =
    object : Entry<StringResourceTableModel, Int>() {
      override fun getModel(): StringResourceTableModel = this@TextRowFilterTest.model

      override fun getValueCount(): Int = items.size

      override fun getValue(index: Int): Any = items[index]

      override fun getIdentifier(): Int = 42
    }
  private val textRowFilter = TextRowFilter(TEXT)

  @Test
  fun getDescription() {
    assertThat(textRowFilter.getDescription())
      .isEqualTo("""Show Keys with Values Containing "$TEXT"""")
  }

  @Test
  fun include() {
    assertThat(textRowFilter.include(entry)).isFalse()
    items[DEFAULT_VALUE_COLUMN] = "A string containing $TEXT is here!"
    assertThat(textRowFilter.include(entry)).isTrue()
    items[DEFAULT_VALUE_COLUMN] = ""
    items[DEFAULT_VALUE_COLUMN + 1] = "A wild $TEXT appears!"
    assertThat(textRowFilter.include(entry)).isTrue()
  }

  @Test
  fun include_ignoresUntranslatableColumn() {
    val trueRowFilter = TextRowFilter("true")
    val falseRowFilter = TextRowFilter("false")
    items[UNTRANSLATABLE_COLUMN] = true
    assertThat(trueRowFilter.include(entry)).isFalse()
    items[UNTRANSLATABLE_COLUMN] = false
    assertThat(falseRowFilter.include(entry)).isFalse()
  }

  companion object {
    private const val TEXT = "my great text"
  }
}

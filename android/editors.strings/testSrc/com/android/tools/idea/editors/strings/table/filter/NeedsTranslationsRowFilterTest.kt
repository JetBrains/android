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

/** Tests the [NeedsTranslationsRowFilter] class. */
@RunWith(JUnit4::class)
class NeedsTranslationsRowFilterTest {
  private val model: StringResourceTableModel = mock()
  private val needsTranslationsRowFilter = NeedsTranslationsRowFilter()
  private val items = Array<Any>(DEFAULT_VALUE_COLUMN + 5) { "Not an empty string!" }
  private val entry =
    object : Entry<StringResourceTableModel, Int>() {
      override fun getModel(): StringResourceTableModel = this@NeedsTranslationsRowFilterTest.model

      override fun getValueCount(): Int = items.size

      override fun getValue(index: Int): Any = items[index]

      override fun getIdentifier(): Int = 42
    }

  @Test
  fun getDescription() {
    assertThat(needsTranslationsRowFilter.getDescription())
      .isEqualTo("Show Keys Needing Translations")
  }

  @Test
  fun include_untranslatable() {
    items[UNTRANSLATABLE_COLUMN] = true

    assertThat(needsTranslationsRowFilter.include(entry)).isFalse()
  }

  @Test
  fun include_noEmptyStrings() {
    items[UNTRANSLATABLE_COLUMN] = false

    assertThat(needsTranslationsRowFilter.include(entry)).isFalse()
  }

  @Test
  fun include_emptyStrings() {
    items[UNTRANSLATABLE_COLUMN] = false
    items[items.size - 1] = ""

    assertThat(needsTranslationsRowFilter.include(entry)).isTrue()
  }
}

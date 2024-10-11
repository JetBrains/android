/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.UNTRANSLATABLE_COLUMN
import com.google.common.truth.Truth.assertThat
import javax.swing.RowFilter.Entry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Tests the [TranslatableRowFilter] class. */
@RunWith(JUnit4::class)
class TranslatableRowFilterTest {
  private val entry: Entry<out StringResourceTableModel, out Int> = mock()
  private val translatableRowFilter = TranslatableRowFilter()

  @Test
  fun getDescription() {
    assertThat(translatableRowFilter.getDescription()).isEqualTo("Show Translatable Keys")
  }

  @Test
  fun getIcon() {
    assertThat(translatableRowFilter.getIcon()).isNull()
  }

  @Test
  fun include_untranslatable() {
    whenever(entry.getValue(UNTRANSLATABLE_COLUMN)).thenReturn(false)
    assertThat(translatableRowFilter.include(entry)).isTrue()
  }

  @Test
  fun include_translatable() {
    whenever(entry.getValue(UNTRANSLATABLE_COLUMN)).thenReturn(true)
    assertThat(translatableRowFilter.include(entry)).isFalse()
  }
}

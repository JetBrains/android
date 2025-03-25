/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.ide.common.resources.Locale
import com.android.tools.idea.editors.strings.StringResource
import com.android.tools.idea.editors.strings.table.StringResourceTableModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import javax.swing.RowFilter.Entry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Tests the [NeedsTranslationForLocaleRowFilter] class. */
@RunWith(JUnit4::class)
class NeedsTranslationForLocaleRowFilterTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val needsTranslationForLocaleRowFilterAr =
    NeedsTranslationForLocaleRowFilter(ARABIC_LOCALE)
  private val needsTranslationForLocaleRowFilterEs =
    NeedsTranslationForLocaleRowFilter(US_SPANISH_LOCALE)

  private val model: StringResourceTableModel = mock()
  private val resource: StringResource = mock()
  private val entry: Entry<StringResourceTableModel, Int> = mock()

  @Before
  fun setUp() {
    whenever(entry.model).thenReturn(model)
    whenever(entry.identifier).thenReturn(ROW_INDEX)
    whenever(model.getStringResourceAt(ROW_INDEX)).thenReturn(resource)
  }

  @Test
  fun getDescription() {
    assertThat(needsTranslationForLocaleRowFilterAr.getDescription())
      .isEqualTo("Show Keys Needing a Translation for Arabic (ar)")
    assertThat(needsTranslationForLocaleRowFilterEs.getDescription())
      .isEqualTo("Show Keys Needing a Translation for Spanish (es) in United States (US)")
  }

  @Test
  fun getIcon() {
    assertThat(needsTranslationForLocaleRowFilterAr.getIcon()).isNull()
    assertThat(needsTranslationForLocaleRowFilterEs.getIcon()).isNull()
  }

  @Test
  fun include_notTranslatable() {
    whenever(resource.isTranslatable).thenReturn(false)

    assertThat(needsTranslationForLocaleRowFilterEs.include(entry)).isFalse()
    assertThat(needsTranslationForLocaleRowFilterAr.include(entry)).isFalse()
  }

  @Test
  fun include() {
    whenever(resource.isTranslatable).thenReturn(true)
    whenever(resource.getTranslationAsString(ARABIC_LOCALE)).thenReturn("")
    whenever(resource.getTranslationAsString(US_SPANISH_LOCALE)).thenReturn("Not an empty string")

    assertThat(needsTranslationForLocaleRowFilterAr.include(entry)).isTrue()
    assertThat(needsTranslationForLocaleRowFilterEs.include(entry)).isFalse()
  }

  companion object {
    private const val ROW_INDEX = 42
    private val ARABIC_LOCALE = Locale.create("ar")
    private val US_SPANISH_LOCALE = Locale.create("es-rUS")
  }
}

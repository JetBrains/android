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
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.editors.strings.StringResource
import com.android.tools.idea.editors.strings.StringResourceData
import com.android.tools.idea.editors.strings.model.StringResourceKey
import com.android.tools.idea.editors.strings.model.StringResourceRepository
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.DEFAULT_VALUE_COLUMN
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.FIXED_COLUMN_COUNT
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.KEY_COLUMN
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.RESOURCE_FOLDER_COLUMN
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.UNTRANSLATABLE_COLUMN
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for [StringResourceTableModel] class. */
@RunWith(JUnit4::class)
class StringResourceTableModelTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  private val stringResourceData: StringResourceData = mock()
  private val stringResourceRepository: StringResourceRepository = mock()
  private val keys: MutableList<StringResourceKey> = mutableListOf()
  private val locales: MutableList<Locale> = mutableListOf()
  private lateinit var model: StringResourceTableModel

  @Before
  fun setUp() {
    whenever(stringResourceData.keys).thenReturn(keys)
    whenever(stringResourceData.localeList).thenReturn(locales)
    model = StringResourceTableModel(stringResourceRepository, projectRule.project, stringResourceData)
  }

  @Test
  fun nullConstructorValues() {
    val emptyModel = StringResourceTableModel()

    assertThat(emptyModel.repository.getKeys()).isEmpty()
    assertThat(emptyModel.data).isNull()
    assertThat(emptyModel.columnCount).isEqualTo(FIXED_COLUMN_COUNT)
  }

  @Test
  fun isStringValueColumn() {
    assertThat(StringResourceTableModel.isStringValueColumn(KEY_COLUMN)).isFalse()
    assertThat(StringResourceTableModel.isStringValueColumn(RESOURCE_FOLDER_COLUMN)).isFalse()
    assertThat(StringResourceTableModel.isStringValueColumn(UNTRANSLATABLE_COLUMN)).isFalse()
    assertThat(StringResourceTableModel.isStringValueColumn(DEFAULT_VALUE_COLUMN)).isTrue()
    repeat(10) {
      assertThat(StringResourceTableModel.isStringValueColumn(FIXED_COLUMN_COUNT + it)).isTrue()
    }
  }

  @Test
  fun getCellProblem_null() {
    val rows = listOf(0, 1, 2)
    for (row in rows) {
      assertThat(model.getCellProblem(row, RESOURCE_FOLDER_COLUMN)).isNull()
      assertThat(model.getCellProblem(row, UNTRANSLATABLE_COLUMN)).isNull()
    }
  }

  @Test
  fun getCellProblem_keyColumn() {
    val numRows = 3
    val failureMessages = listOf(null, "Failure1", "Failure2", "Failure3")
    repeat(numRows) { row ->
      keys.add(StringResourceKey("key$row"))
      for (failureMessage in failureMessages) {
        whenever(stringResourceData.validateKey(keys.last())).thenReturn(failureMessage)
        assertThat(model.getCellProblem(row, KEY_COLUMN)).isEqualTo(failureMessage)
      }
    }
  }

  @Test
  fun getCellProblem_defaultValueColumn() {
    val numRows = 3
    val failureMessages = listOf(null, "Failure1", "Failure2", "Failure3")
    repeat(numRows) { row ->
      keys.add(StringResourceKey("key$row"))
      val resource: StringResource = mock()
      whenever(stringResourceData.getStringResource(keys.last())).thenReturn(resource)
      for (failureMessage in failureMessages) {
        whenever(resource.validateDefaultValue()).thenReturn(failureMessage)
        assertThat(model.getCellProblem(row, DEFAULT_VALUE_COLUMN)).isEqualTo(failureMessage)
      }
    }
  }

  @Test
  fun getCellProblem_translationColumn() {
    val numCases = 3
    val failureMessages = listOf(null, "Failure1", "Failure2", "Failure3")
    repeat(numCases) { rowAndColumn ->
      keys.add(StringResourceKey("key$rowAndColumn"))
      locales.add(mock())
      val resource: StringResource = mock()
      whenever(stringResourceData.getStringResource(keys.last())).thenReturn(resource)
      for (failureMessage in failureMessages) {
        whenever(resource.validateTranslation(locales.last())).thenReturn(failureMessage)
        assertThat(model.getCellProblem(rowAndColumn, FIXED_COLUMN_COUNT + rowAndColumn)).isEqualTo(failureMessage)
      }
    }
  }

  @Test
  fun columnName() {
    locales.add(Locale.create(LocaleQualifier("en")))

    assertThat(model.getColumnName(4)).isEqualTo("English (en)")
  }

  @Test
  fun columnName_unknownRegion() {
    // The region "a00" is invalid, and so Locale.getLocaleLabel() will throw an AssertionError for this instance.
    // This test case ensures we still show a reasonable value for the column name, even if the user has created a malformed locale.
    locales.add(Locale.create(LocaleQualifier("b+es-a00", "es", "a00", null)))

    assertThat(model.getColumnName(4)).isEqualTo("es-a00")
  }

}

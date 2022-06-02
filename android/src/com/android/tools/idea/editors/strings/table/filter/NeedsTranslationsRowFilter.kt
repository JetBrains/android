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

import com.android.tools.idea.editors.strings.table.StringResourceTableModel
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.DEFAULT_VALUE_COLUMN
import com.android.tools.idea.editors.strings.table.StringResourceTableModel.UNTRANSLATABLE_COLUMN

/** Filter that shows rows missing any translations for Locales in use by the project. */
class NeedsTranslationsRowFilter : StringResourceTableRowFilter() {
  override fun include(entry: Entry<out StringResourceTableModel, out Int>): Boolean {
    val untranslatable = entry.getValue(UNTRANSLATABLE_COLUMN) as Boolean
    return !untranslatable &&
        entry.stringValues(startIndex = DEFAULT_VALUE_COLUMN).any(String::isEmpty)
  }

  override fun getDescription(): String = "Show Keys Needing Translations"
}

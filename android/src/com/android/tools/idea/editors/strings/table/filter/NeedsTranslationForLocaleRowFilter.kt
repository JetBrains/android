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
import com.android.tools.idea.editors.strings.table.StringResourceTableModel
import com.android.tools.idea.rendering.FlagManager
import javax.swing.Icon

/** Filter that shows only rows that are missing a translation for the given [locale]. */
class NeedsTranslationForLocaleRowFilter(private val locale: Locale) :
    StringResourceTableRowFilter() {
  override fun include(entry: Entry<out StringResourceTableModel, out Int>): Boolean {
    val resource = entry.model.getStringResourceAt(entry.identifier)
    return resource.isTranslatable && resource.getTranslationAsString(locale).isEmpty()
  }

  override fun getDescription(): String =
      "Show Keys Needing a Translation for ${Locale.getLocaleLabel(locale, /* brief= */false)}"
}

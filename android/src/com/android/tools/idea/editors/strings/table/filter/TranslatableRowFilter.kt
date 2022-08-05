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

/** A filter that shows only rows that contain translatable resources. */
class TranslatableRowFilter : StringResourceTableRowFilter() {
  override fun include(entry: Entry<out StringResourceTableModel, out Int>): Boolean =
      !(entry.getValue(UNTRANSLATABLE_COLUMN) as Boolean)

  override fun getDescription(): String = "Show Translatable Keys"
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.property2.api

import com.android.tools.adtui.ptable2.PTableItem
import com.android.tools.adtui.ptable2.PTableModel

/**
 * The model of a table in an inspector.
 */
interface TableLineModel: InspectorLineModel {

  /**
   * The table model for the table embedded in this inspector line.
   */
  val tableModel: PTableModel

  /**
   * The item currently selected in the table.
   */
  val selectedItem: PTableItem?

  /**
   * Request focus in a specified item.
   */
  fun requestFocus(item: PTableItem)

  /**
   * Stop editing the any table item.
   */
  fun stopEditing()
}

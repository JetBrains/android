/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.property.ptable2

/**
 * Identifies a variable height Cell Renderer & Editor.
 *
 * If renderer component returned from [PTableCellRenderer.getEditorComponent]
 * implements this interface the row height will be set to the preferred height
 * of the component if [isCustomHeight] is true.
 *
 * Otherwise the default height of the table rows is used.
 */
interface PTableVariableHeightCellEditor {

  /**
   * true, if this editor requires a custom row height in the table.
   */
  val isCustomHeight: Boolean

  /**
   * Callback a cell editor can use to update the row height in the table.
   */
  var updateRowHeight: () -> Unit
}

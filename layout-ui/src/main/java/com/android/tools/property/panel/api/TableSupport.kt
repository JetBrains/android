/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.property.panel.api

/**
 * Support for table navigation.
 *
 * See [PropertyEditorModel.tableSupport]
 */
interface TableSupport {

  /**
   * Toggle the group item in a table.
   */
  fun toggleGroup() {}

  /**
   * Update the row height for this editor.
   *
   * This should only be called if the editor requires a non
   * standard height see [PropertyEditorModel.isCustomHeight].
   */
  fun updateRowHeight(scrollIntoView: Boolean) {}
}

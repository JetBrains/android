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
package com.android.tools.idea.devicemanager.displayList.columns

import com.android.tools.idea.devicemanager.displayList.PreconfiguredDeviceDefinition
import com.intellij.openapi.util.Comparing
import com.intellij.util.ui.ColumnInfo
import javax.swing.JTable

abstract class DeviceColumnInfo(name: String, private val width: Int = -1): ColumnInfo<PreconfiguredDeviceDefinition, String>(name) {
  override fun getComparator(): Comparator<PreconfiguredDeviceDefinition> = Comparator { o1, o2 -> Comparing.compare(valueOf(o1), valueOf(o2)) }

  override fun getWidth(table: JTable): Int = width
}

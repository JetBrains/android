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
package com.android.tools.idea.deviceManager.avdmanager

import com.android.sdklib.internal.avd.AvdInfo
import com.intellij.openapi.util.Comparing
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.ColumnInfo
import javax.swing.JTable

/**
 * This class extends [ColumnInfo] in order to pull a string value from a given [AvdInfo].
 * This is the column info used for most of our table, including the Name, Resolution, and API level columns.
 * It uses the text field renderer and allows for sorting by the lexicographical value
 * of the string displayed by the [JBLabel] rendered as the cell component. An explicit width may be used
 * by calling the overloaded constructor, otherwise the column will auto-scale to fill available space.
 */
abstract class AvdColumnInfo(name: String, private val width: Int = -1): ColumnInfo<AvdInfo, String>(name) {
  override fun getComparator(): Comparator<AvdInfo> = Comparator { o1, o2 ->
    val s1 = valueOf(o1)
    val s2 = valueOf(o2)
    Comparing.compare(s1, s2)
  }

  override fun getWidth(table: JTable): Int = width
}

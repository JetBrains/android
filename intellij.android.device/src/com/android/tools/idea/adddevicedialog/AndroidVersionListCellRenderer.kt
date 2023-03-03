/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.adddevicedialog

import com.android.sdklib.AndroidVersion
import com.android.sdklib.getApiNameAndDetails
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JList

internal class AndroidVersionListCellRenderer : ColoredListCellRenderer<AndroidVersion>() {
  override fun customizeCellRenderer(list: JList<out AndroidVersion>,
                                     version: AndroidVersion,
                                     index: Int,
                                     selected: Boolean,
                                     focused: Boolean) {
    val nameAndDetails = version.getApiNameAndDetails(includeReleaseName = true, includeCodeName = true)
    append(nameAndDetails.name)

    nameAndDetails.details?.let {
      append(" $it", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
  }
}

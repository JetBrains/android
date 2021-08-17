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
package com.android.tools.idea.naveditor.property.ui

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.model.deepLinkAction
import com.android.tools.idea.naveditor.model.deepLinkMimeType
import com.android.tools.idea.naveditor.model.uri
import com.intellij.ui.SimpleTextAttributes
import icons.StudioIcons.NavEditor.Properties.DEEPLINK
import javax.swing.JList

class DeepLinkCellRenderer : NavListCellRenderer(DEEPLINK) {
  override fun customizeCellRenderer(list: JList<out NlComponent>, value: NlComponent?, index: Int, selected: Boolean, hasFocus: Boolean) {
    super.customizeCellRenderer(list, value, index, selected, hasFocus)

    val deepLink = value ?: return
    append(listOfNotNull(deepLink.uri, deepLink.deepLinkMimeType, deepLink.deepLinkAction).joinToString())

    val id = deepLink.id?.let { " (${it})" } ?: ""
    append(id, SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }
}
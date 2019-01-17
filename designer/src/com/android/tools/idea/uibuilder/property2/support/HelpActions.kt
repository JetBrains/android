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
package com.android.tools.idea.uibuilder.property2.support

import com.android.SdkConstants.ANDROIDX_PKG_PREFIX
import com.android.SdkConstants.ANDROID_PKG_PREFIX
import com.android.SdkConstants.ANDROID_VIEW_PKG
import com.android.SdkConstants.ANDROID_WIDGET_PREFIX
import com.android.SdkConstants.ATTR_LAYOUT_MARGIN
import com.android.SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX
import com.android.SdkConstants.CLASS_VIEWGROUP
import com.android.SdkConstants.DOT_LAYOUT_PARAMS
import com.android.annotations.VisibleForTesting
import com.android.tools.idea.common.property2.api.HelpSupport
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

const val DEFAULT_ANDROID_REFERENCE_PREFIX = "https://developer.android.com/reference/"

object HelpActions {

  val secondaryHelp = object : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
      val property = event.dataContext.getData(HelpSupport.PROPERTY_ITEM) as NelePropertyItem?
      val componentName = property?.componentName ?: return
      val url = toHelpUrl(componentName, property) ?: return
      BrowserUtil.browse(url)
    }
  }

  @VisibleForTesting
  fun toHelpUrl(componentName: String, property: NelePropertyItem): String? {
    val anchor = getAttributeAnchor(componentName, property) ?: return null
    return getHelpUrl(componentName, property) + anchor
  }

  private fun getHelpUrl(componentName: String, property: NelePropertyItem): String? {
    val dotLayoutParams = when {
      componentName.equals(CLASS_VIEWGROUP) && property.name.startsWith(ATTR_LAYOUT_MARGIN) -> ".MarginLayoutParams"
      property.name.startsWith(ATTR_LAYOUT_RESOURCE_PREFIX) -> DOT_LAYOUT_PARAMS
      else -> ""
    }
    return "$DEFAULT_ANDROID_REFERENCE_PREFIX${componentName.replace('.', '/')}$dotLayoutParams.html"
  }

  private fun getAttributeAnchor(componentName: String, property: NelePropertyItem): String? =
    when {
      componentName.startsWith(ANDROID_VIEW_PKG) ||
      componentName.startsWith(ANDROID_WIDGET_PREFIX) -> "#attr_android:${property.name}"

      // Do not try to specify the attribute anchor since many doc pages is missing the anchor or do not
      // document the XML attributes.
      componentName.startsWith(ANDROID_PKG_PREFIX) ||
      componentName.startsWith(ANDROIDX_PKG_PREFIX) -> ""

      // Do not try to map a class that we know will not be documented on developer.android.com.
      else -> null
    }
}

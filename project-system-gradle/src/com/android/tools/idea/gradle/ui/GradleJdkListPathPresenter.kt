/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.ui

import com.intellij.openapi.roots.ui.configuration.SdkListItem
import com.intellij.openapi.roots.ui.configuration.SdkListModel
import com.intellij.openapi.roots.ui.configuration.SdkListPresenter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.Producer
import java.awt.Component
import javax.swing.JList

/**
 * Custom presenter used as a render on [GradleJdkComboBox] allowing to be able to display the homePath given a [SdkListItem]
 * a part of their name and version i.e: jbr-17 Jetbrains Runtime version 17.0.4 /user/jdks/jdk-17
 */
class GradleJdkListPathPresenter(
  private val sdkReferenceItemsHomePathMap: Map<String, String?> = mapOf(),
  producerSdkList: Producer<SdkListModel>
) : SdkListPresenter(producerSdkList) {

  override fun customizeCellRenderer(
    list: JList<out SdkListItem>,
    value: SdkListItem?,
    index: Int,
    selected: Boolean,
    hasFocus: Boolean
  ) {
    super.customizeCellRenderer(list, value, index, selected, hasFocus)
    when (value) {
      is SdkListItem.SdkItem -> value.sdk.homePath?.let { appendHomePath(it) }
      is SdkListItem.SdkReferenceItem -> sdkReferenceItemsHomePathMap[value.name]?.let { appendHomePath(it) }
    }
  }

  override fun getListCellRendererComponent(
    list: JList<out SdkListItem>,
    value: SdkListItem?,
    index: Int,
    selected: Boolean,
    hasFocus: Boolean
  ): Component {
    val component = super.getListCellRendererComponent(list, value, index, selected, hasFocus)
    list.toolTipText = when {
      value is SdkListItem.SdkItem && value.sdk.homePath != null -> value.sdk.homePath
      value is SdkListItem.SdkReferenceItem && sdkReferenceItemsHomePathMap.containsKey(value.name) -> sdkReferenceItemsHomePathMap[value.name]
      else -> ""
    }
    return component
  }

  private fun appendHomePath(homePath: String) {
    append(" ")
    append(homePath, SimpleTextAttributes.GRAYED_ATTRIBUTES)
  }
}
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
package com.android.tools.idea.appinspection.inspectors.network.view

import com.android.tools.adtui.table.ConfigColumnTableAspect.ColumnInfo
import com.android.tools.idea.appinspection.inspectors.network.view.connectionsview.ConnectionColumn
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XCollection.Style.v2

/** Persistence of the [NetworkInspectorView] state */
@State(name = "NetworkInspectorViewState", storages = [Storage("networkInspectorViewState.xml")])
internal class NetworkInspectorViewState : PersistentStateComponent<NetworkInspectorViewState> {

  @XCollection(style = v2)
  var columns: MutableList<ColumnInfo> =
    ConnectionColumn.values().map { it.toColumnInfo() }.toMutableList()

  companion object {
    fun getInstance(): NetworkInspectorViewState =
      ApplicationManager.getApplication().getService(NetworkInspectorViewState::class.java)
  }

  override fun getState(): NetworkInspectorViewState {
    // Early version had the name as the `Column` enum name. New versions uses a common API that
    // needs the display string.
    if (columns.find { it.name == "NAME" } != null) {
      // One time conversion from enum `name` to `displayString`. This is possible because at the
      // time this code is written, the enum values all have `displayString == capitalized name`
      columns.forEach { it.name = StringUtil.capitalize(it.name.lowercase()) }
    }
    val configNames = columns.mapTo(HashSet()) { it.name }
    val enumNames = ConnectionColumn.values().mapTo(HashSet()) { it.displayString }

    if (configNames != enumNames) {
      ConnectionColumn.values().forEachIndexed { i, value ->
        if (!configNames.contains(value.displayString)) {
          if (i < configNames.size) {
            columns.add(i, value.toColumnInfo())
          } else {
            columns.add(value.toColumnInfo())
          }
        }
      }
      columns.removeAll { it.name !in enumNames }
    }
    return this
  }

  override fun loadState(state: NetworkInspectorViewState) {
    XmlSerializerUtil.copyBean(state, this)
  }
}

private fun ConnectionColumn.toColumnInfo() = ColumnInfo(displayString, widthRatio, visible)

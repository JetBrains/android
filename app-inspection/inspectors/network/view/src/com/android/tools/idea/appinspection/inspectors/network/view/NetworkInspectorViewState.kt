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

import com.android.tools.idea.appinspection.inspectors.network.view.ConnectionsView.Column
import com.android.tools.idea.appinspection.inspectors.network.view.NetworkInspectorViewState.ColumnInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.annotations.XCollection.Style.v2

/** Persistence of the [NetworkInspectorView] state */
@State(name = "NetworkInspectorViewState", storages = [Storage("networkInspectorViewState.xml")])
internal class NetworkInspectorViewState : PersistentStateComponent<NetworkInspectorViewState> {

  @XCollection(style = v2)
  var columns: List<ColumnInfo> =
    mutableListOf(
      Column.NAME.toColumnInfo(),
      Column.SIZE.toColumnInfo(),
      Column.TYPE.toColumnInfo(),
      Column.STATUS.toColumnInfo(),
      Column.TIME.toColumnInfo(),
      Column.TIMELINE.toColumnInfo(),
    )

  @Tag("column-info")
  data class ColumnInfo(
    @Attribute("name") var name: String = "",
    @Attribute("width-ratio") var widthRatio: Double = 0.0,
  )

  companion object {
    fun getInstance(): NetworkInspectorViewState =
      ApplicationManager.getApplication().getService(NetworkInspectorViewState::class.java)
  }

  override fun getState(): NetworkInspectorViewState = this

  override fun loadState(state: NetworkInspectorViewState) {
    XmlSerializerUtil.copyBean(state, this)
  }
}

private fun Column.toColumnInfo() = ColumnInfo(name, widthRatio)

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
package com.android.tools.idea.devicemanager.virtualtab

import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceTableModel.DEVICE_MODEL_COLUMN_INDEX
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import javax.swing.SortOrder

@State(name = "deviceManagerVirtualTab", storages = [Storage("deviceManagerVirtualTab.xml")])
@Service
internal class VirtualTabPersistentStateComponent : PersistentStateComponent<VirtualTabState> {
  private var myState: VirtualTabState

  init {
    myState = VirtualTabState()
  }

  override fun getState(): VirtualTabState {
    return myState
  }

  override fun loadState(state: VirtualTabState) {
    myState = state
  }

  companion object {
    @JvmStatic
    val instance: VirtualTabPersistentStateComponent
      get() = ApplicationManager.getApplication().getService(VirtualTabPersistentStateComponent::class.java)
  }
}

internal class VirtualTabState(var sortColumn: Int = DEVICE_MODEL_COLUMN_INDEX, var sortOrder: SortOrder = SortOrder.ASCENDING)

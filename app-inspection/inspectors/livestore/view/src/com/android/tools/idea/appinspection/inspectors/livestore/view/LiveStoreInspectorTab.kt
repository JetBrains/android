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
package com.android.tools.idea.appinspection.inspectors.livestore.view

import com.android.tools.idea.appinspection.inspectors.livestore.model.LiveStoreInspectorClient
import com.intellij.ui.layout.panel
import javax.swing.JComponent
import javax.swing.JPanel

class LiveStoreInspectorTab(client: LiveStoreInspectorClient) {
  private val root: JPanel
  init {
    val liveStoresList = LiveStoresList(client)
    val currentLiveStoreTable = LiveStoreTable(client)

    liveStoresList.component.addListSelectionListener {
      val currStore = liveStoresList.component.selectedValue
      currentLiveStoreTable.setContentsTo(currStore)
    }

    root = panel {
      row {
        liveStoresList.component(grow)
        currentLiveStoreTable.component(grow)
      }
    }
  }

  val component: JComponent = root
}
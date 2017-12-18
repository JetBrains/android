/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.view

import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.viewmodel.InternalDesignAssetExplorer
import java.awt.BorderLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JPanel

/**
 * View meant to display [com.android.tools.idea.resourceExplorer.model.DesignAsset] located
 * in the project.
 * It uses an [InternalDesignAssetExplorer] to populates the views
 */
class InternalResourceBrowser(
    resourceBrowserViewModel: InternalDesignAssetExplorer
) : JPanel(BorderLayout()) {

  private val listeners = mutableListOf<SelectionListener>()
  private val designAssetsList: DesignAssetsList = DesignAssetsList(resourceBrowserViewModel)

  init {
    designAssetsList.fixedCellWidth = 200
    designAssetsList.fixedCellHeight = 200
    designAssetsList.itemMargin = 50
    add(designAssetsList)
    designAssetsList.addListSelectionListener {
      listeners.forEach { it.onDesignAssetSetSelected(designAssetsList.selectedValue) }
    }
  }

  fun addSelectionListener(listener: SelectionListener) {
    listeners += listener
  }

  fun removeSelectionListener(listener: SelectionListener) {
    listeners -= listener
  }

  interface SelectionListener {
    fun onDesignAssetSetSelected(designAssetSet: DesignAssetSet?)
  }
}
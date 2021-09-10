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
package com.android.tools.idea.uibuilder.structure

import com.android.tools.adtui.common.AdtUiUtils
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.res.RESOURCE_ICON_SIZE
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import java.awt.Component
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener

/**
 * Panel that shows the view's visibility in the gutter next to the component tree.
 * Clicking each icon would show popup menu that allows users to choose visibility.
 */
open class NlVisibilityGutterPanel: JPanel(), TreeExpansionListener, Disposable {

  companion object {
    private const val PADDING_X = 10
    const val WIDTH = RESOURCE_ICON_SIZE + PADDING_X
  }

  @VisibleForTesting
  val list = NlVisibilityJBList()

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    alignmentX = Component.CENTER_ALIGNMENT

    background = secondaryPanelBackground
    preferredSize = Dimension(WIDTH, 0)

    list.model = CollectionListModel()
    list.cellRenderer = NlVisibilityButtonCellRenderer()

    // These are required to remove blue bazel when focused.
    list.border = null
    list.isFocusable = false
    isFocusable = false

    add(list)

    Disposer.register(this, list)
  }

  override fun updateUI() {
    super.updateUI()
    border = BorderFactory.createMatteBorder(
      0, 1, 0, 0, AdtUiUtils.DEFAULT_BORDER_COLOR)
  }

  /**
   * Update the gutter icons according to the tree paths.
   */
  fun update(tree: JTree) {
    val application = ApplicationManager.getApplication()
    if (!application.isReadAccessAllowed) {
      return runReadAction { update(tree) }
    }

    val toReturn = ArrayList<ButtonPresentation>()
    for (i in 0 until tree.rowCount) {
      val path = tree.getPathForRow(i)
      val last = path.lastPathComponent

      if (last is NlComponent) {
        toReturn.add(createItem(last))
      }
      else {
        // Anything else (e.g. Referent id) we don't support visibility change.
        toReturn.add(createItem())
      }
    }

    list.model = CollectionListModel(toReturn)
    revalidate()
  }

  private fun createItem(component: NlComponent? = null): ButtonPresentation {
    if (component == null) {
      return ButtonPresentation()
    }

    val model = NlVisibilityModel(component)
    return ButtonPresentation(model)
  }

  override fun treeExpanded(event: TreeExpansionEvent?) {
    update(event?.source as JTree)
  }

  override fun treeCollapsed(event: TreeExpansionEvent?) {
    update(event?.source as JTree)
  }

  override fun dispose() {
    removeAll()
  }
}

// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.naveditor.editor

import com.android.annotations.VisibleForTesting
import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.idea.naveditor.scene.NavColorSet
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.collect.ImmutableList
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.speedSearch.FilteringListModel
import icons.StudioIcons

import javax.swing.*
import javax.swing.event.DocumentEvent
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * "Add" popup menu in the navigation editor.
 */
@VisibleForTesting
class AddExistingDestinationMenu(
  surface: NavDesignSurface,
  @field:VisibleForTesting val destinations: List<Destination>
) : NavToolbarMenu(surface, "Add Destination", StudioIcons.NavEditor.Toolbar.ADD_EXISTING) {

  private var listModel: FilteringListModel<Destination> = FilteringListModel(CollectionListModel<Destination>(destinations))

  @Suppress("UNCHECKED_CAST")
  @VisibleForTesting
  var destinationsList = object : JBList<Destination>(listModel as ListModel<Destination>) {
    override fun getPreferredScrollableViewportSize(): Dimension {
      return Dimension(252, 300)
    }
  }

  private var mediaTracker = MediaTracker(destinationsList)

  private var loadingPanel: JBLoadingPanel = JBLoadingPanel(BorderLayout(), surface)

  @VisibleForTesting
  var mySearchField = SearchTextField()

  override val mainPanel = createSelectionPanel()

  private fun createSelectionPanel(): JPanel {
    listModel.setFilter { destination -> destination.label.toLowerCase().contains(mySearchField.text.toLowerCase()) }

    destinationsList.setCellRenderer { _, value, _, _, _ ->
      THUMBNAIL_RENDERER.icon = ImageIcon(value.thumbnail.getScaledInstance(50, 64, Image.SCALE_SMOOTH))
      PRIMARY_TEXT_RENDERER.text = value.label
      SECONDARY_TEXT_RENDERER.text = value.typeLabel
      RENDERER
    }

    destinationsList.background = null
    destinationsList.addMouseMotionListener(
        object : MouseAdapter() {
          override fun mouseMoved(event: MouseEvent) {
            val index = destinationsList.locationToIndex(event.point)
            if (index != -1) {
              destinationsList.selectedIndex = index
              destinationsList.requestFocusInWindow()
            } else {
              destinationsList.clearSelection()
            }
          }
        }
    )

    val selectionPanel = AdtSecondaryPanel(VerticalLayout(5))
    destinationsList.background = selectionPanel.background
    selectionPanel.add(mySearchField)
    mySearchField.addDocumentListener(
        object : DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            listModel.refilter()
          }
        }
    )

    val scrollPane = JBScrollPane(destinationsList)
    scrollPane.border = BorderFactory.createEmptyBorder()

    destinations.forEach { destination -> mediaTracker.addImage(destination.thumbnail, 0) }
    if (!mediaTracker.checkAll()) {
      loadingPanel.add(scrollPane, BorderLayout.CENTER)
      loadingPanel.startLoading()

      ApplicationManager.getApplication().executeOnPooledThread {
        try {
          mediaTracker.waitForAll()
          ApplicationManager.getApplication().invokeLater { loadingPanel.stopLoading() }
        } catch (e: Exception) {
          loadingPanel.setLoadingText("Failed to load thumbnails")
        }
      }

      selectionPanel.add(loadingPanel)
    } else {
      selectionPanel.add(scrollPane)
    }
    destinationsList.addMouseListener(
        object : MouseAdapter() {
          override fun mouseClicked(event: MouseEvent) {
            val action = object : AnAction() {
              override fun actionPerformed(e: AnActionEvent) {
                val element = destinationsList.selectedValue
                if (element != null) {
                  element.addToGraph()
                  // explicitly update so the new SceneComponent is created
                  surface.sceneManager!!.update()
                  val component = element.component
                  surface.selectionModel.setSelection(ImmutableList.of(component!!))
                  surface.scrollToCenter(ImmutableList.of(component))
                }
              }
            }
            ActionManager.getInstance().tryToExecute(action, event, event.component, ActionPlaces.TOOLBAR, true)
          }
        }
    )
    return selectionPanel
  }

  companion object {

    private val RENDERER = AdtSecondaryPanel(BorderLayout())
    private val THUMBNAIL_RENDERER = JBLabel()
    private val PRIMARY_TEXT_RENDERER = JBLabel()
    private val SECONDARY_TEXT_RENDERER = JBLabel()

    init {
      SECONDARY_TEXT_RENDERER.foreground = NavColorSet.SUBDUED_TEXT_COLOR
      RENDERER.add(THUMBNAIL_RENDERER, BorderLayout.WEST)
      val leftPanel = JPanel(VerticalLayout(8))
      leftPanel.border = BorderFactory.createEmptyBorder(12, 6, 0, 0)
      leftPanel.add(PRIMARY_TEXT_RENDERER, VerticalLayout.CENTER)
      leftPanel.add(SECONDARY_TEXT_RENDERER, VerticalLayout.CENTER)
      RENDERER.add(leftPanel, BorderLayout.CENTER)
    }
  }
}

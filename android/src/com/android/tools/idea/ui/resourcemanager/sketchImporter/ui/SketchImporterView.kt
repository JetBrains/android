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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.ui

import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetSet
import com.android.tools.idea.ui.resourcemanager.view.DesignAssetCellRenderer
import com.android.tools.idea.ui.resourcemanager.widget.SingleAssetCard
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.BorderFactory
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

private val PAGE_HEADER_SECONDARY_COLOR = Gray.x66
private val PAGE_HEADER_BORDER = BorderFactory.createCompoundBorder(
  BorderFactory.createEmptyBorder(0, 0, 8, 0),
  JBUI.Borders.customLine(PAGE_HEADER_SECONDARY_COLOR, 0, 0, 1, 0)
)
private val PANEL_SIZE = JBUI.size(600, 400)
private val ASSET_FIXED_WIDTH = JBUI.scale(150)
private val ASSET_FIXED_HEIGHT = JBUI.scale(150)

const val IMPORT_DIALOG_TITLE = "Choose the assets you would like to import"
const val FILTER_EXPORTABLE_CHECKBOX_TEXT = "Only show exportable assets"
const val FILTER_EXPORTABLE_TOOLTIP_TEXT = "Any item that has at least one export format in Sketch is considered exportable"
const val NO_VALID_ASSETS_TEXT = "No valid assets"

const val DOCUMENT_HEADER = "Document"

/**
 * The view in the MVP pattern developed for the Sketch Importer UI, deals with the actual interface and doesn't know anything about the
 * model. It doesn't contain any logic.
 */
class SketchImporterView : JPanel(BorderLayout()) {

  lateinit var presenter: SketchImporterPresenter
  private val pageViews = mutableListOf<PageView>()
  private lateinit var documentView: DocumentView

  private val resourcesPanel = JPanel(VerticalFlowLayout())

  init {
    preferredSize = PANEL_SIZE
    add(JScrollPane(resourcesPanel).apply {
      border = null
      horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    })
  }

  fun addFilterExportableButton(defaultState: Boolean) {
    val filterExportableButton = JCheckBox(FILTER_EXPORTABLE_CHECKBOX_TEXT).apply {
      toolTipText = FILTER_EXPORTABLE_TOOLTIP_TEXT
      isSelected = defaultState
      horizontalTextPosition = JCheckBox.LEFT
      horizontalAlignment = JCheckBox.RIGHT
      addItemListener { event ->
        presenter.filterExportable(event.stateChange)
      }
    }
    add(filterExportableButton, BorderLayout.NORTH)
  }

  /**
   * Adds a new [PageView] to the [SketchImporterView], associating it to the [pagePresenter].
   */
  fun addPageView(pagePresenter: PagePresenter) {
    val pageView = PageView(DesignAssetCellRenderer(pagePresenter.assetPreviewManager),
                            ColorAssetCellRenderer())
    pagePresenter.view = pageView
    pageViews.add(pageView)
    resourcesPanel.add(pageView)
  }

  fun addDocumentView(documentPresenter: DocumentPresenter) {
    documentView = DocumentView(DesignAssetCellRenderer(documentPresenter.assetPreviewManager),
                                ColorAssetCellRenderer())
    documentPresenter.view = documentView
    resourcesPanel.add(documentView)
    resourcesPanel.repaint()
  }
}

open class ChildView(protected val designAssetCellRenderer: DesignAssetCellRenderer,
                     protected val colorAssetCellRenderer: ColorAssetCellRenderer) : JPanel(BorderLayout()) {
  protected var resourcesView = ResourcesView(emptyList(), emptyList(), designAssetCellRenderer, colorAssetCellRenderer)

  fun getSelectedDrawables(): List<DesignAssetSet> {
    return resourcesView.drawables?.selectedValuesList?.map { it as DesignAssetSet } ?: listOf()
  }

  fun getSelectedColors(): List<Pair<Color, String>> {
    return resourcesView.colors?.selectedValuesList?.map { it as Pair<Color, String> } ?: listOf()
  }
}

class PageView(designAssetCellRenderer: DesignAssetCellRenderer,
               colorAssetCellRenderer: ColorAssetCellRenderer
) : ChildView(designAssetCellRenderer, colorAssetCellRenderer) {
  /**
   * Create/refresh the preview panel associated with the page.
   */
  fun refresh(pageName: String,
              drawableAssets: List<DesignAssetSet>,
              colorAssets: List<Pair<Color, String>>) {
    removeAll()
    add(createHeader(pageName), BorderLayout.NORTH)
    resourcesView = ResourcesView(drawableAssets, colorAssets, designAssetCellRenderer, colorAssetCellRenderer)
    add(resourcesView)
    revalidate()
    repaint()
  }
}

/**
 * The document view is currently just like a page called "Document", but it is created separately so it can be changed easily in the future.
 */
class DocumentView(designAssetCellRenderer: DesignAssetCellRenderer,
                   colorAssetCellRenderer: ColorAssetCellRenderer
) : ChildView(designAssetCellRenderer, colorAssetCellRenderer) {
  /**
   * Create/refresh the preview panel associated with the document.
   */
  fun refresh(drawableAssets: List<DesignAssetSet>,
              colorAssets: List<Pair<Color, String>>) {
    removeAll()
    add(createHeader(DOCUMENT_HEADER), BorderLayout.NORTH)
    resourcesView = ResourcesView(drawableAssets, colorAssets, designAssetCellRenderer, colorAssetCellRenderer)
    add(resourcesView)
    revalidate()
    repaint()
  }
}

/**
 * Create a page header containing the [pageName].
 */
private fun createHeader(pageName: String): JComponent {
  return JPanel(BorderLayout()).apply {
    val nameLabel = JBLabel(pageName)
    nameLabel.font = nameLabel.font.deriveFont(24f)
    add(nameLabel)
    border = PAGE_HEADER_BORDER
  }
}

class ResourcesView(drawableAssets: List<DesignAssetSet>,
                    colorAssets: List<Pair<Color, String>>,
                    designAssetCellRenderer: DesignAssetCellRenderer,
                    colorResourceCellRenderer: ColorAssetCellRenderer
) : JTabbedPane(JTabbedPane.NORTH) {
  val drawables = createDrawablesPreviewsList(drawableAssets, designAssetCellRenderer)
  val colors = createColorsPreviewsList(colorAssets, colorResourceCellRenderer)

  init {
    tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT
    addTab(ResourceType.DRAWABLE.displayName, createAssetList(drawables))
    addTab(ResourceType.COLOR.displayName, createAssetList(colors))
    addChangeListener { resizeTabbedPane() }
    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) = resizeTabbedPane()
    })
  }

  private fun createAssetList(list: JList<*>?): Component {
    return JPanel(BorderLayout()).apply {
      add((list ?: JLabel(NO_VALID_ASSETS_TEXT)) as Component)
    }
  }
}

/**
 * The tabbed pane has to be resized to wrap around the selected content every time a window is resized or a different tab is selected.
 */
private fun JTabbedPane.resizeTabbedPane() {
  preferredSize = Dimension(selectedComponent.preferredSize.width,
                            selectedComponent.preferredSize.height + ui.getTabBounds(this, 0).height)
  revalidate()
  repaint()
}

/**
 * Create a [JList] with the rendering of the [assetList] drawable assets.
 */
private fun createDrawablesPreviewsList(assetList: List<DesignAssetSet>, designAssetCellRenderer: DesignAssetCellRenderer): JList<*>? {
  if (assetList.isNotEmpty()) {
    return JList<DesignAssetSet>().apply {
      cellRenderer = designAssetCellRenderer
      model = CollectionListModel(assetList)
      setUI()
    }
  }
  return null
}

/**
 * Create a [JList] with the rendering of the [assetList] color assets.
 */
private fun createColorsPreviewsList(assetList: List<Pair<Color, String>>, colorAssetCellRenderer: ColorAssetCellRenderer): JList<*>? {
  if (assetList.isNotEmpty()) {
    return JList<Pair<Color, String>>().apply {
      cellRenderer = colorAssetCellRenderer
      model = CollectionListModel(assetList)
      setUI()
    }
  }
  return null
}

private fun JList<*>.setUI() {
  fixedCellWidth = ASSET_FIXED_WIDTH
  fixedCellHeight = ASSET_FIXED_HEIGHT
  selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
  visibleRowCount = -1
  layoutOrientation = JList.HORIZONTAL_WRAP
}

class ColorAssetCellRenderer : ListCellRenderer<Pair<Color, String>> {

  private val cardView = SingleAssetCard().apply {
    withChessboard = true
  }

  override fun getListCellRendererComponent(list: JList<out Pair<Color, String>>,
                                            value: Pair<Color, String>,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component? {
    cardView.title = "#${ColorUtil.toHex(value.first)}"
    cardView.subtitle = value.second
    val thumbnailSize = cardView.thumbnailSize
    cardView.thumbnail = JPanel(BorderLayout()).apply {
      background = value.first
      size = thumbnailSize
    }
    cardView.selected = isSelected
    return cardView
  }
}

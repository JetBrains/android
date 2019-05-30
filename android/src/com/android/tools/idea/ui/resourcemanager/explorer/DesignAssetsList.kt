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
package com.android.tools.idea.ui.resourcemanager.explorer

import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.model.DesignAssetSet
import com.google.common.cache.CacheBuilder
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Image
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListModel


private val ICON_SIZE = JBUI.size(64)
private const val ITEM_BORDER_WIDTH = 1

/**
 * A JList that display [DesignAssetSet].
 */
class DesignAssetsList(
  private val browserViewModel: DesignAssetExplorer
) : JList<DesignAssetSet>(browserViewModel.designAssetListModel) {

  var itemMargin = 16

  private val assetToImage = CacheBuilder.newBuilder().softValues().build<DesignAssetSet, Image>()

  init {
    layoutOrientation = JList.HORIZONTAL_WRAP
    visibleRowCount = 0
    cellRenderer = Renderer()
  }

  inner class Renderer :
    JPanel(BorderLayout()),
    ListCellRenderer<DesignAssetSet> {

    private val imageIcon = ImageIcon(EMPTY_ICON)
    private val nameLabel = JLabel("", JLabel.CENTER)
    private val statusLabel = JLabel("", JLabel.CENTER)

    init {
      add(JLabel(imageIcon).apply {
        border = JBUI.Borders.empty(18)
      })
      add(nameLabel, BorderLayout.SOUTH)
      add(statusLabel, BorderLayout.NORTH)
    }

    override fun getListCellRendererComponent(
      list: JList<out DesignAssetSet>?,
      assetSet: DesignAssetSet,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean
    ): Component {
      return getDesignAssetView(
        assetSet,
        assetSet.name,
        isSelected,
        browserViewModel.getStatusLabel(assetSet)
      )
    }

    private fun getDesignAssetView(
      asset: DesignAssetSet,
      name: String,
      selected: Boolean = false,
      statusLabel: String
    ): JPanel {
      border = if (selected) {
        isOpaque = true
        background = UIUtil.getListUnfocusedSelectionBackground()
        val emptyBorderWidth = itemMargin - ITEM_BORDER_WIDTH
        BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(UIUtil.getListSelectionBackground(), ITEM_BORDER_WIDTH, true),
          BorderFactory.createEmptyBorder(emptyBorderWidth, emptyBorderWidth, emptyBorderWidth, emptyBorderWidth)
        )
      } else {
        isOpaque = false
        BorderFactory.createEmptyBorder(itemMargin, itemMargin, itemMargin, itemMargin)
      }

      this.statusLabel.text = statusLabel
      this.nameLabel.text = name
      imageIcon.image = assetToImage.getIfPresent(asset) ?: fetchImage(asset)
      return this
    }

    private fun fetchImage(asset: DesignAssetSet): Image {
      browserViewModel.getPreview(asset.getHighestDensityAsset(), ICON_SIZE).whenCompleteAsync(
        BiConsumer { image: Image?, _: Throwable ->
          if (image != null) {
            assetToImage.put(asset, image)
          } else {
            assetToImage.put(asset, EMPTY_ICON) //TODO use unsupported icon.
          }
          repaint()
        }, EdtExecutorService.getInstance())
      return EMPTY_ICON
    }
  }
}

/**
 * Interface for classes that provide [DesignAsset] for [com.android.tools.idea.ui.resourcemanager.view.DesignAssetsList]
 */
interface DesignAssetExplorer {

  /**
   * Returns a [CompletableFuture] that computes and returns an [Image] representing
   * the provided [asset] and that matches the provided [dimension].
   *
   * The [dimension] is just an indication for the implementing class and it is not
   * assured that the computed image will be exactly of these dimension.
   */
  fun getPreview(asset: DesignAsset, dimension: Dimension): CompletableFuture<out Image?>

  /**
   * Returns a status label that will be displayed on
   */
  fun getStatusLabel(assetSet: DesignAssetSet): String

  /**
   * Returns the [ListModel] for [DesignAssetsList]
   */
  val designAssetListModel: ListModel<DesignAssetSet>
}
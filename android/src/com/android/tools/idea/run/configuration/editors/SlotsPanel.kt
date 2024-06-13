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
package com.android.tools.idea.run.configuration.editors

import com.android.tools.deployer.model.component.Complication.ComplicationType
import com.android.tools.idea.run.configuration.AndroidComplicationConfiguration
import com.android.tools.idea.run.configuration.ComplicationSlot
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Image
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.HierarchyEvent
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel

/** A panel that manages complication slots selected by the user and stores them as a ComplicationsModel object. */
class SlotsPanel : JPanel(FlowLayout(FlowLayout.LEFT)) {
  @VisibleForTesting
  lateinit var slotsUiPanel: Box
  private var currentModel = ComplicationsModel()
  private val image = ImageCanvas().apply {
    preferredSize = Dimension(250, 250)
    minimumSize = Dimension(250, 250)
  }
  private val boxWithSlotList: Box
  private val imageBox: Box

  @Nls
  private val noSupportedTypeLabelText = AndroidBundle.message("android.run.configuration.complication.slots.no.type.supported")

  init {
    add(
      panel {
        group(AndroidBundle.message("android.run.configuration.complication.slots.title"), indent = false) {
          row { label(AndroidBundle.message("android.run.configuration.complication.will.run.in.debug")) }
          row { slotsUiPanel = cell(Box.createVerticalBox()).component }
        }
      }
    )
    boxWithSlotList = Box.createVerticalBox()
    boxWithSlotList.alignmentY = TOP_ALIGNMENT
    populateSlotList()
    val mainBox = Box.createHorizontalBox().apply {
      preferredSize = Dimension(650, 300)
    }
    mainBox.add(boxWithSlotList)
    mainBox.add(Box.createGlue())
    imageBox = Box.createVerticalBox()
    imageBox.alignmentY = TOP_ALIGNMENT
    image.updateCurrentModel(currentModel)
    imageBox.add(image, BorderLayout.CENTER)
    mainBox.add(imageBox)

    slotsUiPanel.add(mainBox)
  }

  private fun repaintSlotsComponent() {
    boxWithSlotList.removeAll()
    populateSlotList()
    boxWithSlotList.revalidate()
    boxWithSlotList.repaint()

    imageBox.removeAll()
    image.updateCurrentModel(currentModel)
    imageBox.add(image, BorderLayout.CENTER)
    imageBox.revalidate()
    imageBox.repaint()
  }

  private fun populateSlotList() {
    currentModel.allAvailableSlots.forEach { availableSlot ->
      var chosen = false
      currentModel.currentChosenSlots.forEach { chosenSlot ->
        if (availableSlot.slotId == chosenSlot.id) {
          boxWithSlotList.add(createSlot(chosenSlot))
          chosen = true
        }
      }
      if (!chosen) {
        boxWithSlotList.add(createSlot(AndroidComplicationConfiguration.ChosenSlot(availableSlot.slotId, null)), BorderLayout.CENTER)
      }
    }
  }

  private fun update() {
    repaintSlotsComponent()
  }

  private fun typesSupportedBySlot(slotId: Int) = currentModel.allAvailableSlots.first { it.slotId == slotId }.supportedTypes

  private fun getSlotTypeCompoBox(chosenSlot: AndroidComplicationConfiguration.ChosenSlot): ComboBox<ComplicationType> {
    val options = typesSupportedBySlot(chosenSlot.id).intersect(currentModel.supportedTypes.toSet()).toTypedArray()
    if (chosenSlot.type !in options) {
      chosenSlot.type = options.firstOrNull()
    }
    return ComboBox(options).apply {
      renderer = object : SimpleListCellRenderer<ComplicationType>() {
        override fun customize(list: JList<out ComplicationType>,
                               value: ComplicationType?,
                               index: Int,
                               selected: Boolean,
                               hasFocus: Boolean) {
          text = value?.name ?: noSupportedTypeLabelText
        }
      }
      if (chosenSlot.type == null) {
        border = BorderFactory.createLineBorder(JBColor.ORANGE)
      }
      preferredSize = Dimension(240, preferredSize.height)
      item = chosenSlot.type
      isEnabled = options.isNotEmpty()
      addActionListener {
        chosenSlot.type = this.item
        update()
      }
    }
  }

  private fun createSlot(chosenSlot: AndroidComplicationConfiguration.ChosenSlot): JPanel {
    val isSelected = chosenSlot.id in currentModel.currentChosenSlots.map { it.id }
    val hasSupportedType = typesSupportedBySlot(chosenSlot.id).intersect(currentModel.supportedTypes.toSet()).isNotEmpty()
    val isBackgroundImageSlot = typesSupportedBySlot(chosenSlot.id).contentEquals(arrayOf(ComplicationType.LARGE_IMAGE))
    if (isBackgroundImageSlot) {
      chosenSlot.type = ComplicationType.LARGE_IMAGE
    }

    return JPanel().apply {
      layout = FlowLayout(FlowLayout.LEFT)
      // the background slot only has a single possible type, so
      // we simplify the UI by removing the ComboBox
      val typeBox = if (!isBackgroundImageSlot) getSlotTypeCompoBox(chosenSlot).also {
        it.addFocusListener(object : FocusListener {
          override fun focusGained(e: FocusEvent?) {
            chosenSlot.slotFocused = true
          }

          override fun focusLost(e: FocusEvent?) {
            chosenSlot.slotFocused = false
          }
        })
      }
      else null
      val selectedBox = JCheckBox().apply {
        addActionListener {
          chooseSlot(chosenSlot, this.isSelected)
          typeBox?.isEnabled = this.isSelected
          image.repaint()
        }
      }
      typeBox?.isEnabled = isSelected
      selectedBox.isEnabled = hasSupportedType
      selectedBox.isSelected = isSelected
      add(selectedBox)

      if (chosenSlot.slotFocused) {
        addHierarchyListener {
          if (it.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong() == 0L) return@addHierarchyListener

          if (chosenSlot.slotFocused) {
            typeBox?.requestFocus()
          }
        }
      }

      add(JLabel(currentModel.allAvailableSlots.first { it.slotId == chosenSlot.id }.name).apply {
        isEnabled = hasSupportedType
        border = JBUI.Borders.emptyRight(5)
        if (!isBackgroundImageSlot) {
          preferredSize = Dimension(60, preferredSize.height)
        }
      }
      )

      if (typeBox != null) {
        // Slot type ComboBox.
        add(typeBox)
      }

      if (!hasSupportedType) {
        toolTipText = noSupportedTypeLabelText
      }
      // prevent stretching
      maximumSize = Dimension(maximumSize.width, preferredSize.height)
    }
  }

  private fun chooseSlot(chosenSlot: AndroidComplicationConfiguration.ChosenSlot, isSelected: Boolean) {
    if (isSelected) {
      currentModel.currentChosenSlots.add(chosenSlot)
    }
    else {
      currentModel.currentChosenSlots.removeIf { it.id == chosenSlot.id }
    }
  }

  fun setModel(model: ComplicationsModel) {
    if (model != currentModel) {
      currentModel = model.copy()
      update()
    }
  }

  fun getModel(): ComplicationsModel {
    return currentModel.copy()
  }

  data class ComplicationsModel(val currentChosenSlots: MutableList<AndroidComplicationConfiguration.ChosenSlot> = arrayListOf(),
                                val allAvailableSlots: List<ComplicationSlot> = emptyList(),
                                val supportedTypes: List<ComplicationType> = emptyList()) {
  }

  class ImageCanvas : JPanel() {
    private val sizePx = 240
    private val sideMarginPx = (sizePx * 0.16).toInt()
    private val topMarginPx = (sizePx * 0.2).toInt()
    private lateinit var currentModel: ComplicationsModel
    val watchFacePicture = ImageIO.read(SlotsPanel::class.java.classLoader.getResource("images/watchface/watchface.png"))
    private val typeToIcon = mapOf(ComplicationType.SHORT_TEXT to ImageIO.read(
      SlotsPanel::class.java.classLoader.getResource("images/watchface/short_text.png")),
                                   ComplicationType.LONG_TEXT to ImageIO.read(
                                     SlotsPanel::class.java.classLoader.getResource("images/watchface/long_text.png")),
                                   ComplicationType.ICON to ImageIO.read(
                                     SlotsPanel::class.java.classLoader.getResource("images/watchface/icon.png")),
                                   ComplicationType.SMALL_IMAGE to ImageIO.read(
                                     SlotsPanel::class.java.classLoader.getResource("images/watchface/small_image.png")),
                                   ComplicationType.RANGED_VALUE to ImageIO.read(
                                     SlotsPanel::class.java.classLoader.getResource("images/watchface/ranged_value.png")),
                                   ComplicationType.LARGE_IMAGE to ImageIO.read(
                                     SlotsPanel::class.java.classLoader.getResource("images/watchface/large_image.png")))
    private val slotCoordinates = listOf(Pair(sizePx / 2, topMarginPx),
                                         Pair(sizePx - sideMarginPx, sizePx / 2),
                                         Pair(sizePx / 2, sizePx - topMarginPx),
                                         Pair(sideMarginPx, sizePx / 2),
                                         Pair(sizePx / 2, sizePx / 2))

    fun updateCurrentModel(newModel: ComplicationsModel) {
      currentModel = newModel
    }

    public override fun paintComponent(g: Graphics?) {
      if (g != null) {
        super.paintComponent(g)
      }
      g?.drawImage(watchFacePicture.getScaledInstance(sizePx, sizePx, Image.SCALE_DEFAULT), 0, 0, null)
      g?.color = JBColor.RED
      // We first draw the LARGE_IMAGE complication, as other complications will overlap with it.
      val largeImageSlot = currentModel.currentChosenSlots.firstOrNull { it.type == ComplicationType.LARGE_IMAGE }
      if (largeImageSlot != null) {
        drawComplication(g, largeImageSlot.type!!, largeImageSlot.id)
      }
      for (chosenSlot in currentModel.currentChosenSlots) {
        val type = chosenSlot.type
        if (type != null && type != ComplicationType.LARGE_IMAGE) {
          drawComplication(g, type, chosenSlot.id)
        }
      }
    }

    private fun drawComplication(g: Graphics?, type: ComplicationType, slotNumber: Int) {
      val width = typeToIcon[type]?.width ?: 0
      val height = typeToIcon[type]?.height ?: 0
      g?.drawImage(typeToIcon[type],
                   slotCoordinates[slotNumber].first - width / 2,
                   slotCoordinates[slotNumber].second - height / 2,
                   null)
    }
  }
}
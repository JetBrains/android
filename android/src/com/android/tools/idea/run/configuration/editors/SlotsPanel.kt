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
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.TitledSeparator
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.HierarchyEvent
import java.awt.Graphics
import java.awt.Image
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel

/** A panel that manages complication slots selected by the user and stores them as a ComplicationsModel object. */
class SlotsPanel() : JPanel(FlowLayout(FlowLayout.LEFT)) {
  @VisibleForTesting
  lateinit var slotsUiPanel: Box
  private var currentModel = ComplicationsModel()
  private val image = ImageCanvas().apply {
    preferredSize = Dimension(250, 250)
    minimumSize = Dimension(250, 250)
  }
  private val boxWithSlotList : Box
  private val imageBox : Box
  init {
    add(
      panel {
        row {
          slotsUiPanel = component(Box.createVerticalBox()).component
        }
      }
    )
    boxWithSlotList = Box.createVerticalBox()
    populateSlotList()
    val mainBox = Box.createHorizontalBox().apply {
      preferredSize = Dimension(1000, 300)
    }
    mainBox.add(boxWithSlotList)
    mainBox.add(Box.createGlue())
    imageBox = Box.createVerticalBox()
    image.updateCurrentModel(currentModel)
    imageBox.add(image, BorderLayout.CENTER)
    mainBox.add(imageBox)

    slotsUiPanel.add(TitledSeparator("Slot launch options"))
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
    currentModel.allAvailableSlots.forEach {availableSlot ->
      var chosen = false
      currentModel.currentChosenSlots.forEach {chosenSlot ->
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
    val options = typesSupportedBySlot(chosenSlot.id).intersect(currentModel.supportedTypes).toTypedArray()
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
          text = value?.name ?: "No type is supported by this slot"
        }
      }
      if (chosenSlot.type == null) {
        border = BorderFactory.createLineBorder(Color.ORANGE)
      }
      preferredSize = Dimension(350, preferredSize.height)
      item = chosenSlot.type
      isEnabled = options.isNotEmpty()
      addActionListener {
        chosenSlot.type = this.item
        update()
      }
    }
  }

  private fun createSlot(chosenSlot: AndroidComplicationConfiguration.ChosenSlot): JPanel {
    val isSelected = chosenSlot.id in currentModel.currentChosenSlots.map{it.id}
    return JPanel().apply {
      val typeBox = getSlotTypeCompoBox(chosenSlot).also {
        it.addFocusListener(object: FocusListener {
          override fun focusGained(e: FocusEvent?) {
            chosenSlot.slotFocused = true
          }

          override fun focusLost(e: FocusEvent?) {
            chosenSlot.slotFocused = false
          }
        })
      }
      val selectedBox = JCheckBox().apply {
        addActionListener {
          chooseSlot(chosenSlot, this.isSelected)
          typeBox.isEnabled = this.isSelected}
      }
      typeBox.isEnabled = isSelected
      selectedBox.isEnabled = typesSupportedBySlot(chosenSlot.id).intersect(currentModel.supportedTypes).isNotEmpty()
      selectedBox.isSelected = isSelected
      add(selectedBox)

      if (chosenSlot.slotFocused) {
        addHierarchyListener {
          if (it.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong() == 0L) return@addHierarchyListener

          if (chosenSlot.slotFocused) {
            typeBox.requestFocus()
          }
        }
      }
      add(JLabel(currentModel.allAvailableSlots.first { it.slotId == chosenSlot.id }.name).apply {
        border = JBUI.Borders.empty(0, 0, 0, 5)
        preferredSize = Dimension(100, preferredSize.height) }
      )

      // Slot type ComboBox.
      add(typeBox)
    }
  }

  private fun chooseSlot(chosenSlot: AndroidComplicationConfiguration.ChosenSlot, isSelected: Boolean) {
    if (isSelected) {
      currentModel.currentChosenSlots.add(chosenSlot)
    } else {
      currentModel.currentChosenSlots.removeIf {it.id == chosenSlot.id}
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
      g?.color = Color.red
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
                   slotCoordinates[slotNumber]!!.first - width / 2,
                   slotCoordinates[slotNumber]!!.second - height / 2,
                   null)
    }
  }
}
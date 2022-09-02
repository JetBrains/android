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

import com.android.tools.deployer.model.component.Complication
import com.android.tools.idea.run.configuration.AndroidComplicationConfiguration
import com.android.tools.idea.run.configuration.ComplicationSlot
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.HierarchyEvent
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel

/** A panel that manages complication slots selected by the user and stores them as a ComplicationsModel object. */
class SlotsPanel() : JPanel(FlowLayout(FlowLayout.LEFT)) {
  private lateinit var addSlotLink: JComponent
  @VisibleForTesting
  lateinit var slotsComponent: JPanel
  private var currentModel = ComplicationsModel()

  init {
    add(
      panel {
        row {
          label("Slot launch options")
        }
        row {
          slotsComponent = component(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
          })
            .component
        }

        row {
          addSlotLink = link("+ Add Slot", style = null) {
            val nextAvailableSlot = currentModel.allAvailableSlots.first { availableSlot -> !isSlotIdChosen(availableSlot.slotId) }
            currentModel.currentChosenSlots.add(
              AndroidComplicationConfiguration.ChosenSlot(nextAvailableSlot.slotId, nextAvailableSlot.supportedTypes.first())
            )
            update()
          }.component
        }
      }
    )
    addSlotLink.isEnabled = currentModel.currentChosenSlots.size < currentModel.allAvailableSlots.size
  }

  private fun isSlotIdChosen(id: Int): Boolean {
    return currentModel.currentChosenSlots.any { chosenSlot -> chosenSlot.id == id }
  }

  private fun repaintSlotsComponent() {
    slotsComponent.removeAll()
    currentModel.currentChosenSlots.forEach { slotsComponent.add(createSlot(it)) }
    slotsComponent.revalidate()
    slotsComponent.repaint()
  }

  private fun update() {
    addSlotLink.isEnabled = currentModel.currentChosenSlots.size < currentModel.allAvailableSlots.size
    repaintSlotsComponent()
  }

  private fun typesSupportedBySlot(slotId: Int) = currentModel.allAvailableSlots.first { it.slotId == slotId }.supportedTypes

  private fun getSlotIdComboBox(chosenSlot: AndroidComplicationConfiguration.ChosenSlot): ComboBox<Int> {
    val availableSlotIds = (currentModel.allAvailableSlots.filter { !isSlotIdChosen(it.slotId) }.map { it.slotId }
                            + chosenSlot.id).toTypedArray()
    availableSlotIds.sort()
    return ComboBox(availableSlotIds).apply {
      renderer = SimpleListCellRenderer.create { label, value, _ ->
        label.text = currentModel.allAvailableSlots.first { it.slotId == value }.name
      }
      preferredSize = Dimension(150, preferredSize.height)
      item = chosenSlot.id
      addActionListener {
        chosenSlot.id = this.item
        // We should change available slots ids in each slotIdComboBox + update supportedTypes.
        // It's easier repaint the whole [slotsComponent].
        repaintSlotsComponent()
      }
    }
  }

  private fun getSlotTypeCompoBox(chosenSlot: AndroidComplicationConfiguration.ChosenSlot): ComboBox<Complication.ComplicationType> {
    val options = typesSupportedBySlot(chosenSlot.id).intersect(currentModel.supportedTypes).toTypedArray()
    if (chosenSlot.type !in options) {
      chosenSlot.type = options.firstOrNull()
    }
    return ComboBox(options).apply {
      renderer = object : SimpleListCellRenderer<Complication.ComplicationType>() {
        override fun customize(list: JList<out Complication.ComplicationType>,
                               value: Complication.ComplicationType?,
                               index: Int,
                               selected: Boolean,
                               hasFocus: Boolean) {
          text = value?.name ?: "Complication data source doesn't provide types supported by slot"
        }
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

    return JPanel().apply {
      // Slot id ComboBox.
      add(JLabel("Slot").apply { border = JBUI.Borders.empty(0, 0, 0, 5) })
      val slotCombo = getSlotIdComboBox(chosenSlot).also {
        it.addFocusListener(object: FocusListener {
          override fun focusGained(e: FocusEvent?) {
            chosenSlot.slotFocused = true
          }

          override fun focusLost(e: FocusEvent?) {
            chosenSlot.slotFocused = false
          }
        })
      }
      add(slotCombo)

      // Slot type ComboBox.
      add(JLabel("Type").apply { border = JBUI.Borders.empty(0, 10, 0, 5) })
      val slotTypeCombo = getSlotTypeCompoBox(chosenSlot).also {
        it.addFocusListener(object: FocusListener {
          override fun focusGained(e: FocusEvent?) {
            chosenSlot.slotTypeFocused = true
          }

          override fun focusLost(e: FocusEvent?) {
            chosenSlot.slotTypeFocused = false
          }
        })
      }
      add(slotTypeCombo)

      if (chosenSlot.slotFocused || chosenSlot.slotTypeFocused) {
        addHierarchyListener {
          if (it.changeFlags and HierarchyEvent.PARENT_CHANGED.toLong() == 0L) return@addHierarchyListener

          when {
            chosenSlot.slotFocused -> slotCombo.requestFocus()
            chosenSlot.slotTypeFocused -> slotTypeCombo.requestFocus()
          }
        }
      }

      // Delete button.
      add(JButton(StudioIcons.Common.CLOSE).apply {
        addActionListener {
          currentModel.currentChosenSlots.remove(chosenSlot)
          update()
        }
        toolTipText = "Remove"
        isContentAreaFilled = false
        border = null
      })
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
                                val supportedTypes: List<Complication.ComplicationType> = emptyList()) {
  }
}
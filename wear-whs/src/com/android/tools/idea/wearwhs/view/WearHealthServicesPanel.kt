/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wearwhs.view

import com.android.tools.adtui.model.stdui.CommonAction
import com.android.tools.adtui.stdui.menu.CommonDropDownButton
import com.android.tools.idea.wearwhs.EVENT_TRIGGER_GROUPS
import com.android.tools.idea.wearwhs.WearWhsBundle
import com.android.tools.idea.wearwhs.WhsCapability
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Toolkit
import java.util.Collections
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter

private const val MAX_OVERRIDE_VALUE_LENGTH = 50

// Allows only one leading zero
private val floatPattern = Regex("^(0|0?[1-9]\\d*)?(\\.[0-9]*)?\$")

private const val PADDING = 15
private val horizontalBorders = JBUI.Borders.empty(0, PADDING)

private fun createCenterPanel(
  stateManager: WearHealthServicesStateManager,
  uiScope: CoroutineScope,
  workerScope: CoroutineScope,
  capabilities: List<WhsCapability>): JPanel {
  // List of elements that should be disabled if there's an active exercise
  val elementsToDisableDuringExercise = Collections.synchronizedList(mutableListOf<JComponent>())
  // List of elements that should be visible only if there's an active exercise
  val elementsToDisplayDuringExercise = Collections.synchronizedList(mutableListOf<JComponent>())
  stateManager.ongoingExercise.onEach {
    elementsToDisableDuringExercise.forEach { element ->
      element.isEnabled = !it
      element.toolTipText = if (it) WearWhsBundle.message("wear.whs.panel.disabled.during.exercise") else null
    }
    elementsToDisplayDuringExercise.forEach { element ->
      element.isVisible = it
    }
  }.launchIn(uiScope)
  return JPanel(VerticalFlowLayout()).apply {
    border = horizontalBorders
    add(JPanel(BorderLayout()).apply {
      add(JLabel(WearWhsBundle.message("wear.whs.panel.sensor")).apply {
        font = font.deriveFont(Font.BOLD)
      }, BorderLayout.CENTER)
      add(JPanel(FlowLayout()).apply {
        add(JLabel(WearWhsBundle.message("wear.whs.panel.override")).apply {
          elementsToDisplayDuringExercise.add(this)
          font = font.deriveFont(Font.BOLD)
        })
      }, BorderLayout.EAST)
    })
    capabilities.forEach { capability ->
      add(JPanel(BorderLayout()).apply {
        preferredSize = Dimension(0, 35)
        val label = JLabel(WearWhsBundle.message(capability.label)).also { label ->
          val plainFont = label.font.deriveFont(Font.PLAIN)
          val italicFont = label.font.deriveFont(Font.ITALIC)
          stateManager.getState(capability).map { it.synced }.onEach { synced ->
            if (!synced) {
              label.font = italicFont
              label.text = "${WearWhsBundle.message(capability.label)}*"
            }
            else {
              label.font = plainFont
              label.text = WearWhsBundle.message(capability.label)
            }
          }.launchIn(uiScope)
        }
        val checkBox = JCheckBox().also { checkBox ->
          elementsToDisableDuringExercise.add(checkBox)
          stateManager.getState(capability).map { it.capabilityState.enabled }.onEach { enabled ->
            checkBox.isSelected = enabled
          }.launchIn(uiScope)
          checkBox.addActionListener {
            workerScope.launch {
              stateManager.setCapabilityEnabled(capability, checkBox.isSelected)
              stateManager.preset.value = Preset.CUSTOM
            }
          }
        }
        stateManager.ongoingExercise.onEach {
          label.isEnabled = !it || checkBox.isSelected
        }.launchIn(uiScope)

        add(checkBox, BorderLayout.LINE_START)
        add(label, BorderLayout.CENTER)
        add(JPanel(FlowLayout()).apply {
          elementsToDisplayDuringExercise.add(this)
          add(JTextField().also { textField ->
            (textField.document as AbstractDocument).documentFilter = object : DocumentFilter() {
              fun validate(string: String): Boolean {
                if (!floatPattern.matches(string) || string.length > MAX_OVERRIDE_VALUE_LENGTH) {
                  return false
                }
                workerScope.launch {
                  stateManager.setOverrideValue(capability, string.toFloatOrNull())
                }
                return true
              }

              override fun insertString(fb: FilterBypass, offset: Int, text: String, attr: AttributeSet?) {
                val newValue =
                  fb.document.getText(0, offset) +
                  text +
                  fb.document.getText(offset, fb.document.length - offset)

                if (validate(newValue)) {
                  super.insertString(fb, offset, text, attr)
                }
                else {
                  Toolkit.getDefaultToolkit().beep()
                }
              }

              override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String, attr: AttributeSet?) {
                val newValue =
                  fb.document.getText(0, offset) +
                  text +
                  fb.document.getText(offset + length, fb.document.length - offset - length)

                if (validate(newValue)) {
                  super.replace(fb, offset, length, text, attr)
                }
                else {
                  Toolkit.getDefaultToolkit().beep()
                }
              }
            }
            stateManager.getState(capability).map { it.capabilityState.overrideValue }.onEach {
              if (!textField.isFocusOwner && textField.text.toFloatOrNull() != it) {
                textField.text = it?.toString() ?: ""
              }
            }.launchIn(uiScope)
            textField.preferredSize = JBUI.size(75, 25)
            textField.isEnabled = checkBox.isSelected
            checkBox.selected.addListener {
              textField.isEnabled = it
            }
            textField.isVisible = capability.isOverrideable
          })
          add(JLabel(WearWhsBundle.message(capability.unit)).also { label ->
            checkBox.selected.addListener {
              label.isEnabled = it
            }
            label.isVisible = capability.isOverrideable
            label.preferredSize = JBUI.size(75, 25)
          })
        }, BorderLayout.EAST)
      })
    }
  }
}

private fun createWearHealthServicesPanelHeader(
  stateManager: WearHealthServicesStateManager,
  uiScope: CoroutineScope,
  workerScope: CoroutineScope,
): JPanel = panel {
  row(JBLabel(WearWhsBundle.message("wear.whs.panel.title")).apply {
      foreground = UIUtil.getInactiveTextColor()
    }) {}
  separator()

  val capabilitiesComboBox = ComboBox<Preset>().apply {
    model = DefaultComboBoxModel(Preset.values())
  }
  capabilitiesComboBox.addActionListener {
    stateManager.preset.value = (capabilitiesComboBox.selectedItem as Preset)
  }
  stateManager.preset.onEach {
    capabilitiesComboBox.selectedItem = it
  }.launchIn(uiScope)
  stateManager.ongoingExercise.onEach {
    capabilitiesComboBox.isEnabled = !it
    capabilitiesComboBox.toolTipText = if (it) WearWhsBundle.message("wear.whs.panel.disabled.during.exercise") else null
  }.launchIn(uiScope)
  val eventTriggersDropDownButton = CommonDropDownButton(
    CommonAction("", AllIcons.Actions.More).apply {
      addChildrenActions(
        EVENT_TRIGGER_GROUPS.map { eventTriggerGroup ->
          CommonAction(eventTriggerGroup.eventGroupLabel, null).apply {
            addChildrenActions(eventTriggerGroup.eventTriggers.map { eventTrigger ->
              CommonAction(eventTrigger.eventLabel, null) {
                workerScope.launch {
                  stateManager.triggerEvent(eventTrigger)
                }
              }
            })
          }
        }
      )
    }).apply {
      toolTipText = WearWhsBundle.message("wear.whs.panel.trigger.events")
    }
  val statusLabel = JLabel(WearWhsBundle.message("wear.whs.panel.test.data.inactive")).apply {
    icon = StudioIcons.Common.INFO
    stateManager.ongoingExercise.onEach {
      if (it) {
        this.text = WearWhsBundle.message("wear.whs.panel.test.data.active")
        this.toolTipText = WearWhsBundle.message("wear.whs.panel.press.apply.for.overrides")
      }
      else {
        this.text = WearWhsBundle.message("wear.whs.panel.test.data.inactive")
        this.toolTipText = WearWhsBundle.message("wear.whs.panel.press.apply.for.toggles")
      }
    }.launchIn(uiScope)
  }

  twoColumnsRow(
    {
      cell(capabilitiesComboBox)
      cell(eventTriggersDropDownButton)
    },
    {
      cell(statusLabel)
    })
}

internal fun createWearHealthServicesPanel(
  stateManager: WearHealthServicesStateManager,
  uiScope: CoroutineScope,
  workerScope: CoroutineScope,
): JPanel {
  val content = JBScrollPane().apply {
    setViewportView(createCenterPanel(
      stateManager, uiScope, workerScope,
      stateManager.capabilitiesList))
  }
  val footer = JPanel(FlowLayout(FlowLayout.TRAILING)).apply {
    border = horizontalBorders
    // Display current state e.g. we encountered an error or if there's work in progress
    add(JLabel().apply {
      stateManager.status.onEach {
        text = when (it) {
          is WhsStateManagerStatus.Syncing ->
            WearWhsBundle.message("wear.whs.panel.capabilities.syncing")

          is WhsStateManagerStatus.Timeout ->
            WearWhsBundle.message("wear.whs.panel.connection.timeout")

          is WhsStateManagerStatus.ConnectionLost ->
            WearWhsBundle.message("wear.whs.panel.connection.lost")

          else -> ""
        }
      }.launchIn(uiScope)
    })
    add(JButton(WearWhsBundle.message("wear.whs.panel.reset")).apply {
      addActionListener {
        workerScope.launch {
          stateManager.reset()
        }
      }
    })
    add(JButton(WearWhsBundle.message("wear.whs.panel.apply")).apply {
      addActionListener {
        isEnabled = false
        workerScope.launch {
          try {
            stateManager.applyChanges()
          }
          finally {
            uiScope.launch {
              isEnabled = true
            }
          }
        }
      }
    })
  }
  return JPanel(BorderLayout()).apply {
    add(createWearHealthServicesPanelHeader(stateManager, uiScope, workerScope), BorderLayout.NORTH)
    add(content, BorderLayout.CENTER)
    add(footer, BorderLayout.SOUTH)
  }
}
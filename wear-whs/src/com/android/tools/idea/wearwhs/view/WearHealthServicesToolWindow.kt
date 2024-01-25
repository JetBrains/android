/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.adtui.stdui.StandardColors
import com.android.tools.adtui.stdui.menu.CommonDropDownButton
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.wearwhs.EVENT_TRIGGER_GROUPS
import com.android.tools.idea.wearwhs.WearWhsBundle.message
import com.android.tools.idea.wearwhs.WhsCapability
import com.intellij.codeInsight.hint.HintUtil.createWarningLabel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

private const val PADDING = 15

private val horizontalBorders = Borders.empty(0, PADDING)

internal class WearHealthServicesToolWindow(private val stateManager: WearHealthServicesToolWindowStateManager) : SimpleToolWindowPanel(
  true, true), Disposable {
  private val uiScope: CoroutineScope = AndroidCoroutineScope(this, uiThread)
  private val workerScope: CoroutineScope = AndroidCoroutineScope(this, workerThread)
  private var isErrorState = false

  fun setSerialNumber(serialNumber: String) {
    if (serialNumber != stateManager.serialNumber || isErrorState) {
      stateManager.serialNumber = serialNumber
      workerScope.launch {
        if (stateManager.isWhsVersionSupported()) {
          withContext(uiThread) {
            isErrorState = false
            removeAll()
            add(createContentPanel())
          }
        }
        else {
          withContext(uiThread) {
            isErrorState = true
            removeAll()
            add(createWhsVersionNotSupportedPanel())
          }
        }
      }
    }
  }

  private fun getLogger() = Logger.getInstance(this::class.java)

  private fun createWhsVersionNotSupportedPanel(): JPanel =
    JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = Borders.empty(JBUI.scale(20))
      add(Box.createVerticalGlue())
      add(JLabel("<html><center>" + message("wear.whs.panel.version.not.supported") + "</center></html>", JLabel.CENTER).apply {
        foreground = StandardColors.PLACEHOLDER_TEXT_COLOR
      })
      add(Box.createVerticalGlue())
    }

  private fun createContentPanel(): JPanel {
    if (stateManager.serialNumber == null) {
      return JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = Borders.empty(JBUI.scale(20))
        add(Box.createVerticalGlue())
        add(JLabel("<html><center>" + message("wear.whs.panel.launch.from.emulator") + "</center></html>", JLabel.CENTER).apply {
          foreground = StandardColors.PLACEHOLDER_TEXT_COLOR
        })
        add(Box.createVerticalGlue())
      }
    }
    val header = JPanel(BorderLayout()).apply {
      border = horizontalBorders
      val capabilitiesComboBox = ComboBox<Preset>().apply {
        model = DefaultComboBoxModel(Preset.values())
      }
      capabilitiesComboBox.addActionListener {
        workerScope.launch {
          stateManager.setPreset(capabilitiesComboBox.selectedItem as Preset)
        }
      }
      stateManager.getPreset().onEach {
        capabilitiesComboBox.selectedItem = it
      }.launchIn(uiScope)
      val eventTriggersDropDownButton = CommonDropDownButton(
        CommonAction("", AllIcons.Actions.More).apply {
          toolTipText = message("wear.whs.panel.trigger.events")
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
        })
      add(JPanel(FlowLayout(FlowLayout.LEADING)).apply {
        add(capabilitiesComboBox)
        add(eventTriggersDropDownButton)
      }, BorderLayout.WEST)
      add(JLabel(message("wear.whs.panel.test.data.inactive")).apply {
        icon = StudioIcons.Common.INFO
        stateManager.getOngoingExercise().onEach {
          if (it) {
            this.text = message("wear.whs.panel.test.data.active")
            this.toolTipText = message("wear.whs.panel.press.apply.for.overrides")
          }
          else {
            this.text = message("wear.whs.panel.test.data.inactive")
            this.toolTipText = message("wear.whs.panel.press.apply.for.toggles")
          }
        }.launchIn(uiScope)
      }, BorderLayout.EAST)
    }
    val content = JBScrollPane().apply {
      stateManager.getCapabilitiesList().onEach { capabilities ->
        setViewportView(createCenterPanel(capabilities))
      }.launchIn(uiScope)
    }
    val footer = JPanel(FlowLayout(FlowLayout.TRAILING)).apply {
      border = horizontalBorders
      // Display current state e.g. we encountered an error or if there's work in progress
      add(JLabel().apply {
        stateManager.getStatus().onEach {
          when (it) {
            is WhsStateManagerStatus.Ready -> {
              text = ""
            }

            is WhsStateManagerStatus.Syncing -> {
              text = message("wear.whs.panel.capabilities.syncing")
            }

            is WhsStateManagerStatus.ConnectionLost -> {
              text = message("wear.whs.panel.connection.lost")
            }

            is WhsStateManagerStatus.Idle -> {
              text = ""
            }

            else -> {}
          }
        }.launchIn(uiScope)
      })
      add(JButton(message("wear.whs.panel.reset")).apply {
        addActionListener {
          workerScope.launch {
            stateManager.reset()
          }
        }
      })
      add(JButton(message("wear.whs.panel.apply")).apply {
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
      add(header, BorderLayout.NORTH)
      add(content, BorderLayout.CENTER)
      add(footer, BorderLayout.SOUTH)
    }
  }

  private fun createCenterPanel(capabilities: List<WhsCapability>): JPanel {
    val warningLabel = createWarningLabel(message("wear.whs.panel.overridden.value.invalid")).apply {
      setBorder(Borders.empty(2))
      isVisible = false
    }
    // List of elements that should be hidden if there's an active exercise
    val overrideElementsList = mutableListOf<JComponent>()
    overrideElementsList.add(warningLabel)
    stateManager.getOngoingExercise().onEach {
      overrideElementsList.forEach { element ->
        element.isVisible = it
      }
    }.launchIn(uiScope)
    return JPanel(VerticalFlowLayout()).apply {
      add(warningLabel)
      border = horizontalBorders
      add(JPanel(BorderLayout()).apply {
        add(JLabel(message("wear.whs.panel.sensor")).apply {
          font = font.deriveFont(Font.BOLD)
        }, BorderLayout.CENTER)
        add(JPanel(FlowLayout()).apply {
          add(JLabel(message("wear.whs.panel.override")).apply {
            overrideElementsList.add(this)
            font = font.deriveFont(Font.BOLD)
          })
        }, BorderLayout.EAST)
      })
      capabilities.forEach { capability ->
        add(JPanel(BorderLayout()).apply {
          preferredSize = Dimension(0, 35)
          val checkBox = JCheckBox(message(capability.label)).also { checkBox ->
            val plainFont = checkBox.font.deriveFont(Font.PLAIN)
            val italicFont = checkBox.font.deriveFont(Font.ITALIC)
            stateManager.getState(capability).map { it.capabilityState.enabled }.onEach { enabled ->
              checkBox.isSelected = enabled
            }.launchIn(uiScope)
            stateManager.getState(capability).map { it.synced }.onEach { synced ->
              if (!synced) {
                checkBox.font = italicFont
                checkBox.text = "${message(capability.label)}*"
              }
              else {
                checkBox.font = plainFont
                checkBox.text = message(capability.label)
              }
            }.launchIn(uiScope)
            checkBox.addActionListener {
              workerScope.launch {
                stateManager.setCapabilityEnabled(capability, checkBox.isSelected)
                stateManager.setPreset(Preset.CUSTOM)
              }
            }
          }
          add(checkBox, BorderLayout.CENTER)
          add(JPanel(FlowLayout()).apply {
            overrideElementsList.add(this)
            add(JTextField().also { textField ->
              textField.addFocusListener(object : FocusListener {
                override fun focusGained(e: FocusEvent?) {}

                override fun focusLost(e: FocusEvent?) {
                  // Validate the field when the user navigates away and clear it
                  try {
                    textField.text.toFloat()
                  }
                  catch (exception: NumberFormatException) {
                    getLogger().warn("String is not a float")
                    textField.text = ""
                    workerScope.launch {
                      stateManager.setOverrideValue(capability, null)
                    }
                  }
                  finally {
                    warningLabel.isVisible = false
                  }
                }
              })
              textField.document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                  workerScope.launch {
                    try {
                      if (textField.text.isEmpty()) {
                        stateManager.setOverrideValue(capability, null)
                      }
                      else {
                        stateManager.setOverrideValue(capability, textField.text.toFloat())
                      }
                      uiScope.launch {
                        warningLabel.isVisible = false
                      }
                    }
                    catch (exception: NumberFormatException) {
                      getLogger().warn("String is not a float")
                      uiScope.launch {
                        warningLabel.isVisible = true
                      }
                    }
                  }
                }
              })
              stateManager.getState(capability).map { it.capabilityState.overrideValue }.onEach {
                if (!textField.isFocusOwner) {
                  textField.text = it?.toString() ?: ""
                }
              }.launchIn(uiScope)
              textField.preferredSize = Dimension(JBUI.scale(50), JBUI.scale(25))
              textField.isEnabled = checkBox.isSelected
              checkBox.selected.addListener {
                textField.isEnabled = it
              }
              textField.isVisible = capability.isOverrideable
            })
            add(JLabel(message(capability.unit)).also { label ->
              label.isVisible = capability.isOverrideable
              label.preferredSize = Dimension(JBUI.scale(50), JBUI.scale(25))
            })
          }, BorderLayout.EAST)
        })
      }
    }
  }

  init {
    add(createContentPanel())
  }

  override fun dispose() {}
}

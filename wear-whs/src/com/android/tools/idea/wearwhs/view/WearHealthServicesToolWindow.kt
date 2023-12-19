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

import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.wearwhs.WearWhsBundle.message
import com.android.tools.idea.wearwhs.WhsCapability
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

private const val PADDING = 15

private val horizontalBorders = JBUI.Borders.empty(0, PADDING)

internal class WearHealthServicesToolWindow(private val stateManager: WearHealthServicesToolWindowStateManager) : SimpleToolWindowPanel(
  true, true), Disposable {
  private val uiScope: CoroutineScope = AndroidCoroutineScope(this, uiThread)
  private val workerScope: CoroutineScope = AndroidCoroutineScope(this, workerThread)

  fun setSerialNumber(serialNumber: String) {
    stateManager.serialNumber = serialNumber
  }

  private fun getLogger() = Logger.getInstance(this::class.java)

  private fun createContentPanel(): JPanel {
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
      add(capabilitiesComboBox, BorderLayout.WEST)
      add(JLabel(message("wear.whs.panel.test.data.inactive")), BorderLayout.EAST)
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
              text = message(it.capability.labelKey)
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
          workerScope.launch {
            stateManager.applyChanges()
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
    return JPanel(VerticalFlowLayout()).apply {
      border = horizontalBorders
      add(JPanel(BorderLayout()).apply {
        add(JLabel(message("wear.whs.panel.sensor")).apply {
          font = font.deriveFont(Font.BOLD)
        }, BorderLayout.CENTER)
        add(JPanel(FlowLayout()).apply {
          add(JLabel(message("wear.whs.panel.override")).apply {
            font = font.deriveFont(Font.BOLD)
          })
          add(JLabel(message("wear.whs.panel.unit")).apply {
            font = font.deriveFont(Font.BOLD)
            preferredSize = Dimension(50, preferredSize.height)
          })
        }, BorderLayout.EAST)
      })
      capabilities.forEach { capability ->
        add(JPanel(BorderLayout()).apply {
          preferredSize = Dimension(0, 35)
          val checkBox = JCheckBox(message(capability.labelKey)).also { checkBox ->
            val plainFont = checkBox.font.deriveFont(Font.PLAIN)
            val italicFont = checkBox.font.deriveFont(Font.ITALIC)
            stateManager.getState(capability).map { it.enabled }.onEach { enabled ->
              checkBox.isSelected = enabled
            }.launchIn(uiScope)
            stateManager.getState(capability).map { it.synced }.onEach { synced ->
              if (!synced) {
                checkBox.font = italicFont
                checkBox.text = "${message(capability.labelKey)}*"
              }
              else {
                checkBox.font = plainFont
                checkBox.text = message(capability.labelKey)
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
            add(JTextField().also { textField ->
              textField.document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                  workerScope.launch {
                    try {
                      stateManager.setOverrideValue(capability, textField.text.toFloat())
                    }
                    catch (exception: NumberFormatException) { // TODO(b/309931192): Show a tooltip to the user that the value is not a float
                      getLogger().warn("String is not a float")
                    }
                  }
                }
              })
              stateManager.getState(capability).map { it.overrideValue }.onEach {
                if (!textField.isFocusOwner) {
                  textField.text = it?.toString() ?: ""
                }
              }.launchIn(uiScope)
              textField.preferredSize = Dimension(JBUI.scale(50), JBUI.scale(20))
              textField.isEnabled = checkBox.isSelected
              checkBox.selected.addListener {
                textField.isEnabled = it
              }
              textField.isVisible = capability.isOverrideable
            })
            add(JLabel(message(capability.unitKey)).also { label ->
              label.isVisible = capability.isOverrideable
              label.preferredSize = Dimension(JBUI.scale(50), JBUI.scale(20))
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
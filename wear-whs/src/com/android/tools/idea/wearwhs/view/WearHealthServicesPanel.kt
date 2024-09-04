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
import com.android.tools.idea.wearwhs.WearWhsBundle.message
import com.android.tools.idea.wearwhs.WhsCapability
import com.android.tools.idea.wearwhs.WhsDataValue
import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
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
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

private const val MAX_OVERRIDE_VALUE_LENGTH = 50

// Allows only one leading zero
private val floatPattern = Regex("^(0|0?[1-9]\\d*)?(\\.[0-9]*)?\$")

private const val PADDING = 15
private val horizontalBorders = JBUI.Borders.empty(0, PADDING)
private const val NOTIFICATION_GROUP_ID = "Wear Health Services Notification"
private val TEMPORARY_MESSAGE_DISPLAY_DURATION = 2.seconds

private fun createCenterPanel(
  stateManager: WearHealthServicesStateManager,
  uiScope: CoroutineScope,
  workerScope: CoroutineScope,
  capabilities: List<WhsCapability>,
): JPanel {
  // List of elements that should be disabled if there's an active exercise
  val elementsToDisableDuringExercise = Collections.synchronizedList(mutableListOf<JComponent>())
  // List of elements that should be visible only if there's an active exercise
  val elementsToDisplayDuringExercise = Collections.synchronizedList(mutableListOf<JComponent>())
  stateManager.ongoingExercise
    .onEach {
      elementsToDisableDuringExercise.forEach { element ->
        element.isEnabled = !it
        element.toolTipText = if (it) message("wear.whs.panel.disabled.during.exercise") else null
      }
      elementsToDisplayDuringExercise.forEach { element -> element.isVisible = it }
    }
    .launchIn(uiScope)
  return JPanel(VerticalFlowLayout()).apply {
    border = horizontalBorders
    add(
      JPanel(BorderLayout()).apply {
        add(
          JLabel(message("wear.whs.panel.sensor")).apply { font = font.deriveFont(Font.BOLD) },
          BorderLayout.CENTER,
        )
        add(
          JPanel(FlowLayout()).apply {
            add(
              JLabel(message("wear.whs.panel.override")).apply {
                elementsToDisplayDuringExercise.add(this)
                font = font.deriveFont(Font.BOLD)
              }
            )
          },
          BorderLayout.EAST,
        )
      }
    )
    capabilities.forEach { capability ->
      add(
        JPanel(BorderLayout()).apply {
          preferredSize = Dimension(0, 35)
          val label =
            JLabel(message(capability.label)).also { label ->
              val plainFont = label.font.deriveFont(Font.PLAIN)
              val italicFont = label.font.deriveFont(Font.ITALIC)
              stateManager
                .getState(capability)
                .map { it.synced }
                .onEach { synced ->
                  if (!synced) {
                    label.font = italicFont
                    label.text = "${message(capability.label)}*"
                  } else {
                    label.font = plainFont
                    label.text = message(capability.label)
                  }
                }
                .launchIn(uiScope)
            }
          val checkBox =
            JCheckBox().also { checkBox ->
              elementsToDisableDuringExercise.add(checkBox)
              stateManager
                .getState(capability)
                .map { it.capabilityState.enabled }
                .onEach { enabled -> checkBox.isSelected = enabled }
                .launchIn(uiScope)
              checkBox.addActionListener {
                workerScope.launch {
                  stateManager.setCapabilityEnabled(capability, checkBox.isSelected)
                  stateManager.preset.value = Preset.CUSTOM
                }
              }
            }
          stateManager.ongoingExercise
            .onEach { label.isEnabled = !it || checkBox.isSelected }
            .launchIn(uiScope)

          add(checkBox, BorderLayout.LINE_START)
          add(label, BorderLayout.CENTER)
          add(
            JPanel(FlowLayout()).apply {
              elementsToDisplayDuringExercise.add(this)
              add(
                JTextField().also { textField ->
                  (textField.document as AbstractDocument).documentFilter =
                    object : DocumentFilter() {
                      fun validate(string: String): Boolean {
                        if (
                          !floatPattern.matches(string) || string.length > MAX_OVERRIDE_VALUE_LENGTH
                        ) {
                          return false
                        }
                        workerScope.launch {
                          when (capability.dataType.overrideDataType) {
                            WhsDataValue.IntValue::class.java -> {
                              string.toIntOrNull()?.let {
                                stateManager.setOverrideValue(capability, it)
                              } ?: stateManager.clearOverrideValue(capability)
                            }
                            else -> {
                              string.toFloatOrNull()?.let {
                                stateManager.setOverrideValue(capability, it)
                              } ?: stateManager.clearOverrideValue(capability)
                            }
                          }
                        }
                        return true
                      }

                      override fun insertString(
                        fb: FilterBypass,
                        offset: Int,
                        text: String,
                        attr: AttributeSet?,
                      ) {
                        val newValue =
                          fb.document.getText(0, offset) +
                            text +
                            fb.document.getText(offset, fb.document.length - offset)

                        if (validate(newValue)) {
                          super.insertString(fb, offset, text, attr)
                        } else {
                          Toolkit.getDefaultToolkit().beep()
                        }
                      }

                      override fun replace(
                        fb: FilterBypass,
                        offset: Int,
                        length: Int,
                        text: String,
                        attr: AttributeSet?,
                      ) {
                        val newValue =
                          fb.document.getText(0, offset) +
                            text +
                            fb.document.getText(
                              offset + length,
                              fb.document.length - offset - length,
                            )

                        if (validate(newValue)) {
                          super.replace(fb, offset, length, text, attr)
                        } else {
                          Toolkit.getDefaultToolkit().beep()
                        }
                      }
                    }
                  stateManager
                    .getState(capability)
                    .map { it.capabilityState.overrideValue }
                    .onEach { overrideValue ->
                      val overrideValueAsText = overrideValue.asText().trim()
                      if (!textField.isFocusOwner && textField.text.trim() != overrideValueAsText) {
                        textField.text = overrideValueAsText
                      }
                    }
                    .launchIn(uiScope)
                  textField.preferredSize = JBUI.size(75, 25)
                  textField.isEnabled = checkBox.isSelected
                  checkBox.selected.addListener { textField.isEnabled = it }
                  textField.isVisible = capability.isOverrideable
                }
              )
              add(
                JLabel(message(capability.unit)).also { label ->
                  checkBox.selected.addListener { label.isEnabled = it }
                  label.preferredSize = JBUI.size(75, 25)

                  if (!capability.isOverrideable) {
                    label.icon = AllIcons.General.Note
                    label.toolTipText = message("wear.whs.capability.override.not.supported")
                  }
                }
              )
            },
            BorderLayout.EAST,
          )
        }
      )
    }
  }
}

private fun createWearHealthServicesPanelHeader(
  stateManager: WearHealthServicesStateManager,
  uiScope: CoroutineScope,
  workerScope: CoroutineScope,
  notifyUser: (String, MessageType) -> Unit,
): JPanel = panel {
  row(
    JBLabel(message("wear.whs.panel.title")).apply { foreground = UIUtil.getInactiveTextColor() }
  ) {}
  separator()

  val capabilitiesComboBox =
    ComboBox<Preset>().apply { model = DefaultComboBoxModel(Preset.values()) }
  capabilitiesComboBox.addActionListener {
    stateManager.preset.value = (capabilitiesComboBox.selectedItem as Preset)
  }
  stateManager.preset.onEach { capabilitiesComboBox.selectedItem = it }.launchIn(uiScope)
  stateManager.ongoingExercise
    .onEach {
      capabilitiesComboBox.isEnabled = !it
      capabilitiesComboBox.toolTipText =
        if (it) message("wear.whs.panel.disabled.during.exercise") else null
    }
    .launchIn(uiScope)
  val eventTriggersDropDownButton =
    object :
        CommonDropDownButton(
          CommonAction("", AllIcons.Actions.More).apply {
            addChildrenActions(
              EVENT_TRIGGER_GROUPS.map { eventTriggerGroup ->
                CommonAction(eventTriggerGroup.eventGroupLabel, null).apply {
                  addChildrenActions(
                    eventTriggerGroup.eventTriggers.map { eventTrigger ->
                      CommonAction(eventTrigger.eventLabel, null) {
                        workerScope.launch {
                          stateManager
                            .triggerEvent(eventTrigger)
                            .onSuccess {
                              notifyUser(
                                message("wear.whs.event.trigger.success"),
                                MessageType.INFO,
                              )
                            }
                            .onFailure {
                              notifyUser(
                                message("wear.whs.event.trigger.failure"),
                                MessageType.ERROR,
                              )
                            }
                        }
                      }
                    }
                  )
                }
              }
            )
          }
        ) {
        override fun isFocusable(): Boolean = true
      }
      .apply { toolTipText = message("wear.whs.panel.trigger.events") }

  val statusLabel =
    JLabel(message("wear.whs.panel.exercise.inactive")).apply {
      // setting a minimum width to prevent the label from being cropped when the text
      // changes
      minimumSize = Dimension(140, 0)
      combine(stateManager.ongoingExercise, stateManager.isStateStale) {
          ongoingExercise,
          isStateStale ->
          ongoingExercise to isStateStale
        }
        .onEach { (isActiveExercise, isStateStale) ->
          icon = if (isStateStale) StudioIcons.Common.WARNING else StudioIcons.Common.INFO
          text =
            if (isActiveExercise) message("wear.whs.panel.exercise.active")
            else message("wear.whs.panel.exercise.inactive")
          toolTipText =
            when {
              isStateStale -> message("wear.whs.panel.stale.data")
              isActiveExercise -> message("wear.whs.panel.press.apply.for.overrides")
              else -> message("wear.whs.panel.press.apply.for.toggles")
            }
        }
        .launchIn(uiScope)
    }

  twoColumnsRow(
    {
      cell(capabilitiesComboBox)
      cell(eventTriggersDropDownButton)
    },
    { cell(statusLabel) },
  )
}

/** Container for the Wear Health Services panel. */
data class WearHealthServicesPanel(
  /** The UI component containing the Wear Health Services panel. */
  val component: JComponent,
  /**
   * Flow receiving an element when the user applies changes. The changes might still fail to be
   * applied.
   */
  val onUserApplyChangesFlow: Flow<Unit>,
)

private sealed class PanelInformation(val message: String) {
  class Message(message: String) : PanelInformation(message)

  class TemporaryMessage(
    message: String,
    val duration: Duration = TEMPORARY_MESSAGE_DISPLAY_DURATION,
  ) : PanelInformation(message)

  data object EmptyMessage : PanelInformation("")
}

internal fun createWearHealthServicesPanel(
  stateManager: WearHealthServicesStateManager,
  uiScope: CoroutineScope,
  workerScope: CoroutineScope,
): WearHealthServicesPanel {

  // Display current state e.g. we encountered an error, if there's work in progress, or if an
  // action was successful
  val informationFlow = MutableStateFlow<PanelInformation>(PanelInformation.EmptyMessage)
  val informationLabel = JLabel()

  fun notifyUser(message: String, type: MessageType) {
    uiScope.launch {
      val isPanelShowing = informationLabel.topLevelAncestor != null
      if (isPanelShowing) {
        informationFlow.value = PanelInformation.TemporaryMessage(message)
      } else {
        Notifications.Bus.notify(
          Notification(NOTIFICATION_GROUP_ID, message, type.toNotificationType())
        )
      }
    }
  }

  val content =
    JBScrollPane().apply {
      setViewportView(
        createCenterPanel(stateManager, uiScope, workerScope, stateManager.capabilitiesList)
      )
    }

  val onApplyChangesChannel = Channel<Unit>()
  val footer =
    JPanel(FlowLayout(FlowLayout.TRAILING)).apply {
      border = horizontalBorders

      add(informationLabel)
      add(
        JButton(message("wear.whs.panel.reset")).apply {
          addActionListener {
            workerScope.launch {
              stateManager
                .reset()
                .onSuccess { notifyUser(message("wear.whs.panel.reset.success"), MessageType.INFO) }
                .onFailure {
                  notifyUser(message("wear.whs.panel.reset.failure"), MessageType.ERROR)
                }
            }
          }
        }
      )
      add(
        JButton(message("wear.whs.panel.apply")).apply {
          stateManager.ongoingExercise
            .onEach {
              toolTipText =
                if (it) message("wear.whs.panel.apply.tooltip.during.exercise")
                else message("wear.whs.panel.apply.tooltip.no.exercise")
            }
            .launchIn(uiScope)

          addActionListener {
            isEnabled = false
            workerScope.launch {
              try {
                onApplyChangesChannel.send(Unit)
                stateManager
                  .applyChanges()
                  .onSuccess {
                    notifyUser(message("wear.whs.panel.apply.success"), MessageType.INFO)
                  }
                  .onFailure {
                    notifyUser(message("wear.whs.panel.apply.failure"), MessageType.ERROR)
                  }
              } finally {
                uiScope.launch { isEnabled = true }
              }
            }
          }
        }
      )
    }

  stateManager.status
    .onEach {
      when (it) {
        is WhsStateManagerStatus.Syncing ->
          informationFlow.value =
            PanelInformation.Message(message("wear.whs.panel.capabilities.syncing"))
        is WhsStateManagerStatus.ConnectionLost ->
          informationFlow.value =
            PanelInformation.Message(message("wear.whs.panel.connection.lost"))
        is WhsStateManagerStatus.Idle ->
          if (informationFlow.value.message == message("wear.whs.panel.connection.lost")) {
            // the connection is restored
            informationFlow.value = PanelInformation.EmptyMessage
          }
        else -> {}
      }
    }
    .launchIn(uiScope)

  uiScope.launch {
    informationFlow.collectLatest {
      informationLabel.text = it.message
      if (it is PanelInformation.TemporaryMessage) {
        delay(it.duration)
        informationFlow.value = PanelInformation.EmptyMessage
      }
    }
  }

  return WearHealthServicesPanel(
    component =
      JPanel(BorderLayout()).apply {
        add(
          createWearHealthServicesPanelHeader(stateManager, uiScope, workerScope, ::notifyUser),
          BorderLayout.NORTH,
        )
        add(content, BorderLayout.CENTER)
        add(footer, BorderLayout.SOUTH)

        isFocusCycleRoot = true
        isFocusTraversalPolicyProvider = true
        focusTraversalPolicy = LayoutFocusTraversalPolicy()
      },
    onUserApplyChangesFlow = onApplyChangesChannel.receiveAsFlow(),
  )
}

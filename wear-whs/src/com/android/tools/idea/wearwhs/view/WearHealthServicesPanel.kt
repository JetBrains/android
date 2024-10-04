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

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.wearwhs.EVENT_TRIGGER_GROUPS
import com.android.tools.idea.wearwhs.EventTrigger
import com.android.tools.idea.wearwhs.WearWhsBundle.message
import com.android.tools.idea.wearwhs.WhsCapability
import com.android.tools.idea.wearwhs.WhsDataValue
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val MAX_OVERRIDE_VALUE_LENGTH = 50

// Allows only one leading zero
private val floatPattern = Regex("^(0|0?[1-9]\\d*)?(\\.[0-9]*)?\$")

private const val PADDING = 15
private val horizontalBorders = JBUI.Borders.empty(0, PADDING)

internal const val LEARN_MORE_URL =
  "https://developer.android.com/health-and-fitness/guides/health-services/simulated-data#use_the_health_services_sensor_panel"

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
                isVisible = stateManager.ongoingExercise.value == true
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
              combine(stateManager.getState(capability), stateManager.ongoingExercise) {
                  uiState,
                  ongoingExercise ->
                  if (uiState.hasUserChanges(ongoingExercise)) {
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
              checkBox.addActionListener {
                workerScope.launch {
                  stateManager.setCapabilityEnabled(capability, checkBox.isSelected)
                }
              }
            }

          combine(stateManager.getState(capability), stateManager.ongoingExercise) {
              uiState,
              ongoingExercise ->
              // When an exercise is ongoing, we want to reflect the capability state of the device,
              // not any pending user changes
              val isCapabilityEnabled =
                if (ongoingExercise) uiState.upToDateState.enabled
                else
                  (uiState as? PendingUserChangesCapabilityUIState)?.userState?.enabled
                    ?: uiState.upToDateState.enabled
              checkBox.isSelected = isCapabilityEnabled
              label.isEnabled = !ongoingExercise || isCapabilityEnabled
            }
            .launchIn(uiScope)

          add(checkBox, BorderLayout.LINE_START)
          add(label, BorderLayout.CENTER)
          add(
            JPanel(FlowLayout()).apply {
              isVisible = stateManager.ongoingExercise.value == true
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

                      override fun remove(fb: FilterBypass, offset: Int, length: Int) {
                        val newValue =
                          fb.document.getText(0, offset) +
                            fb.document.getText(
                              offset + length,
                              fb.document.length - offset - length,
                            )

                        if (validate(newValue)) {
                          super.remove(fb, offset, length)
                        } else {
                          Toolkit.getDefaultToolkit().beep()
                        }
                      }
                    }
                  stateManager
                    .getState(capability)
                    .map { it.currentState.overrideValue }
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
  triggerEvent: (EventTrigger) -> Unit,
): JPanel = panel {
  row(
    JBLabel(message("wear.whs.panel.title")).apply { foreground = UIUtil.getInactiveTextColor() }
  ) {
    cell(
        ActionLink(message("wear.whs.panel.learn.more")).apply {
          addActionListener { BrowserUtil.browse(LEARN_MORE_URL) }
          setExternalLinkIcon()
        }
      )
      .align(AlignX.RIGHT)
  }
  separator()

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
      cell(createLoadCapabilityPresetButton(stateManager = stateManager, uiScope = uiScope))
      cell(createTriggerEventGroupsButton(triggerEvent = { triggerEvent(it) }))
    },
    { cell(statusLabel) },
  )
}

/** Container for the Wear Health Services panel. */
data class WearHealthServicesPanel(
  /** The UI component containing the Wear Health Services panel. */
  val component: JComponent
)

internal fun createWearHealthServicesPanel(
  stateManager: WearHealthServicesStateManager,
  uiScope: CoroutineScope,
  workerScope: CoroutineScope,
  informationLabelFlow: Flow<String>,
  reset: () -> Unit,
  applyChanges: () -> Unit,
  triggerEvent: (EventTrigger) -> Unit,
): WearHealthServicesPanel {

  // Display current state e.g. we encountered an error, if there's work in progress, or if an
  // action was successful
  val informationLabel = JLabel()
  uiScope.launch { informationLabelFlow.collectLatest { informationLabel.text = it } }

  val content =
    JBScrollPane().apply {
      setViewportView(
        createCenterPanel(stateManager, uiScope, workerScope, stateManager.capabilitiesList)
      )
    }

  val footer =
    JPanel(FlowLayout(FlowLayout.TRAILING)).apply {
      border = horizontalBorders

      add(informationLabel)
      add(JButton(message("wear.whs.panel.reset")).apply { addActionListener { reset() } })
      add(
        JButton(message("wear.whs.panel.reapply")).apply {
          stateManager.hasUserChanges
            .onEach {
              text = if (it) message("wear.whs.panel.apply") else message("wear.whs.panel.reapply")
            }
            .launchIn(uiScope)
          stateManager.ongoingExercise
            .onEach {
              toolTipText =
                if (it) message("wear.whs.panel.apply.tooltip.during.exercise")
                else message("wear.whs.panel.apply.tooltip.no.exercise")
            }
            .launchIn(uiScope)

          stateManager.status
            .onEach { isEnabled = it !is WhsStateManagerStatus.Syncing }
            .launchIn(uiScope)

          addActionListener { applyChanges() }
        }
      )
    }

  return WearHealthServicesPanel(
    component =
      JPanel(BorderLayout()).apply {
        add(
          createWearHealthServicesPanelHeader(
            stateManager = stateManager,
            uiScope = uiScope,
            triggerEvent = { triggerEvent(it) },
          ),
          BorderLayout.NORTH,
        )
        add(content, BorderLayout.CENTER)
        add(footer, BorderLayout.SOUTH)

        isFocusCycleRoot = true
        isFocusTraversalPolicyProvider = true
        focusTraversalPolicy = LayoutFocusTraversalPolicy()
      }
  )
}

private fun createTriggerEventGroupsButton(triggerEvent: (EventTrigger) -> Unit): JButton {
  val eventTriggerGroupActions =
    EVENT_TRIGGER_GROUPS.map { eventTriggerGroup ->
      val eventTriggerActions =
        eventTriggerGroup.eventTriggers.map { eventTrigger ->
          createEventTriggerAction(eventTrigger = eventTrigger, triggerEvent = { triggerEvent(it) })
        }
      DropDownAction(eventTriggerGroup.eventGroupLabel, null, null).apply {
        addAll(eventTriggerActions)
      }
    }
  val eventTriggerGroups =
    DropDownAction(null, null, null).apply { addAll(eventTriggerGroupActions) }

  return JButton(message("wear.whs.panel.trigger.events")).apply {
    toolTipText = message("wear.whs.panel.trigger.events.tooltip")
    isFocusable = true
    addActionListener {
      val popup =
        ActionManager.getInstance().createActionPopupMenu(ActionPlaces.POPUP, eventTriggerGroups)
      JBPopupMenu.showBelow(this, popup.component)
    }
  }
}

private fun createEventTriggerAction(
  eventTrigger: EventTrigger,
  triggerEvent: (EventTrigger) -> Unit,
) =
  object : AnAction(eventTrigger.eventLabel, null, null) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
      triggerEvent(eventTrigger)
    }
  }

private fun createLoadCapabilityPresetButton(
  stateManager: WearHealthServicesStateManager,
  uiScope: CoroutineScope,
): JButton {
  val presetActionGroup =
    object : ActionGroup(null, true) {
      override fun getChildren(e: AnActionEvent?) =
        Preset.entries
          .map {
            object : AnAction(message(it.labelKey)) {
              override fun getActionUpdateThread() = ActionUpdateThread.BGT

              override fun actionPerformed(e: AnActionEvent) {
                stateManager.loadPreset(it)
              }
            }
          }
          .toTypedArray()
    }

  val loadCapabilityPresetButton =
    JButton(message("wear.whs.panel.load.preset")).apply {
      addActionListener {
        val popup =
          ActionManager.getInstance().createActionPopupMenu(ActionPlaces.POPUP, presetActionGroup)
        JBPopupMenu.showBelow(this, popup.component)
      }
    }

  stateManager.ongoingExercise
    .onEach {
      loadCapabilityPresetButton.isEnabled = !it
      loadCapabilityPresetButton.toolTipText =
        if (it) message("wear.whs.panel.disabled.during.exercise")
        else message("wear.whs.panel.load.preset.tooltip")
    }
    .launchIn(uiScope)
  return loadCapabilityPresetButton
}

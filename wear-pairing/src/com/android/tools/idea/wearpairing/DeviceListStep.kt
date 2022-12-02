/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.wearpairing

import com.android.sdklib.SdkVersionInfo
import com.android.tools.adtui.HtmlLabel
import com.android.tools.adtui.common.AdtUiUtils.allComponents
import com.android.tools.adtui.common.ColoredIconGenerator.generateWhiteIcon
import com.android.tools.adtui.util.HelpTooltipForList
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.wearpairing.AndroidWearPairingBundle.Companion.message
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.google.wireless.android.sdk.stats.WearPairingEvent
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBMenuItem
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.CollectionListModel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.util.containers.FixedHashMap
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.JBUI.Borders.emptyLeft
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import io.ktor.util.reflect.instanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.net.URL
import javax.swing.BoxLayout
import javax.swing.DefaultListSelectionModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.SwingConstants
import javax.swing.SwingUtilities.isRightMouseButton
import javax.swing.event.HyperlinkEvent.EventType.ACTIVATED

internal const val WEAR_DOCS_LINK = "https://developer.android.com/training/wearables/apps/creating#pairing-assistant"

class DeviceListStep(model: WearDevicePairingModel, private val project: Project?, private val wizardAction: WizardAction) :
  ModelWizardStep<WearDevicePairingModel>(model, "") {
  private val listeners = ListenerManager()
  private val phoneListPanel = createDeviceListPanel(
    title = message("wear.assistant.device.list.phone.header"),
    listName = "phoneList",
    emptyTextTitle = message("wear.assistant.device.list.no.phone")
  )
  private val wearListPanel = createDeviceListPanel(
    title = message("wear.assistant.device.list.wear.header"),
    listName = "wearList",
    emptyTextTitle = message("wear.assistant.device.list.no.wear")
  )
  private var preferredFocus: JComponent? = null
  private val canGoForward = BoolValueProperty()

  override fun onWizardStarting(wizard: ModelWizard.Facade) {
    if (model.selectedPhoneDevice.valueOrNull == null) { // Don't update list if a value is pre-selected
      listeners.listenAndFire(model.phoneList) {
        updateList(phoneListPanel, model.phoneList.get())
      }
    }

    if (model.selectedWearDevice.valueOrNull == null) { // Don't update list if a value is pre-selected
      listeners.listenAndFire(model.wearList) {
        updateList(wearListPanel, model.wearList.get())
      }
    }
  }

  override fun onEntering() {
    val eventType = if (model.selectedPhoneDevice.valueOrNull == null && model.selectedWearDevice.valueOrNull == null)
      WearPairingEvent.EventKind.SHOW_ASSISTANT_FULL_SELECTION
    else
      WearPairingEvent.EventKind.SHOW_ASSISTANT_PRE_SELECTION
    WearPairingUsageTracker.log(eventType)
  }

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> {
    return listOf(
      NewConnectionAlertStep(model),
      DevicesConnectionStep(model, project, wizardAction),
    )
  }

  override fun getComponent(): JComponent = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
    border = empty(24)

    val selectedPhone = model.selectedPhoneDevice.valueOrNull
    val selectedWear = model.selectedWearDevice.valueOrNull
    add(JBLabel(UIUtil.ComponentStyle.LARGE).apply {
      font = JBFont.label().biggerOn(5.0f)
      text = message("wear.assistant.device.list.title")
    }, gridConstraint(x = 0, y = 0, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))

    add(HtmlLabel().apply {
      border = empty(24, 0)
      HtmlLabel.setUpAsHtmlLabel(this)
      text = when {
        selectedPhone != null -> message("wear.assistant.device.list.subtitle_one", selectedPhone.displayName, WEAR_DOCS_LINK)
        selectedWear != null -> message("wear.assistant.device.list.subtitle_one", selectedWear.displayName, WEAR_DOCS_LINK)
        else -> message("wear.assistant.device.list.subtitle_two", WEAR_DOCS_LINK)
      }
    }, gridConstraint(x = 0, y = 1, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))

    add(Splitter(false, 0.5f).apply {
      firstComponent = phoneListPanel.takeIf { selectedPhone == null }
      secondComponent = wearListPanel.takeIf { selectedWear == null }
    }, gridConstraint(x = 0, y = 2, weightx = 1.0, weighty = 1.0, fill = GridBagConstraints.BOTH))

    preferredFocus = if (selectedPhone == null) phoneListPanel.list else wearListPanel.list
  }

  override fun getPreferredFocusComponent(): JComponent? = preferredFocus

  override fun onProceeding() {
    if (model.selectedPhoneDevice.valueOrNull == null) {
      model.selectedPhoneDevice.setNullableValue(phoneListPanel.list.selectedValue)
    }
    if (model.selectedWearDevice.valueOrNull == null) {
      model.selectedWearDevice.setNullableValue(wearListPanel.list.selectedValue)
    }
  }

  override fun canGoForward(): ObservableBool = canGoForward

  override fun dispose() = listeners.releaseAll()

  private fun updateGoForward() {
    canGoForward.set(
      (model.selectedPhoneDevice.valueOrNull != null || phoneListPanel.list.selectedValue != null) &&
      (model.selectedWearDevice.valueOrNull != null || wearListPanel.list.selectedValue != null)
    )
  }

  private fun createDeviceListPanel(title: String, listName: String, emptyTextTitle: String): DeviceListPanel {
    val list = createList(listName)
    HelpTooltipForList<PairingDevice>().installOnList(this, list) { listIndex, helpTooltip ->
      val tooltip = list.model.getElementAt(listIndex).getTooltip() ?: return@installOnList false
      helpTooltip.setDescription(tooltip)
      helpTooltip.setBrowserLink(message("wear.assistant.device.list.tooltip.learn.more"), URL(WEAR_DOCS_LINK))
      true
    }

    return DeviceListPanel(title, list, createEmptyListPanel(list, emptyTextTitle))
  }

  private fun createList(listName: String): TooltipList<PairingDevice> {
    return TooltipList<PairingDevice>().apply {
      name = listName
      setCellRenderer { _, value, index, isSelected, _ ->

        JPanel().apply {
          layout = GridBagLayout()

          if (value.isDisabled() && (index == 0 || !model.getElementAt(index - 1).isDisabled())) {
            if (index == 0) {
              add(
                JBLabel("No compatible devices are available to pair.", SwingConstants.CENTER).apply {
                  isOpaque = true
                  foreground = UIUtil.getLabelDisabledForeground()
                  background = UIUtil.getListBackground()
                  border = empty(32, 16)
                },
                GridBagConstraints().apply {
                  gridwidth = GridBagConstraints.REMAINDER
                  fill = GridBagConstraints.HORIZONTAL
                  gridy = 0
                }
              )
            }
            add(
              JBLabel("Unavailable devices").apply {
                isOpaque = true
                background = JBUI.CurrentTheme.NewClassDialog.panelBackground()
                border = empty(4, 16)
              },
              GridBagConstraints().apply {
                gridwidth = GridBagConstraints.REMAINDER
                fill = GridBagConstraints.HORIZONTAL
                gridy = 1
              }
            )
          }

          add(
            JBLabel(getDeviceIcon(value, isSelected)).apply {
              border = emptyLeft(16)
            },
            GridBagConstraints().apply {
              gridx = 0
              gridy = 2
            }
          )
          add(
            JPanel().apply {
              layout = BoxLayout(this, BoxLayout.Y_AXIS)
              border = empty(4, 4, 8, 16)
              isOpaque = false
              add(JBLabel(value.displayName).apply {
                icon = if (!value.isWearDevice && value.hasPlayStore) getIcon(StudioIcons.Avd.DEVICE_PLAY_STORE, isSelected) else null
                foreground = when {
                  isSelected -> UIUtil.getListForeground(true, true)
                  value.isDisabled() -> UIUtil.getLabelDisabledForeground()
                  else -> UIUtil.getLabelForeground()
                }
                horizontalTextPosition = SwingConstants.LEFT
              })
              add(JBLabel(SdkVersionInfo.getAndroidName(value.apiLevel)).apply {
                foreground = when {
                  isSelected -> UIUtil.getListForeground(true, true)
                  value.isDisabled() -> UIUtil.getLabelDisabledForeground()
                  else -> UIUtil.getContextHelpForeground()
                }
                font = JBFont.label().lessOn(2f)
              })
            },
            GridBagConstraints().apply {
              fill = GridBagConstraints.HORIZONTAL
              weightx = 1.0
              gridx = 1
              gridy = 2
            }
          )

          val rightIcon = when {
            StudioFlags.WEAR_OS_VIRTUAL_DEVICE_PAIRING_ASSISTANT_ENABLED.get() -> null
            WearPairingManager.isPaired(value.deviceID) -> StudioIcons.LayoutEditor.Toolbar.INSERT_HORIZ_CHAIN
            value.isDisabled() -> AllIcons.General.ShowInfos
            else -> null
          }
          if (rightIcon != null) {
            add(
              JBLabel(getIcon(rightIcon, isSelected)).apply {
                border = empty(0, 0, 0, 16)
              },
              GridBagConstraints().apply {
                gridx = 2
                gridy = 2
              }
            )
          }

          // For accessibility purposes, pick the first visible label in this cell and use the text from
          // it for the screen reader.
          allComponents(this)
            .filterIsInstance<JLabel>()
            .filter { it.accessibleContext.accessibleName != null }
            .firstOrNull()?.let {
              accessibleContext.accessibleName = it.accessibleContext.accessibleName
              accessibleContext.accessibleDescription =  it.accessibleContext.accessibleDescription
            }

          isOpaque = true
          background = UIUtil.getListBackground(isSelected, isSelected)
        }
      }

      selectionModel = SomeDisabledSelectionModel(this)

      addListSelectionListener {
        if (!it.valueIsAdjusting) {
          updateGoForward()
        }
      }

      if (!StudioFlags.PAIRED_DEVICES_TAB_ENABLED.get()) {
        addRightClickAction()
      }
    }
  }

  private fun updateList(deviceListPanel: DeviceListPanel, originalDeviceList: List<PairingDevice>) {
    val deviceList = originalDeviceList.sortedWith(compareBy { it.isDisabled() }) // Disabled at the bottom
    val uiList: JBList<PairingDevice> = deviceListPanel.list
    if (uiList.model.size == deviceList.size) {
      deviceList.forEachIndexed { index, device ->
        val listDevice = uiList.model.getElementAt(index)
        if (listDevice != device) {
          (uiList.model as CollectionListModel).setElementAt(device, index)
        }
      }
    }
    else {
      uiList.model = CollectionListModel(deviceList)
    }

    if (uiList.selectedValue?.isDisabled() == true) {
      uiList.clearSelection()
    }

    if (uiList.selectedValue == null) {
      val firstAvailable = deviceList.indexOfFirst { !it.isDisabled() }
      if (firstAvailable >= 0) {
        uiList.selectedIndex = firstAvailable
      }
    }

    deviceListPanel.showList()
    updateGoForward()
  }

  private fun getDeviceIcon(device: PairingDevice, isSelected: Boolean): Icon {
    val baseIcon = when {
      device.isWearDevice -> getIcon(StudioIcons.Avd.DEVICE_WEAR, isSelected)
      else -> getIcon(StudioIcons.Avd.DEVICE_PHONE, isSelected)
    }
    return if (device.isOnline()) ExecutionUtil.getLiveIndicator(baseIcon) else baseIcon
  }

  // Cache generated white icons, so we don't keep creating new ones
  private val whiteIconsCache = hashMapOf<Icon, Icon>()
  private fun getIcon(icon: Icon, isSelected: Boolean): Icon = when {
    isSelected -> whiteIconsCache.getOrPut(icon) { generateWhiteIcon(icon) }
    else -> icon
  }

  private fun JBList<PairingDevice>.addRightClickAction() {
    val listener: MouseListener = object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        val row = locationToIndex(e.point)
        if (row >= 0 && isRightMouseButton(e)) {
          val listDevice = model.getElementAt(row)
          val phoneWearPair = WearPairingManager.getPairsForDevice(listDevice.deviceID).firstOrNull()
          if (phoneWearPair != null) {
            val peerDevice = phoneWearPair.getPeerDevice(listDevice.deviceID)
            val item = JBMenuItem(message("wear.assistant.device.list.forget.connection", peerDevice.displayName))
            item.addActionListener {
              val process = Runnable {
                val cloudSyncIsEnabled = runBlocking(context = Dispatchers.IO) {
                  withTimeoutOrNull(5_000) {
                    WearPairingManager.checkCloudSyncIsEnabled(phoneWearPair.phone)
                  }
                }
                if (cloudSyncIsEnabled == true) {
                  ApplicationManager.getApplication().invokeLater({ showCloudSyncDialog(phoneWearPair.phone) }, ModalityState.any())
                }
                AndroidCoroutineScope(this@DeviceListStep).launch(Dispatchers.IO) {
                  WearPairingManager.removeAllPairedDevices(listDevice.deviceID)
                  // Update pairing icon
                  ApplicationManager.getApplication().invokeLater(
                    {
                      phoneListPanel.list.cleanCache()
                      wearListPanel.list.cleanCache()
                      phoneListPanel.showList()
                      wearListPanel.showList()
                    }, ModalityState.any()
                  )
                }
              }
              val progressTitle = message("wear.assistant.device.list.forget.connection", peerDevice.displayName)
              ProgressManager.getInstance().runProcessWithProgressSynchronously(
                process, progressTitle, true, project, this@addRightClickAction
              )
            }
            val menu = JBPopupMenu()
            menu.add(item)
            JBPopupMenu.showByEvent(e, menu)
          }
        }
      }
    }
    addMouseListener(listener)
  }

  private fun showCloudSyncDialog(pairedPhone: PairingDevice) {
    Messages.showIdeaMessageDialog(
      project,
      message("wear.assistant.device.list.cloud.sync.subtitle", pairedPhone.displayName, WEAR_DOCS_LINK),
      message("wear.assistant.device.list.cloud.sync.title"),
      arrayOf(Messages.getOkButton()),
      0,
      Messages.getWarningIcon(),
      null
    )
  }

  private class SomeDisabledSelectionModel(val list: JBList<PairingDevice>) : DefaultListSelectionModel() {
    init {
      selectionMode = ListSelectionModel.SINGLE_SELECTION
    }

    override fun setSelectionInterval(idx0: Int, idx1: Int) {
      // Note from javadoc: in SINGLE_SELECTION selection mode, only the second index is used
      val n = if (idx1 < 0 || idx1 >= list.model.size || list.model.getElementAt(idx1).isDisabled()) -1 else idx1
      super.setSelectionInterval(n, n)
    }
  }

  private fun createEmptyListPanel(list: JBList<PairingDevice>, emptyTextTitle: String): JPanel = JPanel(GridBagLayout()).apply {
    background = list.background
    border = IdeBorderFactory.createBorder(SideBorder.TOP)
    add(JEditorPane().apply {
      name = "${list.name}EmptyText"
      border = empty(0, 16, 0, 16)
      HtmlLabel.setUpAsHtmlLabel(this)
      text = "<div style='text-align:center'>$emptyTextTitle</div>" // Center text horizontally
      addHyperlinkListener {
        if (it.eventType == ACTIVATED) {
          wizardAction.closeAndStartAvd(project)
        }
      }
    }, gridConstraint(x = 0, y = 0, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL))
  }
}

private fun PairingDevice.isDisabled(): Boolean {
  return state == ConnectionState.DISCONNECTED || isEmulator && !isWearDevice && (apiLevel < 30 || !hasPlayStore)
         || isEmulator && isWearDevice && apiLevel < 28
}

private fun PairingDevice.getTooltip(): String? {
  if (!StudioFlags.PAIRED_DEVICES_TAB_ENABLED.get()) {
    WearPairingManager.getPairsForDevice(deviceID).firstOrNull()?.apply {
      return "Paired with ${getPeerDevice(deviceID).displayName}"
    }
  }

  return when {
    isEmulator && isWearDevice && apiLevel < 28 -> message("wear.assistant.device.list.tooltip.requires.api", 28)
    isEmulator && !isWearDevice && apiLevel < 30 -> message("wear.assistant.device.list.tooltip.requires.api", 30)
    isEmulator && !isWearDevice && !hasPlayStore -> message("wear.assistant.device.list.tooltip.requires.play")
    else -> null
  }
}

/**
 * A [JBList] with a special tooltip that can take html links
 */
private class TooltipList<E> : JBList<E>() {
  private data class CellRendererItem<E>(val value: E, val isSelected: Boolean, val cellHasFocus: Boolean)

  // Tooltip manager keeps requesting cell items when the mouse moves (even inside the same item!). Keep the last few in memory.
  private val cellRendererCache = FixedHashMap<CellRendererItem<E>, Component>(8)

  override fun setCellRenderer(cellRenderer: ListCellRenderer<in E>) {
    super.setCellRenderer { list, value, index, isSelected, cellHasFocus ->
      cellRendererCache.getOrPut(CellRendererItem(value, isSelected, cellHasFocus)) {
        cellRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      }
    }
  }

  override fun getToolTipText(event: MouseEvent?): String? = null

  fun cleanCache() {
    cellRendererCache.clear()
  }
}

private class DeviceListPanel(title: String, val list: TooltipList<PairingDevice>, val emptyListPanel: JPanel) : JPanel(BorderLayout()) {
  val scrollPane = ScrollPaneFactory.createScrollPane(list, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER).apply {
    border = IdeBorderFactory.createBorder(SideBorder.TOP)
  }

  init {
    border = IdeBorderFactory.createBorder(SideBorder.ALL)

    add(JBLabel(title).apply {
      font = JBFont.label().asBold()
      border = empty(4, 16)
    }, BorderLayout.NORTH)
    add(scrollPane, BorderLayout.CENTER)
  }

  fun showList() {
    val view = if (list.isEmpty) emptyListPanel else list
    scrollPane.setViewportView(view)
  }
}
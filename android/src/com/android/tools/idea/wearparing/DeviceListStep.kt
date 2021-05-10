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
package com.android.tools.idea.wearparing

import com.android.sdklib.SdkVersionInfo
import com.android.tools.adtui.HtmlLabel
import com.android.tools.adtui.common.ColoredIconGenerator.generateWhiteIcon
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.wearparing.ConnectionState.DISCONNECTED
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.JBMenuItem
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.CollectionListModel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.TooltipWithClickableLinks
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.util.containers.FixedHashMap
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.BoxLayout
import javax.swing.DefaultListSelectionModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.SwingConstants
import javax.swing.SwingUtilities.isRightMouseButton
import javax.swing.event.HyperlinkEvent.EventType.ACTIVATED
import com.intellij.ui.TooltipWithClickableLinks.ForBrowser as TooltipForBrowser

internal const val WEAR_DOCS_LINK = "https://developer.android.com/training/wearables/apps/creating#pairing-assistant"

class DeviceListStep(model: WearDevicePairingModel, val project: Project, val wizardAction: WizardAction) :
  ModelWizardStep<WearDevicePairingModel>(model, "") {
  private val listeners = ListenerManager()
  private val phoneListPanel = createDeviceListPanel(
    title = message("wear.assistant.device.list.phone.header"),
    listName = "phoneList",
    emptyTextTitle = message("wear.assistant.device.list.no.phone")
  )
  private val wearListPanel = createDeviceListPanel(
    title = message("wear.assistant.device.list.phone.header"),
    listName = "wearList",
    emptyTextTitle = message("wear.assistant.device.list.no.wear")
  )
  private val canGoForward = BoolValueProperty()

  override fun onWizardStarting(wizard: ModelWizard.Facade) {
    listeners.listenAndFire(model.phoneList) {
      updateList(phoneListPanel, model.phoneList.get())
    }

    listeners.listenAndFire(model.wearList) {
      updateList(wearListPanel, model.wearList.get())
    }
  }

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> {
    return listOf(
      NewConnectionAlertStep(model, project),
      DevicesConnectionStep(model, project, wizardAction),
    )
  }

  override fun getComponent(): JComponent = JBPanel<JBPanel<*>>(null).apply {
    border = empty(24)
    layout = BoxLayout(this, BoxLayout.Y_AXIS)

    add(JBLabel(message("wear.assistant.device.list.title"), UIUtil.ComponentStyle.LARGE).apply {
      font = JBFont.label().biggerOn(5.0f)
      alignmentX = Component.LEFT_ALIGNMENT
    })

    add(JBLabel(message("wear.assistant.device.list.subtitle")).apply {
      alignmentX = Component.LEFT_ALIGNMENT
      border = empty(24, 0)
    })

    add(Splitter(false, 0.5f).apply {
      alignmentX = Component.LEFT_ALIGNMENT
      firstComponent = phoneListPanel
      secondComponent = wearListPanel
    })
  }

  override fun onProceeding() {
    model.selectedPhoneDevice.setNullableValue(phoneListPanel.list.selectedValue)
    model.selectedWearDevice.setNullableValue(wearListPanel.list.selectedValue)
  }

  override fun canGoForward(): ObservableBool = canGoForward

  override fun dispose() = listeners.releaseAll()

  private fun updateGoForward() {
    canGoForward.set(phoneListPanel.list.selectedValue != null && wearListPanel.list.selectedValue != null)
  }

  private fun createDeviceListPanel(title: String, listName: String, emptyTextTitle: String): DeviceListPanel {
    val list = createList(listName)
    return DeviceListPanel(title, list, createEmptyListPanel(list, emptyTextTitle))
  }

  private fun createList(listName: String): JBList<PairingDevice> {
    return TooltipList<PairingDevice>().apply {
      name = listName
      setCellRenderer { _, value, _, isSelected, _ ->

        JPanel().apply {
          layout = GridBagLayout()
          add(JBLabel(getDeviceIcon(value, isSelected)))
          add(
            JPanel().apply {
              layout = BoxLayout(this, BoxLayout.Y_AXIS)
              border = JBUI.Borders.emptyLeft(8)
              isOpaque = false
              add(JBLabel(value.displayName).apply {
                icon = if (!value.isWearDevice && value.hasPlayStore) getIcon(StudioIcons.Avd.DEVICE_PLAY_STORE, isSelected) else null
                foreground = when {
                  isSelected -> UIUtil.getListForeground(isSelected, isSelected)
                  value.isDisabled() -> UIUtil.getLabelDisabledForeground()
                  else -> UIUtil.getLabelForeground()
                }
                horizontalTextPosition = SwingConstants.LEFT
              })
              add(JBLabel(SdkVersionInfo.getAndroidName(value.apiLevel)).apply {
                foreground = when {
                  isSelected -> UIUtil.getListForeground(isSelected, isSelected)
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
            }
          )
          if (value.isPaired) {
            add(JBLabel(getIcon(StudioIcons.LayoutEditor.Toolbar.INSERT_HORIZ_CHAIN, isSelected)))
          }

          isOpaque = true
          background = UIUtil.getListBackground(isSelected, isSelected)
          toolTipText = value.getTooltip()?.let {
            val learnMore = message("wear.assistant.device.list.tooltip.learn.more")
            """<html>$it<br><a href="$WEAR_DOCS_LINK">$learnMore</a>"""
          }
          border = empty(4, 16)
        }
      }

      selectionModel = SomeDisabledSelectionModel(this)

      addListSelectionListener {
        if (!it.valueIsAdjusting) {
          updateGoForward()
        }
      }

      addRightClickAction()
    }
  }

  private fun updateList(deviceListPanel: DeviceListPanel, deviceList: List<PairingDevice>) {
    val uiList: JBList<PairingDevice> = deviceListPanel.list
    if (uiList.model.size == deviceList.size) {
      deviceList.forEachIndexed { index, device ->
        val listDevice = uiList.model.getElementAt(index)
        if (listDevice != device) {
          (uiList.model as CollectionListModel).setElementAt(device, index)
          if (device.isPaired && device.state != DISCONNECTED && uiList.selectedIndex < 0) {
            uiList.selectedIndex = index
          }
        }
      }
    }
    else {
      uiList.model = CollectionListModel(deviceList)
    }

    if (uiList.selectedValue?.state == DISCONNECTED) {
      uiList.clearSelection()
    }

    deviceListPanel.showList(showEmpty = uiList.isEmpty)
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
          val (pairedPhone, pairedWear) = WearPairingManager.getPairedDevices()
          if (listDevice.isPaired && pairedPhone != null && pairedWear != null) {
            val peerDevice = if (pairedPhone.deviceID == listDevice.deviceID) pairedWear else pairedPhone
            val item = JBMenuItem(message("wear.assistant.device.list.forget.connection", peerDevice.displayName))
            item.addActionListener {
              GlobalScope.launch(Dispatchers.IO) {
                WearPairingManager.removePairedDevices()
              }
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
  return state == DISCONNECTED || isEmulator && !isWearDevice && (apiLevel < 30 || !hasPlayStore)
}

private fun PairingDevice.getTooltip(): String? = when {
  isEmulator && !isWearDevice && apiLevel < 30 -> message("wear.assistant.device.list.tooltip.requires.api")
  isEmulator && !isWearDevice && !hasPlayStore -> message("wear.assistant.device.list.tooltip.requires.play")
  else -> null
}

/**
 * A [JBList] with a special tooltip that can take html links
 */
private class TooltipList<E> : JBList<E>() {
  private data class CellRendererItem<E>(val value: E, val isSelected: Boolean, val cellHasFocus: Boolean)

  // Tooltip manager keeps requesting cell items when the mouse moves (even inside the same item!). Keep the last few in memory.
  private val cellRendererCache = FixedHashMap<CellRendererItem<E>, Component>(8)
  private val tooltipCache = hashMapOf<String, TooltipWithClickableLinks>()

  override fun setCellRenderer(cellRenderer: ListCellRenderer<in E>) {
    super.setCellRenderer { list, value, index, isSelected, cellHasFocus ->
      cellRendererCache.getOrPut(CellRendererItem(value, isSelected, cellHasFocus)) {
        cellRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      }
    }
  }

  override fun getToolTipText(event: MouseEvent?): String? {
    // Do nothing if tooltip already showing (or it will dismiss, making pressing the link very hard)
    val manager = IdeTooltipManager.getInstance().takeIf { !it.hasCurrent() } ?: return null
    val toolTipText = super.getToolTipText(event)
    val tooltip = toolTipText?.let { tooltipCache.getOrPut(toolTipText) { TooltipForBrowser(this, toolTipText) } }

    tooltip?.point = event?.point
    manager.setCustomTooltip(this, tooltip)

    return toolTipText
  }
}

private class DeviceListPanel(title: String, val list: JBList<PairingDevice>, val emptyListPanel: JPanel): JPanel(BorderLayout()) {
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

  fun showList(showEmpty: Boolean) {
    val view = if (showEmpty) emptyListPanel else list
    scrollPane.setViewportView(view)
  }
}
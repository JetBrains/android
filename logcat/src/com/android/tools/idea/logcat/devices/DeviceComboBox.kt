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
package com.android.tools.idea.logcat.devices

import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.devices.DeviceComboBox.DeviceComboItem
import com.android.tools.idea.logcat.devices.DeviceComboBox.DeviceComboItem.DeviceItem
import com.android.tools.idea.logcat.devices.DeviceComboBox.DeviceComboItem.FileItem
import com.android.tools.idea.logcat.devices.DeviceEvent.Added
import com.android.tools.idea.logcat.devices.DeviceEvent.StateChanged
import com.android.tools.idea.logcat.util.LOGGER
import com.intellij.codeInsight.codeVision.ui.popup.layouter.top
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes.ERROR_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import icons.StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
import icons.StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE
import icons.StudioIcons.Logcat.Input.FILTER_HISTORY_DELETE
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.awt.Component
import java.awt.Insets
import java.awt.Rectangle
import java.awt.event.ActionListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.nio.file.Path
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString

private val DELETE_ICON = FILTER_HISTORY_DELETE
@Suppress("UseDPIAwareInsets") private val COMBO_ITEM_INSETS = Insets(2, 8, 2, 4)
private val DELETE_KEY_CODES = arrayOf(KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE)

/**
 * A [ComboBox] for selecting a device.
 *
 * The items are populated by devices as they come online. When a device goes offline, it's not
 * removed from the combo, rather, it's representation changes to reflect its state.
 *
 * An initial device can optionally be provided. This initial device will become the selected item.
 * If no initial device is provided, the first device added will be selected.
 */
internal class DeviceComboBox(
  private val project: Project,
  private val initialItem: DeviceComboItem?,
) : ComboBox<DeviceComboItem>() {
  private val deviceTracker: IDeviceComboBoxDeviceTracker =
    project
      .service<DeviceComboBoxDeviceTrackerFactory>()
      .createDeviceComboBoxDeviceTracker((initialItem as? DeviceItem)?.device)

  private val deviceComboModel: DeviceComboModel
    get() = model as DeviceComboModel

  init {
    AccessibleContextUtil.setName(this, LogcatBundle.message("logcat.device.combo.accessible.name"))
    renderer = DeviceComboBoxRenderer()
    model = DeviceComboModel()
    if (initialItem is FileItem) {
      deviceComboModel.add(initialItem)
      deviceComboModel.selectedItem = initialItem
    }
  }

  override fun updateUI() {
    setUI(DeviceComboBoxUi())
  }

  override fun setSelectedItem(item: Any?) {
    when (item) {
      is FileItem -> {
        if (!item.path.exists()) {
          val itemRemoved =
            handleItemError(
              item,
              LogcatBundle.message("logcat.device.combo.error.message", item.path)
            )
          if (itemRemoved) {
            return
          }
        }
      }
    }
    super.setSelectedItem(item)
  }

  /**
   * Shows a popup reporting a problem with an item.
   *
   * The popup asks the user if they want to remove the item from the list. Returns true if the item
   * was removed.
   */
  fun handleItemError(item: DeviceComboItem, message: String): Boolean {
    val answer =
      MessageDialogBuilder.yesNo(
          LogcatBundle.message("logcat.device.combo.error.title"),
          LogcatBundle.message("logcat.device.combo.error.message", message)
        )
        .ask(project)
    if (answer) {
      deviceComboModel.remove(item)
      selectedItem =
        when {
          selectedItem != selectedItemReminder -> selectedItemReminder
          deviceComboModel.items.count() == 1 -> null
          else -> deviceComboModel.items.first()
        }
    }
    return answer
  }

  fun trackSelected(): Flow<DeviceComboItem> = callbackFlow {
    // If an item is already selected, the listener will not send it, so we send it now
    (selectedItem as? DeviceComboItem)?.let { trySendBlocking(it) }
    val listener = ActionListener { item?.let { trySendBlocking(it) } }
    addActionListener(listener)
    launch {
      deviceTracker.trackDevices().collect {
        LOGGER.debug("trackDevices: $it")
        when (it) {
          is Added -> deviceAdded(it.device)
          is StateChanged -> deviceStateChanged(it.device)
        }
      }
      this@callbackFlow.close()
    }
    awaitClose { removeActionListener(listener) }
  }

  fun getSelectedDevice(): Device? = (item as? DeviceItem)?.device

  fun getSelectedFile(): Path? = (item as? FileItem)?.path

  private fun deviceAdded(device: Device) {
    if (deviceComboModel.containsDevice(device)) {
      deviceStateChanged(device)
    } else {
      val item = deviceComboModel.addDevice(device)
      when {
        selectedItem != null -> return
        initialItem == null -> selectItem(item)
        device.deviceId == (initialItem as? DeviceItem)?.device?.deviceId -> selectItem(item)
      }
    }
  }

  private fun selectItem(item: DeviceItem) {
    selectedItem = item
  }

  private fun deviceStateChanged(device: Device) {
    when (deviceComboModel.containsDevice(device)) {
      true ->
        deviceComboModel.replaceDevice(
          device,
          device.deviceId == (item as? DeviceItem)?.device?.deviceId
        )
      false -> deviceAdded(device) // Device was removed manually so we re-add it
    }
  }

  fun addOrSelectFile(path: Path) {
    val fileItem =
      deviceComboModel.items.find { it is FileItem && it.path.pathString == path.pathString }
        ?: deviceComboModel.addFile(path)
    selectedItem = fileItem
  }

  // Renders a Device.
  //
  // Examples:
  // Online physical device:  "Google Pixel 2 (HT85F1A236612) Android 11, API 30"
  // Offline physical device: "Google Pixel 2 (HT85F1A236612) Android 11, API 30 [OFFLINE]"
  // Online emulator:         "Pixel 4 API 30 (emulator-5554) Android 11, API 30"
  // Offline emulator:        "Pixel 4 API 30 Android 11, API 30 [OFFLINE]"
  //
  // Notes
  //   Physical device name is based on the manufacturer and model while emulator name is based on
  // the AVD name.
  //   Offline emulator does not include the serial number because it is irrelevant while the device
  // offline.
  private class DeviceComboBoxRenderer : ColoredListCellRenderer<DeviceComboItem>() {
    private val component = BorderLayoutPanel()
    private val deleteLabel = JLabel()

    init {
      component.addToRight(deleteLabel)
      component.isOpaque = false
    }

    override fun getListCellRendererComponent(
      list: JList<out DeviceComboItem>,
      value: DeviceComboItem?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean,
    ): Component {
      val deviceComponent =
        super.getListCellRendererComponent(list, value, index, selected, hasFocus)
      component.addToLeft(deviceComponent)
      deleteLabel.icon =
        if (selected && value != null && value.isDeletable())
          IconUtil.colorize(DELETE_ICON, list.selectionForeground)
        else null

      return component
    }

    override fun customizeCellRenderer(
      list: JList<out DeviceComboItem>,
      item: DeviceComboItem?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean
    ) {
      if (item == null) {
        append(LogcatBundle.message("logcat.device.combo.no.connected.devices"), ERROR_ATTRIBUTES)
        return
      }
      when (item) {
        is DeviceItem -> renderDevice(item.device)
        is FileItem -> renderFile(item.path, (list.model as DeviceComboModel).items)
      }
    }

    private fun renderDevice(device: Device) {
      icon = if (device.isEmulator) VIRTUAL_DEVICE_PHONE else PHYSICAL_DEVICE_PHONE

      append(device.name, REGULAR_ATTRIBUTES)
      if (device.isOnline) {
        append(" (${device.serialNumber})", REGULAR_ATTRIBUTES)
      }
      append(
        LogcatBundle.message("logcat.device.combo.version", device.release, device.sdk.toString()),
        GRAY_ATTRIBUTES
      )
      if (!device.isOnline) {
        append(LogcatBundle.message("logcat.device.combo.offline"), GRAYED_BOLD_ATTRIBUTES)
      }
    }

    private fun renderFile(path: Path, items: List<DeviceComboItem>) {
      icon = AllIcons.FileTypes.Text
      val sameName = items.filterIsInstance<FileItem>().count { it.path.name == path.name }
      val name = if (sameName > 1) path.pathString else path.name
      append(name)
    }
  }

  private class DeviceComboModel : CollectionComboBoxModel<DeviceComboItem>() {

    fun addDevice(device: Device): DeviceItem {
      return DeviceItem(device).also { add(it) }
    }

    fun addFile(path: Path): FileItem {
      return FileItem(path).also { add(it) }
    }

    fun replaceDevice(device: Device, setSelected: Boolean) {
      val index = items.indexOfFirst { it is DeviceItem && it.device.deviceId == device.deviceId }
      if (index < 0) {
        LOGGER.warn("Device ${device.deviceId} expected to exist but was not found")
        return
      }
      val item = DeviceItem(device)
      setElementAt(item, index)
      if (setSelected) {
        selectedItem = item
      }
    }

    fun containsDevice(device: Device): Boolean =
      items.find { it is DeviceItem && it.device.deviceId == device.deviceId } != null
  }

  sealed class DeviceComboItem {
    data class DeviceItem(val device: Device) : DeviceComboItem()

    data class FileItem(val path: Path) : DeviceComboItem()
  }

  /**
   * A custom UI based on DarculaComboBoxUI that has more control over the popup, so we can
   * intercept mouse events.
   */
  private class DeviceComboBoxUi : DarculaComboBoxUI() {
    override fun installDefaults() {
      super.installDefaults()
      if (padding == null) {
        // In tests, this isn't being set properly.
        padding = JBUI.insets(1, 6)
      }
    }

    override fun createPopup() = DeviceComboBoxPopup(comboBox)

    private class DeviceComboBoxPopup(comboBox: JComboBox<Any>) : CustomComboPopup(comboBox) {
      override fun installListListeners() {
        val mouseListener = createListMouseListener()
        val mouseMotionListener = createListMouseMotionListener()

        val handler =
          DeviceComboBoxPopupMouseListener(comboBox, list, mouseListener, mouseMotionListener)
        comboBox.addKeyListener(handler)

        listMouseListener = handler
        list.addMouseListener(listMouseListener)

        listMouseMotionListener = handler
        list.addMouseMotionListener(listMouseMotionListener)

        listSelectionListener = createListSelectionListener()
        list.addListSelectionListener(listSelectionListener)
      }

      override fun customizeListRendererComponent(component: JComponent) {
        // The default right border doesn't look right with the delete icon.
        component.border = JBUI.Borders.empty(COMBO_ITEM_INSETS)
      }
    }

    override fun selectNextPossibleValue() {
      val index = listBox.selectedIndex
      if (index < comboBox.model.size - 1) {
        setSelectedIndex(index + 1)
      }
    }

    override fun selectPreviousPossibleValue() {
      val index = listBox.selectedIndex
      if (index > 0) {
        setSelectedIndex(index - 1)
      }
    }

    private fun setSelectedIndex(index: Int) {
      listBox.selectedIndex = index
      listBox.ensureIndexIsVisible(index)
      comboBox.repaint()
    }
  }

  /** A mouse & keyboard listener that handles item deletion. */
  private class DeviceComboBoxPopupMouseListener(
    private val comboBox: JComboBox<in DeviceComboItem>,
    private val list: JList<Any>,
    private val mouseListener: MouseListener,
    private val mouseMotionListener: MouseMotionListener,
  ) : KeyAdapter(), MouseListener by mouseListener, MouseMotionListener by mouseMotionListener {

    override fun keyReleased(e: KeyEvent) {
      if (e.keyCode !in DELETE_KEY_CODES) {
        return
      }
      val item = list.selectedValue as DeviceComboItem? ?: return
      if (!item.isDeletable()) {
        return
      }
      deleteSelectedItem()
    }

    override fun mouseReleased(e: MouseEvent) {
      if (e.button == MouseEvent.BUTTON1 && e.modifiersEx == 0) {
        if (e.isOverDeleteIcon()) {
          deleteSelectedItem()
          return
        }
      }
      mouseListener.mouseReleased(e)
    }

    override fun mouseMoved(e: MouseEvent) {
      val hintColor = String.format("%06x", NamedColorUtil.getInactiveTextColor().rgb and 0xffffff)
      list.toolTipText =
        if (e.isOverDeleteIcon())
          LogcatBundle.message("logcat.device.combo.delete.tooltip", hintColor)
        else null
      mouseMotionListener.mouseMoved(e)
    }

    private fun MouseEvent.isOverDeleteIcon(): Boolean {
      val item = list.selectedValue as DeviceComboItem? ?: return false
      if (!item.isDeletable()) {
        return false
      }
      val index = list.selectedIndex

      // Calculate the bounds of the delete click target:
      //   The height of the target is the full height of the cell.
      //   The width of the target is the width of the icon plus the cell right-padding applied to
      // the left & right of the icon.
      val cellBounds = list.getCellBounds(index, index)
      val cellPadding = JBUI.scale(COMBO_ITEM_INSETS.right)
      val x = cellBounds.width - (2 * cellPadding) - DELETE_ICON.iconWidth
      val iconBounds = Rectangle(x, cellBounds.top, cellBounds.width - x, cellBounds.height)

      return iconBounds.contains(point)
    }

    private fun deleteSelectedItem() {
      val index = list.selectedIndex
      if (index < 0) {
        return
      }
      val deviceComboModel = comboBox.model as DeviceComboModel
      deviceComboModel.remove(index)
      deviceComboModel.selectedItem =
        when {
          deviceComboModel.items.isEmpty() -> null
          index == 0 -> deviceComboModel.items.first()
          else -> deviceComboModel.items[index - 1]
        }
    }
  }
}

private fun DeviceComboItem.isDeletable() = !(this is DeviceItem && device.isOnline)

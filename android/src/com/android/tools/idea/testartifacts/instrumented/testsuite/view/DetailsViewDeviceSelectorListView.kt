/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.getRoundedDuration
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.getName
import com.google.common.annotations.VisibleForTesting
import com.google.common.html.HtmlEscapers
import com.intellij.largeFilesEditor.GuiUtils
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColorUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.util.Locale
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Shows a list of devices. This view is intended to be used in Android test suite details page
 * in conjunction with [DetailsViewContentView].
 * A user can click on a device item to look up detailed test results for a selected device.
 */
class DetailsViewDeviceSelectorListView(listener: DetailsViewDeviceSelectorListViewListener) {
  /**
   * Interface to listen events occurred in [DetailsViewDeviceSelectorListView].
   */
  interface DetailsViewDeviceSelectorListViewListener {
    /**
     * Called when a user selects a device for looking up test results for the device.
     */
    @UiThread
    fun onDeviceSelected(selectedDevice: AndroidDevice)

    /**
     * Called when a user selects a special item, "Raw Output", in the list.
     */
    @UiThread
    fun onRawOutputSelected()
  }

  private val myDeviceListModel: DefaultListModel<Any> = DefaultListModel()
  private val myCellRenderer: AndroidDeviceListCellRenderer = AndroidDeviceListCellRenderer()
  @VisibleForTesting object RawOutputItem

  @get:VisibleForTesting
  val deviceList: JBList<Any> = JBList(myDeviceListModel).apply {
    selectionMode = ListSelectionModel.SINGLE_INTERVAL_SELECTION
    putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
    cellRenderer = myCellRenderer
    fixedCellHeight = 50
    selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    border = JBUI.Borders.empty()
    addListSelectionListener {
      val selectedItem = selectedValue
      if (selectedItem is AndroidDevice) {
        listener.onDeviceSelected(selectedItem)
      }
      else if (selectedItem is RawOutputItem) {
        listener.onRawOutputSelected()
      }
    }
  }

  val rootPanel: JPanel = panel {
    row { scrollCell(deviceList).align(Align.FILL) }.resizableRow()
  }

  /**
   * Adds a given device to the list.
   */
  @UiThread
  fun addDevice(device: AndroidDevice) {
    myDeviceListModel.addElement(device)
  }

  /**
   * Select a given device in the list.
   * @param device a device to be selected
   */
  @UiThread
  fun selectDevice(device: AndroidDevice?) {
    deviceList.setSelectedValue(device, true)
  }

  /**
   * Select the raw output item in the list.
   */
  @UiThread
  fun selectRawOutputItem() {
    setShowRawOutputItem(true)
    deviceList.setSelectedValue(RawOutputItem, true)
  }

  /**
   * Updates the view with a given AndroidTestResults.
   */
  @UiThread
  fun setAndroidTestResults(results: AndroidTestResults) {
    myCellRenderer.setAndroidTestResults(results)
  }

  @UiThread
  fun setShowRawOutputItem(showRawOutputItem: Boolean) {
    if (showRawOutputItem) {
      if (myDeviceListModel.indexOf(RawOutputItem) == -1) {
        myDeviceListModel.add(0, RawOutputItem)
      }
    }
    else {
      myDeviceListModel.removeElement(RawOutputItem)
    }
  }

  private class AndroidDeviceListCellRenderer : DefaultListCellRenderer() {
    private val myEmptyBorder = JBUI.Borders.empty(5, 10)
    private val myCellRendererComponent = JPanel(BorderLayout())
    private val myDeviceLabelPanel = JPanel()
    private val myDeviceLabel = JLabel()
    private val myTestResultLabel = JLabel()
    private var myTestResults: AndroidTestResults? = null

    init {
      myDeviceLabelPanel.layout = BoxLayout(myDeviceLabelPanel, BoxLayout.X_AXIS)
      maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
      myDeviceLabelPanel.add(this)
      myDeviceLabelPanel.add(myDeviceLabel)
      myCellRendererComponent.add(myDeviceLabelPanel, BorderLayout.WEST)
      myCellRendererComponent.add(myTestResultLabel, BorderLayout.EAST)
      GuiUtils.setStandardLineBorderToPanel(myCellRendererComponent, 0, 0, 1, 0)
    }

    fun setAndroidTestResults(results: AndroidTestResults) {
      myTestResults = results
    }

    override fun getListCellRendererComponent(list: JList<*>, value: Any,
                                              index: Int, isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      super.getListCellRendererComponent(list, " ", index, isSelected, cellHasFocus)
      background = UIUtil.getTableBackground(isSelected, list.hasFocus())
      myCellRendererComponent.background = list.background
      myCellRendererComponent.foreground = list.foreground
      myDeviceLabelPanel.background = list.background
      myDeviceLabelPanel.foreground = list.foreground
      if (value is AndroidDevice) {
        val testDurationText = myTestResults?.getRoundedDuration(value)?.let {
          StringUtil.formatDuration(it.toMillis(), "\u2009")
        } ?: ""
        if (StringUtil.isNotEmpty(testDurationText)) {
          myDeviceLabel.text = String.format(Locale.US,
                                             "<html>%s<br><font color='#%s'>API %d - %s</font></html>",
                                             value.getName().htmlEscape(),
                                             ColorUtil.toHex(SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor),
                                             value.version.apiLevel,
                                             testDurationText.htmlEscape())
        }
        else {
          myDeviceLabel.text = String.format(Locale.US,
                                             "<html>%s<br><font color='#%s'>API %d</font></html>",
                                             value.getName().htmlEscape(),
                                             ColorUtil.toHex(SimpleTextAttributes.GRAYED_ATTRIBUTES.fgColor),
                                             value.version.apiLevel)
        }
        myDeviceLabel.icon = getIconForDeviceType(value.deviceType)
      }
      else if (value is RawOutputItem) {
        myDeviceLabel.text = "Raw Output"
        myDeviceLabel.icon = null
      }
      myDeviceLabel.iconTextGap = 10
      myDeviceLabel.border = myEmptyBorder
      myDeviceLabel.background = list.background
      myDeviceLabel.foreground = list.foreground
      myDeviceLabel.font = list.font
      if (value is AndroidDevice) {
        myTestResultLabel.icon  = myTestResults?.getTestCaseResult(value)?.let { getIconFor(it) }
      }
      else if (value is RawOutputItem) {
        myTestResultLabel.icon = null
      }
      myTestResultLabel.border = myEmptyBorder
      myTestResultLabel.background = list.background
      myTestResultLabel.foreground = list.foreground
      myTestResultLabel.font = list.font
      return myCellRendererComponent
    }
  }
}

private fun getIconForDeviceType(deviceType: AndroidDeviceType): Icon? {
  return when (deviceType) {
    AndroidDeviceType.LOCAL_EMULATOR, AndroidDeviceType.LOCAL_GRADLE_MANAGED_EMULATOR -> StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE
    AndroidDeviceType.LOCAL_PHYSICAL_DEVICE -> StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
  }
}

private fun String.htmlEscape(): String = HtmlEscapers.htmlEscaper().escape(this)

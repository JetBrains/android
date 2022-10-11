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

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.getName
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import icons.StudioIcons
import org.jetbrains.kotlin.utils.alwaysTrue
import java.util.TreeSet
import javax.swing.Icon
import javax.swing.JComponent

private const val ALL_DEVICES: String = "All devices"

/**
 * A drop-down list to allow users to select an Android API level or a device.
 */
class DeviceAndApiLevelFilterComboBoxAction : ComboBoxAction(), DumbAware {
  private val myAvailableApiLevels: TreeSet<AndroidVersion> = sortedSetOf()
  private val myAvailableDevices: MutableSet<AndroidDevice> = mutableSetOf()
  private var myText: String = ALL_DEVICES
  private var myIcon: Icon? = StudioIcons.DeviceExplorer.MULTIPLE_DEVICES
  private var myFilter: ((AndroidDevice) -> Boolean) = alwaysTrue()
  val filter: ((AndroidDevice) -> Boolean)
    get() = { myFilter(it) }
  var listener: DeviceAndApiLevelFilterComboBoxActionListener? = null

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.text = myText
    e.presentation.icon = myIcon
    e.presentation.isVisible = (myAvailableDevices.size > 1)
  }

  override fun createPopupActionGroup(button: JComponent, context: DataContext): DefaultActionGroup = createActionGroup()

  fun createActionGroup(): DefaultActionGroup {
    val actionGroup = DefaultActionGroup()

    actionGroup.add(createFilterAction(ALL_DEVICES, StudioIcons.DeviceExplorer.MULTIPLE_DEVICES, alwaysTrue()))
    actionGroup.addSeparator()

    val apiLevelGroup = DefaultActionGroup("API level", true)
    apiLevelGroup.addAll(myAvailableApiLevels.map { version ->
      createFilterAction("API ${version.apiString}", null) { it.version.apiString == version.apiString }
    })
    actionGroup.add(apiLevelGroup)
    actionGroup.addSeparator()

    // AndroidDevice.getName() may return different value depends on the timing
    // you call because it resolves device name lazily.
    actionGroup.addAll(myAvailableDevices.map { Pair(it.getName(), it) }.toSortedSet(compareBy { it.first }).map { (name, device) ->
      val icon = when(device.deviceType) {
        AndroidDeviceType.LOCAL_EMULATOR, AndroidDeviceType.LOCAL_GRADLE_MANAGED_EMULATOR -> StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE
        AndroidDeviceType.LOCAL_PHYSICAL_DEVICE -> StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
      }
      createFilterAction(name, icon) { it.id == device.id || it.getName() == device.getName() }
    })

    return actionGroup
  }

  private fun createFilterAction(text: String, icon: Icon?, filter: (AndroidDevice) -> Boolean): DumbAwareAction {
    return DumbAwareAction.create(text) {
      myText = text
      myIcon = icon
      myFilter = filter
      listener?.onFilterUpdated()
    }.apply {
      templatePresentation.icon = icon
    }
  }

  fun addDevice(device: AndroidDevice) {
    myAvailableApiLevels.add(device.version)
    myAvailableDevices.add(device)
  }
}

/**
 * An interface to observe an update of a [DeviceAndApiLevelFilterComboBoxAction] state.
 */
interface DeviceAndApiLevelFilterComboBoxActionListener {
  /**
   * Invoked when a [DeviceAndApiLevelFilterComboBoxAction.filter] gets updated.
   */
  fun onFilterUpdated()
}
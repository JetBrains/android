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
package com.android.tools.idea.deviceprovisioner

import com.android.adblib.DeviceState
import com.android.adblib.deviceInfo
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.getFullReleaseName
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JList
import javax.swing.ListModel

/** Utilities for rendering a [DeviceHandle] to a [ColoredTextContainer]. */
object DeviceHandleRenderer {
  /** Renders the given [DeviceHandle] to a [ColoredTextContainer]. */
  @JvmStatic
  fun renderDevice(
    component: ColoredTextContainer,
    device: DeviceHandle,
  ) {
    renderDevice(component, device, device.state.properties.title, false)
  }

  /**
   * Renders the given [DeviceHandle] to a [ColoredTextContainer], attempting to disambiguate it if
   * it has the same label as another device in the given list.
   */
  @JvmStatic
  fun renderDevice(
    component: ColoredTextContainer,
    device: DeviceHandle,
    allDevices: Iterable<DeviceHandle>
  ) {
    val name = device.state.properties.title
    val isDuplicated = allDevices.any { it != device && it.state.properties.title == name }

    renderDevice(component, device, name, isDuplicated)
  }

  private fun renderDevice(
    component: ColoredTextContainer,
    device: DeviceHandle,
    name: String,
    isDuplicated: Boolean
  ) {
    component.append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)

    when (val deviceState = device.state.connectedDevice?.deviceInfo?.deviceState) {
      null ->
        component.append(
          " [${device.state.javaClass.simpleName}]",
          SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES
        )
      DeviceState.ONLINE -> {}
      else ->
        component.append(
          " [${titleCase(deviceState.toString())}]",
          SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES
        )
    }

    if (isDuplicated) {
      device.state.properties.disambiguator?.let {
        component.append(" [$it]", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
      }
    }

    // E.g. "Android 5.1 (Lollipop)"
    device.state.properties.androidVersion?.let {
      component.append(
        " ${it.getFullReleaseName(includeApiLevel = false, includeCodeName = true)}",
        SimpleTextAttributes.GRAY_ATTRIBUTES
      )
    }
  }
}

/** Renderer for device names in lists and combo boxes. */
class DeviceHandleListCellRenderer : ColoredListCellRenderer<DeviceHandle>() {
  override fun customizeCellRenderer(
    list: JList<out DeviceHandle>,
    value: DeviceHandle?,
    index: Int,
    selected: Boolean,
    hasFocus: Boolean
  ) {
    value ?: return

    DeviceHandleRenderer.renderDevice(this, value, list.model.toIterable())
  }
}

fun <T> ListModel<T>.toIterable(): Iterable<T> =
  object : Iterable<T> {
    override fun iterator(): Iterator<T> =
      object : Iterator<T> {
        var index = 0
        override fun hasNext(): Boolean = index < size
        override fun next(): T = getElementAt(index++)
      }
  }

fun titleCase(s: String) = if (s.isEmpty()) "" else s[0].titlecase() + s.substring(1).lowercase()

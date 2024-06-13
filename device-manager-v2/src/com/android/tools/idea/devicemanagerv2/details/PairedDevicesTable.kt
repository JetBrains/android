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
package com.android.tools.idea.devicemanagerv2.details

import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.tools.adtui.categorytable.Attribute
import com.android.tools.adtui.categorytable.CategoryTable
import com.android.tools.adtui.categorytable.Column
import com.android.tools.adtui.categorytable.IconLabel
import com.android.tools.adtui.categorytable.LabelColumn
import com.android.tools.idea.devicemanagerv2.DeviceManagerBundle
import com.android.tools.idea.devicemanagerv2.TwoLineLabel
import com.android.tools.idea.devicemanagerv2.titlecase
import com.android.tools.idea.devicemanagerv2.toLabelText
import com.android.tools.idea.wearpairing.WearPairingManager
import com.intellij.util.ui.JBEmptyBorder
import icons.StudioIcons
import javax.swing.Icon
import kotlinx.coroutines.CoroutineDispatcher

/** Immutable data class for the PairingTable. */
internal data class PairedDeviceData(
  val handle: DeviceHandle,
  val displayName: String,
  val icon: Icon?,
  val androidVersion: AndroidVersion?,
  val state: WearPairingManager.PairingState,
) {
  companion object {
    fun create(
      handle: DeviceHandle,
      state: DeviceState,
      pairingState: WearPairingManager.PairingState,
    ) =
      PairedDeviceData(
        handle,
        state.properties.title,
        null, // TODO: use state.properties.icon once it exists
        state.properties.androidVersion,
        pairingState,
      )
  }
}

internal object PairedDevicesTable {
  fun create(dispatcher: CoroutineDispatcher): CategoryTable<PairedDeviceData> =
    CategoryTable(listOf(Type, Name, WearConnectionStatus), { it.handle }, dispatcher)

  object Type : Column<PairedDeviceData, Icon?, IconLabel> {
    override val name = DeviceManagerBundle.message("column.title.type")
    override val columnHeaderName = "" // no room for a name
    override val attribute =
      object : Attribute<PairedDeviceData, Icon?> {
        override val sorter = null

        override fun value(t: PairedDeviceData) = t.icon
      }

    override val widthConstraint =
      Column.SizeConstraint.exactly(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE.iconWidth + 5)

    override fun createUi(rowValue: PairedDeviceData) = IconLabel(null)

    override fun updateValue(rowValue: PairedDeviceData, component: IconLabel, value: Icon?) {
      component.baseIcon = value
    }
  }

  object Name : Column<PairedDeviceData, String, TwoLineLabel> {
    override val name = DeviceManagerBundle.message("column.title.name")
    override val widthConstraint = Column.SizeConstraint(min = 200, preferred = 400)
    override val attribute =
      Attribute.stringAttribute<PairedDeviceData>(isGroupable = false) { it.displayName }

    override fun createUi(rowValue: PairedDeviceData) =
      TwoLineLabel().apply { border = JBEmptyBorder(4) }

    override fun updateValue(rowValue: PairedDeviceData, component: TwoLineLabel, value: String) {
      component.line1Label.text = rowValue.displayName
      component.line2Label.text =
        rowValue.androidVersion?.toLabelText() ?: "Unknown Android version"
    }
  }

  object WearConnectionStatus :
    LabelColumn<PairedDeviceData>(
      "Status",
      Column.SizeConstraint(min = 20, max = 80),
      Attribute.stringAttribute(isGroupable = false) { it.state.toString().titlecase() },
    )
}

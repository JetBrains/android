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
package com.android.tools.idea.devicemanagerv2

import com.android.adblib.deviceInfo
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.devices.Abi
import com.android.tools.adtui.categorytable.Attribute
import com.android.tools.adtui.categorytable.Attribute.Companion.stringAttribute
import com.android.tools.adtui.categorytable.Column
import com.android.tools.adtui.categorytable.LabelColumn
import com.android.tools.idea.wearpairing.WearPairingManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope

/**
 * Immutable snapshot of relevant parts of a [DeviceHandle] or [DeviceTemplate] for use in
 * CategoryTable.
 */
internal data class DeviceRowData(
  /**
   * If this row represents a template, this value is set and handle is null. Otherwise, handle must
   * be set, and this is also set to handle.sourceTemplate (which may be null).
   */
  val template: DeviceTemplate?,
  val handle: DeviceHandle?,
  val name: String,
  val type: DeviceType,
  val androidVersion: AndroidVersion?,
  val abi: Abi?,
  val status: String,
  val isVirtual: Boolean,
) {
  init {
    checkNotNull(handle ?: template) { "Either template or handle must be set" }
  }

  fun key() = handle ?: template!!

  companion object {
    fun create(handle: DeviceHandle): DeviceRowData {
      val state = handle.state
      val properties = state.properties
      return DeviceRowData(
        template = handle.sourceTemplate,
        handle = handle,
        name = properties.title,
        type = properties.deviceType ?: DeviceType.HANDHELD,
        androidVersion = properties.androidVersion,
        abi = properties.abi,
        status = state.connectedDevice?.deviceInfo?.deviceState?.toString()?.titlecase()
            ?: "Disconnected",
        isVirtual = properties.isVirtual ?: false,
      )
    }

    fun create(template: DeviceTemplate): DeviceRowData {
      val properties = template.properties
      return DeviceRowData(
        template = template,
        handle = null,
        name = properties.title,
        type = properties.deviceType ?: DeviceType.HANDHELD,
        androidVersion = properties.androidVersion,
        abi = properties.abi,
        status = "Disconnected",
        isVirtual = properties.isVirtual ?: false,
      )
    }

    private fun String.titlecase() = lowercase().let { it.replaceFirstChar { it.uppercase() } }
  }
}

internal object DeviceTableColumns {

  val nameAttribute = stringAttribute<DeviceRowData>(isGroupable = false) { it.name }

  class Name(private val wearPairingManager: WearPairingManager) :
    Column<DeviceRowData, String, DeviceNamePanel> {
    override val name = DeviceManagerBundle.message("column.title.name")
    override val widthConstraint = Column.SizeConstraint(min = 200, preferred = 400)
    override val attribute = nameAttribute
    override fun createUi(rowValue: DeviceRowData) = DeviceNamePanel(wearPairingManager)

    override fun updateValue(rowValue: DeviceRowData, component: DeviceNamePanel, value: String) =
      component.update(rowValue)
  }

  object TypeAttribute : Attribute<DeviceRowData, DeviceType> {
    override val sorter: Comparator<DeviceType> = compareBy { it.name }

    override fun value(t: DeviceRowData): DeviceType = t.type

    // TODO: CategoryRow uses DeviceType.toString() which renders the category
    //  in uppercase; we need a way to make this titlecase.
  }

  /** Renders the type of device as an icon. */
  object Type : Column<DeviceRowData, DeviceType, JBLabel> {
    override val name = DeviceManagerBundle.message("column.title.formfactor")

    override val attribute = TypeAttribute

    override fun createUi(rowValue: DeviceRowData): JBLabel = JBLabel()

    override fun updateValue(rowValue: DeviceRowData, component: JBLabel, value: DeviceType) {
      // While we use isVirtual to tweak the icon, this column groups based on device type only.
      component.icon =
        if (rowValue.isVirtual) {
          when (value) {
            DeviceType.HANDHELD -> StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE
            DeviceType.WEAR -> StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR
            DeviceType.TV -> StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV
            DeviceType.AUTOMOTIVE -> StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_CAR
          }
        } else {
          when (value) {
            DeviceType.HANDHELD -> StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
            DeviceType.WEAR -> StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_WEAR
            DeviceType.TV -> StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_TV
            DeviceType.AUTOMOTIVE -> StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_CAR
          }
        }
    }

    // All icons should be the same size
    override val widthConstraint =
      Column.SizeConstraint.exactly(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE.iconWidth + 5)
  }

  object Api :
    LabelColumn<DeviceRowData>(
      DeviceManagerBundle.message("column.title.api"),
      Column.SizeConstraint(min = 20, max = 65),
      stringAttribute { it.androidVersion?.apiStringWithExtension ?: "" }
    )

  object IsVirtual :
    LabelColumn<DeviceRowData>(
      DeviceManagerBundle.message("column.title.isvirtual"),
      Column.SizeConstraint(min = 20, max = 80),
      stringAttribute {
        DeviceManagerBundle.message(
          if (it.isVirtual) "column.value.virtual" else "column.value.physical"
        )
      }
    )

  object Status :
    LabelColumn<DeviceRowData>(
      DeviceManagerBundle.message("column.title.status"),
      Column.SizeConstraint(min = 20, max = 100),
      stringAttribute { it.status }
    )

  class Actions(private val project: Project, val coroutineScope: CoroutineScope) :
    Column<DeviceRowData, Unit, ActionButtonsPanel> {
    override val name = DeviceManagerBundle.message("column.title.actions")
    override val attribute = Attribute.Unit

    override fun updateValue(rowValue: DeviceRowData, component: ActionButtonsPanel, value: Unit) {
      component.updateState(rowValue)
    }

    override fun createUi(rowValue: DeviceRowData): ActionButtonsPanel {
      return when (rowValue.handle) {
        null -> DeviceTemplateButtonsPanel(coroutineScope, rowValue.template!!)
        else -> DeviceHandleButtonsPanel(project, rowValue.handle)
      }
    }

    // TODO: Precomputing this is a hack... can we base it on the panel after it has been
    // constructed?
    override val widthConstraint =
      Column.SizeConstraint.exactly((StudioIcons.Avd.RUN.iconWidth + 7) * 3)
  }

  fun columns(project: Project, coroutineScope: CoroutineScope) =
    listOf(
      Type,
      Name(WearPairingManager.getInstance()),
      Api,
      IsVirtual,
      Status,
      Actions(project, coroutineScope)
    )
}

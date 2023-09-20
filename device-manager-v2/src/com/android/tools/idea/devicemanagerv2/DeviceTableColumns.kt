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

import com.android.sdklib.deviceprovisioner.DeviceError
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.categorytable.Attribute
import com.android.tools.adtui.categorytable.Attribute.Companion.stringAttribute
import com.android.tools.adtui.categorytable.ColorableAnimatedSpinnerIcon
import com.android.tools.adtui.categorytable.Column
import com.android.tools.adtui.categorytable.IconLabel
import com.android.tools.adtui.categorytable.LabelColumn
import com.android.tools.adtui.event.DelegateMouseEventHandler
import com.google.common.collect.Ordering
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope

internal object DeviceTableColumns {

  val nameAttribute = stringAttribute<DeviceRowData>(isGroupable = false) { it.name }

  object Name : Column<DeviceRowData, String, DeviceNamePanel> {
    override val name = DeviceManagerBundle.message("column.title.name")
    override val widthConstraint = Column.SizeConstraint(min = 200, preferred = 400)
    override val attribute = nameAttribute

    override fun createUi(rowValue: DeviceRowData) = DeviceNamePanel()

    override fun updateValue(rowValue: DeviceRowData, component: DeviceNamePanel, value: String) =
      component.update(rowValue)
  }

  object FormFactor : Attribute<DeviceRowData, DeviceType> {
    override val sorter: Comparator<DeviceType> = compareBy { it.name }

    override fun value(t: DeviceRowData): DeviceType = t.type
  }

  object Api :
    LabelColumn<DeviceRowData>(
      DeviceManagerBundle.message("column.title.api"),
      Column.SizeConstraint(min = 20, max = 65),
      stringAttribute { it.androidVersion?.apiStringWithExtension ?: "" }
    )

  object HandleType :
    LabelColumn<DeviceRowData>(
      DeviceManagerBundle.message("column.title.handletype"),
      Column.SizeConstraint(min = 20, max = 80),
      stringAttribute { it.handleType.toString() }
    )

  object Status : Column<DeviceRowData, DeviceRowData.Status, IconLabel> {
    override val name = "Status"
    override val columnHeaderName = "" // no room for a name
    override val visibleWhenGrouped = true
    override val attribute =
      object : Attribute<DeviceRowData, DeviceRowData.Status> {
        override val sorter: Comparator<DeviceRowData.Status> =
          Ordering.explicit(DeviceRowData.Status.ONLINE, DeviceRowData.Status.OFFLINE)

        override fun value(t: DeviceRowData) = t.status
      }

    override val widthConstraint = Column.SizeConstraint.exactly(JBUI.scale(24))

    override fun updateValue(
      rowValue: DeviceRowData,
      component: IconLabel,
      value: DeviceRowData.Status
    ) {
      component.baseIcon =
        when {
          rowValue.handle?.state?.isTransitioning == true -> ColorableAnimatedSpinnerIcon()
          rowValue.error != null ->
            when (rowValue.error.severity) {
              DeviceError.Severity.INFO -> StudioIcons.Common.INFO
              DeviceError.Severity.WARNING -> StudioIcons.Common.WARNING
              DeviceError.Severity.ERROR -> StudioIcons.Common.ERROR
            }
          else ->
            when (value) {
              DeviceRowData.Status.OFFLINE -> null
              DeviceRowData.Status.ONLINE -> StudioIcons.Avd.STATUS_DECORATOR_ONLINE
            }
        }
    }

    override fun createUi(rowValue: DeviceRowData) =
      IconLabel(null).apply { size = JBDimension(24, 24) }
  }

  class Actions(private val project: Project?, val coroutineScope: CoroutineScope) :
    Column<DeviceRowData, Unit, ActionButtonsPanel> {
    override val name = DeviceManagerBundle.message("column.title.actions")
    override val columnHeaderName = "" // no room for a name
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

    override fun installMouseDelegate(
      component: ActionButtonsPanel,
      mouseDelegate: DelegateMouseEventHandler
    ) {
      // Install the mouse handler on each child of the panel
      component.components.forEach { mouseDelegate.installListenerOn(it) }
    }

    // TODO: Precomputing this is a hack... can we base it on the panel after it has been
    // constructed?
    override val widthConstraint =
      Column.SizeConstraint.exactly((StudioIcons.Avd.RUN.iconWidth + 7) * 2)
  }

  fun columns(project: Project?, coroutineScope: CoroutineScope) =
    listOf(Status, Name, Api, HandleType, Actions(project, coroutineScope))
}

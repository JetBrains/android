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
package com.android.tools.idea.compose.preview.pickers.properties

import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DENSITY
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DEVICE
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DIM_UNIT
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_HEIGHT
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_ORIENTATION
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_WIDTH
import com.android.tools.idea.compose.preview.pickers.properties.editingsupport.IntegerNormalValidator
import com.android.tools.idea.compose.preview.pickers.properties.editingsupport.IntegerStrictValidator
import com.android.tools.idea.compose.preview.pickers.properties.utils.findByIdOrName
import com.android.tools.idea.compose.preview.pickers.properties.utils.getDefaultPreviewDevice
import com.android.tools.idea.compose.preview.pickers.properties.utils.toDeviceConfig
import com.android.tools.idea.compose.preview.pickers.tracking.PickerTrackableValue
import com.android.tools.idea.compose.preview.pickers.tracking.PickerTrackerHelper
import com.android.tools.idea.compose.preview.util.enumValueOfOrNull
import com.android.tools.idea.configurations.ConfigurationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

/**
 * A [PsiPropertyItem] for the Device parameter. Contains internal properties used to modify the device hardware, those are not actual
 * parameters on file and are only kept on memory.
 */
internal class DeviceParameterPropertyItem(
  project: Project,
  model: PsiCallPropertyModel,
  resolvedCall: ResolvedCall<*>,
  descriptor: ValueParameterDescriptor,
  argumentExpression: KtExpression?,
  defaultValue: String?
) : PsiCallParameterPropertyItem(
  project,
  model,
  resolvedCall,
  descriptor,
  argumentExpression,
  defaultValue) {
  private val log = Logger.getInstance(this.javaClass)

  private val defaultDeviceValues: DeviceConfig =
    ConfigurationManager.findExistingInstance(model.module)?.getDefaultPreviewDevice()?.toDeviceConfig() ?: DeviceConfig(
      shape = DEFAULT_SHAPE,
      width = DEFAULT_WIDTH,
      height = DEFAULT_HEIGHT,
      dimUnit = DEFAULT_UNIT,
      dpi = DEFAULT_DENSITY.dpiValue
    )

  override var name: String = PARAMETER_HARDWARE_DEVICE

  val innerProperties = listOf<MemoryParameterPropertyItem>(
    DevicePropertyItem(
      name = PARAMETER_HARDWARE_WIDTH,
      defaultValue = defaultDeviceValues.width.toString(),
      inputValidation = IntegerNormalValidator,
      getter = { it.width.toString() }) { config, newValue ->
      newValue.toIntOrNull()?.let {
        config.width = it
      }
      PickerTrackableValue.UNSUPPORTED_OR_OPEN_ENDED
    },
    DevicePropertyItem(
      name = PARAMETER_HARDWARE_HEIGHT,
      defaultValue = defaultDeviceValues.height.toString(),
      inputValidation = IntegerNormalValidator,
      getter = { it.height.toString() }) { config, newValue ->
      newValue.toIntOrNull()?.let {
        config.height = it
      }
      PickerTrackableValue.UNSUPPORTED_OR_OPEN_ENDED
    },
    DevicePropertyItem(
      name = PARAMETER_HARDWARE_DIM_UNIT,
      defaultValue = defaultDeviceValues.dimUnit.name,
      getter = { it.dimUnit.name }) { config, newValue ->
      val newUnit = enumValueOfOrNull<DimUnit>(newValue)
      newUnit?.let {
        config.dimUnit = newUnit
        newUnit.trackableValue
      } ?: PickerTrackableValue.UNKNOWN
    },
    DevicePropertyItem(
      name = PARAMETER_HARDWARE_DENSITY,
      defaultValue = defaultDeviceValues.dpi.toString(),
      inputValidation = IntegerStrictValidator,
      getter = { it.dpi.toString() }) { config, newValue ->
      val newDpi = newValue.toIntOrNull()
      newDpi?.let {
        config.dpi = newDpi
        PickerTrackerHelper.densityBucketOfDeviceConfig(config)
      } ?: PickerTrackableValue.UNKNOWN
    },
    DevicePropertyItem(
      name = PARAMETER_HARDWARE_ORIENTATION,
      defaultValue = defaultDeviceValues.orientation.name,
      getter = { it.orientation.name }) { config, newValue ->
      val newOrientation = enumValueOfOrNull<Orientation>(newValue)
      newOrientation?.let {
        config.orientation = newOrientation
        newOrientation.trackableValue
      } ?: PickerTrackableValue.UNKNOWN
    },
  )

  private fun getCurrentDeviceConfig(): MutableDeviceConfig {
    val availableDevices = AvailableDevicesKey.getData(model) ?: emptyList()
    return value?.let { currentValue ->
      // Translate the current value, the value could either be a DeviceConfig string or a Device ID
      DeviceConfig.toDeviceConfigOrNull(currentValue) ?: availableDevices.findByIdOrName(currentValue, log)?.toDeviceConfig()
    }?.toMutableConfig() ?: defaultDeviceValues.toMutableConfig()
  }

  /**
   * PropertyItem for internal device parameters, so that they all read and write to one single source.
   */
  private inner class DevicePropertyItem(
    name: String,
    defaultValue: String?,
    inputValidation: EditingValidation = { EDITOR_NO_ERROR },
    private val getter: (MutableDeviceConfig) -> String,
    private val setter: (MutableDeviceConfig, String) -> PickerTrackableValue
  ) : MemoryParameterPropertyItem(
    name, defaultValue, inputValidation
  ) {
    override var value: String?
      get() = getter(getCurrentDeviceConfig())
      set(newValue) {
        newValue?.let {
          val deviceConfig = getCurrentDeviceConfig()
          val trackableValue = setter(deviceConfig, newValue)
          writeNewValue(deviceConfig.deviceSpec(), false, trackableValue)
        }
      }
  }
}

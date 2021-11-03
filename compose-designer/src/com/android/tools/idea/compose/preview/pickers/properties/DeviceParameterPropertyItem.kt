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

import com.android.sdklib.devices.DeviceManager
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
import com.android.tools.idea.compose.preview.pickers.properties.utils.findOrParseFromDefinition
import com.android.tools.idea.compose.preview.pickers.properties.utils.toDeviceConfig
import com.android.tools.idea.compose.preview.util.enumValueOfOrNull
import com.android.tools.idea.configurations.ConfigurationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.sdk.AndroidSdkData
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

  //TODO: Do this elsewhere, this is not the only place where it's done
  private val availableDevices = run {
    AndroidFacet.getInstance(model.module)?.let { facet ->
      AndroidSdkData.getSdkData(facet)?.deviceManager?.getDevices(DeviceManager.ALL_DEVICES)?.toList()
    } ?: emptyList()
  }

  private val defaultDeviceValues: DeviceValues =
    ConfigurationManager.findExistingInstance(model.module)?.defaultDevice?.toDeviceConfig()?.toImmutableValues() ?: DeviceValues(
      shape = DEFAULT_SHAPE,
      width = DEFAULT_WIDTH,
      height = DEFAULT_HEIGHT,
      unit = DEFAULT_UNIT,
      density = DEFAULT_DENSITY.dpiValue
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
    },
    DevicePropertyItem(
      name = PARAMETER_HARDWARE_HEIGHT,
      defaultValue = defaultDeviceValues.height.toString(),
      inputValidation = IntegerNormalValidator,
      getter = { it.height.toString() }) { config, newValue ->
      newValue.toIntOrNull()?.let {
        config.height = it
      }
    },
    DevicePropertyItem(
      name = PARAMETER_HARDWARE_DIM_UNIT,
      defaultValue = defaultDeviceValues.unit.name,
      getter = { it.dimensionUnit.name }) { config, newValue ->
      enumValueOfOrNull<DimUnit>(newValue)?.let {
        config.dimensionUnit = it
      }
    },
    DevicePropertyItem(
      name = PARAMETER_HARDWARE_DENSITY,
      defaultValue = defaultDeviceValues.density.toString(),
      inputValidation = IntegerStrictValidator,
      getter = { it.density.toString() }) { config, newValue ->
      newValue.toIntOrNull()?.let {
        config.density = it
      }
    },
    DevicePropertyItem(
      name = PARAMETER_HARDWARE_ORIENTATION,
      defaultValue = defaultDeviceValues.orientation.name,
      getter = { it.orientation.name }) { config, newValue ->
      enumValueOfOrNull<Orientation>(newValue)?.let {
        config.orientation = it
      }
    },
  )

  private fun getCurrentDeviceConfig(): DeviceConfig {
    val defaultConfig = defaultDeviceValues.toMutableConfig()
    return value?.let { currentValue ->
      DeviceConfig.toDeviceConfigOrNull(currentValue) ?: availableDevices.findByIdOrName(currentValue, log)?.toDeviceConfig()
    } ?: defaultConfig
  }

  /**
   * PropertyItem for internal device parameters, so that they all read and write to one single source.
   */
  private inner class DevicePropertyItem(
    name: String,
    defaultValue: String?,
    inputValidation: EditingValidation = { EDITOR_NO_ERROR },
    private val getter: (DeviceConfig) -> String,
    private val setter: (DeviceConfig, String) -> Unit
  ) : MemoryParameterPropertyItem(
    name, defaultValue, inputValidation
  ) {
    override var value: String?
      get() = getter(getCurrentDeviceConfig())
      set(newValue) {
        newValue?.let {
          val deviceConfig = getCurrentDeviceConfig()
          setter(deviceConfig, newValue)
          writeNewValue(deviceConfig.deviceSpec(), false)
        }
      }
  }
}

private fun DeviceConfig.toImmutableValues(): DeviceValues =
  DeviceValues(
    shape = this.shape,
    width = this.width,
    height = this.height,
    unit = this.dimensionUnit,
    density = this.density
  )

private fun DeviceValues.toMutableConfig(): DeviceConfig =
  DeviceConfig(
    shape = this.shape,
    width = this.width,
    height = this.height,
    dimUnit = this.unit,
    density = this.density
  )

/**
 * Immutable equivalent of [DeviceConfig]
 */
private data class DeviceValues(
  val shape: Shape,
  val width: Int,
  val height: Int,
  val unit: DimUnit,
  val density: Int
) {
  val orientation = if (height >= width) Orientation.portrait else Orientation.landscape
}


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

import com.android.resources.ScreenRound
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DENSITY
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DEVICE
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DIM_UNIT
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_HEIGHT
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_ORIENTATION
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_WIDTH
import com.android.tools.idea.compose.preview.pickers.properties.editingsupport.IntegerStrictValidator
import com.android.tools.idea.compose.preview.util.enumValueOfOrDefault
import com.android.tools.idea.configurations.ConfigurationManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

private const val WIDTH_INITIAL = "width"
private const val HEIGHT_INITIAL = "height"

private val ORIENTATION_DEFAULT = Orientation.portrait.name

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

  private val initialState: DeviceValues = run {
    val initialValue = value
    if (initialValue == null || initialValue.isBlank()) {
      resolvedCall.call.callElement.module?.let { module ->
        val config = DeviceConfig().apply { dimensionUnit = DimUnit.px }
        ConfigurationManager.findExistingInstance(module)?.defaultDevice?.defaultState?.let { deviceState ->
          val screen = deviceState.hardware.screen
          config.width = screen.xDimension
          config.height = screen.yDimension
          config.density = screen.pixelDensity.dpiValue
          if (screen.screenRound == ScreenRound.ROUND) {
            config.shape = if (screen.chin != 0) Shape.Square else Shape.Round
          }
          else {
            config.shape = Shape.Normal
          }
          return@run DeviceValues(
            shape = config.shape,
            width = config.width,
            height = config.height,
            unit = config.dimensionUnit,
            density = config.density
          )
        }
      }
    }
    return@run DeviceValues(
      shape = DEFAULT_SHAPE,
      width = DEFAULT_WIDTH,
      height = DEFAULT_HEIGHT,
      unit = DEFAULT_UNIT,
      density = DEFAULT_DENSITY.dpiValue
    )
  }

  override var name: String = PARAMETER_HARDWARE_DEVICE

  // TODO(b/197021783): Have a better/correct wat to choose default values. That matches the device used in Preview when 'Device = null'
  private val width = DeviceConfigProperty(
    getter = { config -> config.width.toString() },
    setter = { config, newValue -> config.width = newValue.toIntOrNull() ?: initialState.width },
    default = initialState.width.toString()
  )

  private val height = DeviceConfigProperty(
    getter = { config -> config.height.toString() },
    setter = { config, newValue -> config.height = newValue.toIntOrNull() ?: initialState.height },
    default = initialState.height.toString()
  )

  private val dimensionUnit = DeviceConfigProperty(
    getter = { config -> config.dimensionUnit.name },
    setter = { config, newValue -> config.dimensionUnit = enumValueOfOrDefault(newValue, initialState.unit) },
    default = initialState.unit.name
  )

  private val density = DeviceConfigProperty(
    getter = { config -> config.density.toString() },
    setter = { config, newValue -> config.density = newValue.toIntOrNull() ?: initialState.density },
    default = initialState.density.toString()
  )

  private val orientation = DeviceConfigProperty(
    getter = { config -> config.orientation.name },
    setter = { config, newValue -> config.orientation = enumValueOfOrDefault(newValue, initialState.orientation) },
    default = initialState.orientation.name
  )

  val innerProperties = listOf<PsiPropertyItem>(
    DeviceMemoryPropertyItem(PARAMETER_HARDWARE_WIDTH, WIDTH_INITIAL, width, IntegerStrictValidator),
    DeviceMemoryPropertyItem(PARAMETER_HARDWARE_HEIGHT, HEIGHT_INITIAL, height, IntegerStrictValidator),
    DeviceMemoryPropertyItem(PARAMETER_HARDWARE_DENSITY, DEFAULT_DENSITY.dpiValue.toString(), density),
    DeviceMemoryPropertyItem(PARAMETER_HARDWARE_ORIENTATION, ORIENTATION_DEFAULT, orientation),
    DeviceMemoryPropertyItem(PARAMETER_HARDWARE_DIM_UNIT, DEFAULT_UNIT.name, dimensionUnit)
  )

  /**
   * Variable wrapper that reads and writes values to the actual property of [DeviceParameterPropertyItem].
   */
  private inner class DeviceConfigProperty(
    private val getter: (DeviceConfig) -> String,
    private val setter: (DeviceConfig, String) -> Unit,
    private val default: String
  ) {
    fun getValue(): String = getter(readCurrentConfig())

    fun setValue(newValue: String?) {
      val config = readCurrentConfig()
      setter(config, newValue ?: default)
      writeNewValue(config.deviceSpec(), false)
    }

    /**
     * Creates a [DeviceConfig] based on the current [value].
     */
    private fun readCurrentConfig(): DeviceConfig {
      val currentValue = value
      if (currentValue == null || currentValue.isBlank()) {
        return DeviceConfig(
          shape = initialState.shape,
          width = initialState.width,
          height = initialState.height,
          dimUnit = initialState.unit,
          density = initialState.density
        )
      }
      return DeviceConfig.toDeviceConfigOrDefault(currentValue)
    }
  }

  /**
   * [PsiPropertyItem] for the internal parameters of [DeviceParameterPropertyItem].
   *
   * The [value] references one of the properties of [DeviceParameterPropertyItem].
   */
  private class DeviceMemoryPropertyItem(
    name: String,
    initialValue: String?,
    private val property: DeviceParameterPropertyItem.DeviceConfigProperty,
    inputValidation: EditingValidation = { EDITOR_NO_ERROR }
  ) : MemoryParameterPropertyItem(
    name,
    initialValue
  ) {
    override var value: String?
      get() = property.getValue()
      set(newValue) {
        if (newValue != property.getValue()) {
          property.setValue(newValue)
        }
      }

    override val editingSupport: EditingSupport = object : EditingSupport {
      override val validation: EditingValidation = inputValidation
    }
  }
}

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


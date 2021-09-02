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

import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DEVICE
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DIM_UNIT
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_DENSITY
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_HEIGHT
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_ORIENTATION
import com.android.tools.idea.compose.preview.PARAMETER_HARDWARE_WIDTH
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
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
  initialValue: String?
) : PsiCallParameterPropertyItem(
  project,
  model,
  resolvedCall,
  descriptor,
  argumentExpression,
  initialValue) {
  override var name: String = PARAMETER_HARDWARE_DEVICE

  // TODO(b/197021783): Have a better/correct wat to choose default values. That matches the device used in Preview when 'Device = null'
  private val width = DeviceConfigProperty(
    getter = { config -> config.width.toString() },
    setter = { config, newValue -> config.width = newValue.toIntOrNull() ?: DEFAULT_WIDTH },
    default = DEFAULT_WIDTH.toString()
  )

  private val height = DeviceConfigProperty(
    getter = { config -> config.height.toString() },
    setter = { config, newValue -> config.height = newValue.toIntOrNull() ?: DEFAULT_HEIGHT },
    default = DEFAULT_HEIGHT.toString()
  )

  private val dimensionUnit = DeviceConfigProperty(
    getter = { config -> config.dimensionUnit.name },
    setter = { config, newValue -> config.dimensionUnit = DimUnit.valueOfOrPx(newValue) },
    default = DEFAULT_UNIT.name
  )

  private val density = DeviceConfigProperty(
    getter = { config -> config.density.toString() },
    setter = { config, newValue -> config.density = newValue.toIntOrNull() ?: DEFAULT_DENSITY.dpiValue },
    default = DEFAULT_DENSITY.dpiValue.toString()
  )

  private val orientation = DeviceConfigProperty(
    getter = { config -> config.orientation.name },
    setter = { config, newValue -> config.orientation = Orientation.valueOfOrPortrait(newValue) },
    default = Orientation.portrait.name
  )

  val innerProperties = listOf<PsiPropertyItem>(
    DeviceMemoryPropertyItem(PARAMETER_HARDWARE_WIDTH, WIDTH_INITIAL, width),
    DeviceMemoryPropertyItem(PARAMETER_HARDWARE_HEIGHT, HEIGHT_INITIAL, height),
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
    fun getValue(): String {
      return getter(DeviceConfig.toDeviceConfigOrDefault(value))
    }

    fun setValue(newValue: String?) {
      val config = DeviceConfig.toDeviceConfigOrDefault(value).also { setter(it, newValue ?: default) }
      writeNewValue(config.deviceSpec(), false)
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
    private val property: DeviceParameterPropertyItem.DeviceConfigProperty
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
  }
}


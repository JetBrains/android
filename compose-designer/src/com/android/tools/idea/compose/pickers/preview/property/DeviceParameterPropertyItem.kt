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
package com.android.tools.idea.compose.pickers.preview.property

import com.android.ide.common.util.enumValueOfOrNull
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.adtui.model.stdui.EditingValidation
import com.android.tools.environment.Logger
import com.android.tools.idea.compose.pickers.base.model.PsiCallPropertiesModel
import com.android.tools.idea.compose.pickers.base.property.MemoryParameterPropertyItem
import com.android.tools.idea.compose.pickers.base.property.PsiCallParameterPropertyItem
import com.android.tools.idea.compose.pickers.common.editingsupport.BooleanValidator
import com.android.tools.idea.compose.pickers.common.editingsupport.IntegerStrictValidator
import com.android.tools.idea.compose.pickers.preview.editingsupport.DeviceSpecDimValidator
import com.android.tools.idea.compose.pickers.preview.tracking.PickerTrackerHelper
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.preview.util.AvailableDevicesKey
import com.android.tools.preview.config.Cutout
import com.android.tools.preview.config.DeviceConfig
import com.android.tools.preview.config.DimUnit
import com.android.tools.preview.config.MutableDeviceConfig
import com.android.tools.preview.config.Navigation
import com.android.tools.preview.config.Orientation
import com.android.tools.preview.config.PARAMETER_HARDWARE_CHIN_SIZE
import com.android.tools.preview.config.PARAMETER_HARDWARE_CUTOUT
import com.android.tools.preview.config.PARAMETER_HARDWARE_DENSITY
import com.android.tools.preview.config.PARAMETER_HARDWARE_DEVICE
import com.android.tools.preview.config.PARAMETER_HARDWARE_DIM_UNIT
import com.android.tools.preview.config.PARAMETER_HARDWARE_HEIGHT
import com.android.tools.preview.config.PARAMETER_HARDWARE_IS_ROUND
import com.android.tools.preview.config.PARAMETER_HARDWARE_NAVIGATION
import com.android.tools.preview.config.PARAMETER_HARDWARE_ORIENTATION
import com.android.tools.preview.config.PARAMETER_HARDWARE_WIDTH
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_CUTOUT
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_DPI
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_HEIGHT_DP
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_NAVIGATION
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_SHAPE
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_UNIT
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_WIDTH_DP
import com.android.tools.preview.config.Shape
import com.android.tools.preview.config.findByIdOrName
import com.android.tools.preview.config.getDefaultPreviewDevice
import com.android.tools.preview.config.toDeviceConfig
import com.android.tools.preview.config.toMutableConfig
import com.google.wireless.android.sdk.stats.EditorPickerEvent.EditorPickerAction.PreviewPickerModification.PreviewPickerValue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * A [PsiCallParameterPropertyItem] for the Device parameter. Contains internal properties used to
 * modify the device hardware, those are not actual parameters on file and are only kept on memory.
 */
internal class DeviceParameterPropertyItem(
  project: Project,
  model: PsiCallPropertiesModel,
  addNewArgumentToResolvedCall: (KtValueArgument, KtPsiFactory) -> KtValueArgument?,
  parameterName: Name,
  parameterTypeNameIfStandard: Name?,
  argumentExpression: KtExpression?,
  defaultValue: String?,
) :
  PsiCallParameterPropertyItem(
    project,
    model,
    addNewArgumentToResolvedCall,
    parameterName,
    parameterTypeNameIfStandard,
    argumentExpression,
    defaultValue,
  ) {
  private val log = Logger.getInstance(this.javaClass)

  private val defaultDeviceValues: DeviceConfig =
    ConfigurationManager.findExistingInstance(model.module)
      ?.getDefaultPreviewDevice()
      ?.toDeviceConfig()
      ?: DeviceConfig(
        shape = DEFAULT_SHAPE,
        width = DEFAULT_WIDTH_DP.toFloat(),
        height = DEFAULT_HEIGHT_DP.toFloat(),
        dimUnit = DEFAULT_UNIT,
        dpi = DEFAULT_DPI,
        cutout = DEFAULT_CUTOUT,
        navigation = DEFAULT_NAVIGATION,
      )

  override var name: String = PARAMETER_HARDWARE_DEVICE

  val innerProperties =
    listOf<MemoryParameterPropertyItem>(
      DevicePropertyItem(
        name = PARAMETER_HARDWARE_WIDTH,
        defaultValue = defaultDeviceValues.widthString,
        inputValidation = DeviceSpecDimValidator(strictPositive = true),
        getter = { it.widthString },
      ) { config, newValue ->
        newValue.toFloatOrNull()?.let { config.width = it }
        PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED
      },
      DevicePropertyItem(
        name = PARAMETER_HARDWARE_HEIGHT,
        defaultValue = defaultDeviceValues.heightString,
        inputValidation = DeviceSpecDimValidator(strictPositive = true),
        getter = { it.heightString },
      ) { config, newValue ->
        newValue.toFloatOrNull()?.let { config.height = it }
        PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED
      },
      DevicePropertyItem(
        name = PARAMETER_HARDWARE_DIM_UNIT,
        defaultValue = defaultDeviceValues.dimUnit.name,
        getter = { it.dimUnit.name },
      ) { config, newValue ->
        val newUnit = enumValueOfOrNull<DimUnit>(newValue)
        newUnit?.let {
          config.dimUnit = newUnit
          newUnit.trackableValue
        } ?: PreviewPickerValue.UNKNOWN_PREVIEW_PICKER_VALUE
      },
      DevicePropertyItem(
        name = PARAMETER_HARDWARE_DENSITY,
        defaultValue = defaultDeviceValues.dpi.toString(),
        inputValidation = IntegerStrictValidator,
        getter = { it.dpi.toString() },
      ) { config, newValue ->
        val newDpi = newValue.toIntOrNull()
        newDpi?.let {
          config.dpi = newDpi
          PickerTrackerHelper.densityBucketOfDeviceConfig(config)
        } ?: PreviewPickerValue.UNKNOWN_PREVIEW_PICKER_VALUE
      },
      DevicePropertyItem(
        name = PARAMETER_HARDWARE_ORIENTATION,
        defaultValue = defaultDeviceValues.orientation.name,
        getter = { it.orientation.name },
      ) { config, newValue ->
        val newOrientation = enumValueOfOrNull<Orientation>(newValue)
        newOrientation?.let {
          config.orientation = newOrientation
          newOrientation.trackableValue
        } ?: PreviewPickerValue.UNKNOWN_PREVIEW_PICKER_VALUE
      },
      DevicePropertyItem(
        name = PARAMETER_HARDWARE_IS_ROUND,
        defaultValue = defaultDeviceValues.isRound.toString(),
        inputValidation = BooleanValidator,
        getter = { it.isRound.toString() },
      ) { config, newValue ->
        val newIsRound = newValue.toBooleanStrictOrNull()
        newIsRound?.let {
          config.shape = if (it) Shape.Round else Shape.Normal
          if (it) PreviewPickerValue.SHAPE_ROUND else PreviewPickerValue.SHAPE_NORMAL
        } ?: PreviewPickerValue.UNKNOWN_PREVIEW_PICKER_VALUE
      },
      DevicePropertyItem(
        name = PARAMETER_HARDWARE_CHIN_SIZE,
        defaultValue = defaultDeviceValues.chinSizeString,
        inputValidation = DeviceSpecDimValidator(strictPositive = false),
        getter = { it.chinSizeString },
      ) { config, newValue ->
        val newChinSize = newValue.toFloatOrNull()
        newChinSize?.let {
          if (it > 0) {
            config.shape = Shape.Round
          }
          config.chinSize = newChinSize
          PreviewPickerValue.UNSUPPORTED_OR_OPEN_ENDED
        } ?: PreviewPickerValue.UNKNOWN_PREVIEW_PICKER_VALUE
      },
      DevicePropertyItem(
        name = PARAMETER_HARDWARE_CUTOUT,
        defaultValue = defaultDeviceValues.cutout.name,
        getter = { it.cutout.name },
      ) { config, newValue ->
        val newCutout = enumValueOfOrNull<Cutout>(newValue)
        newCutout?.let {
          config.cutout = newCutout
          newCutout.trackableValue
        } ?: PreviewPickerValue.UNKNOWN_PREVIEW_PICKER_VALUE
      },
      DevicePropertyItem(
        name = PARAMETER_HARDWARE_NAVIGATION,
        defaultValue = defaultDeviceValues.navigation.name,
        getter = { it.navigation.name },
      ) { config, newValue ->
        val newNavigation = enumValueOfOrNull<Navigation>(newValue)
        newNavigation?.let {
          config.navigation = newNavigation
          newNavigation.trackableValue
        } ?: PreviewPickerValue.UNKNOWN_PREVIEW_PICKER_VALUE
      },
    )

  private var lastValueToDevice: Pair<String, DeviceConfig>? = null

  private fun getCurrentDeviceConfig(): MutableDeviceConfig {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val currentValue = value ?: return defaultDeviceValues.toMutableConfig()
    val availableDevices = AvailableDevicesKey.getData(model) ?: emptyList()

    val lastValue = lastValueToDevice
    if (lastValue != null && currentValue == lastValue.first) {
      // No need to parse or find Device for repeated calls.
      return lastValue.second.toMutableConfig()
    }

    // Translate the current value, the value could either be a DeviceConfig string or a Device ID
    val resolvedDeviceConfig =
      DeviceConfig.toDeviceConfigOrNull(currentValue, availableDevices)
        ?: availableDevices.findByIdOrName(currentValue, log)?.toDeviceConfig()
        ?: defaultDeviceValues

    lastValueToDevice = Pair(currentValue, resolvedDeviceConfig)
    return resolvedDeviceConfig.toMutableConfig()
  }

  /**
   * PropertyItem for internal device parameters, so that they all read and write to one single
   * source.
   */
  private inner class DevicePropertyItem(
    name: String,
    defaultValue: String?,
    inputValidation: EditingValidation = { EDITOR_NO_ERROR },
    private val getter: (MutableDeviceConfig) -> String,
    private val setter: (MutableDeviceConfig, String) -> PreviewPickerValue,
  ) : MemoryParameterPropertyItem(name, defaultValue, inputValidation) {
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

  private val DimUnit.trackableValue: PreviewPickerValue
    get() =
      when (this) {
        DimUnit.dp -> PreviewPickerValue.UNIT_DP
        DimUnit.px -> PreviewPickerValue.UNIT_PIXELS
      }

  private val Orientation.trackableValue: PreviewPickerValue
    get() =
      when (this) {
        Orientation.portrait -> PreviewPickerValue.ORIENTATION_PORTRAIT
        Orientation.landscape -> PreviewPickerValue.ORIENTATION_LANDSCAPE
      }

  private val Cutout.trackableValue: PreviewPickerValue
    get() =
      when (this) {
        Cutout.none -> PreviewPickerValue.CUTOUT_NONE
        Cutout.corner -> PreviewPickerValue.CUTOUT_CORNER
        Cutout.double -> PreviewPickerValue.CUTOUT_DOUBLE
        Cutout.punch_hole -> PreviewPickerValue.CUTOUT_HOLE
        Cutout.tall -> PreviewPickerValue.CUTOUT_TALL
      }

  private val Navigation.trackableValue: PreviewPickerValue
    get() =
      when (this) {
        Navigation.buttons -> PreviewPickerValue.NAVIGATION_BUTTONS
        Navigation.gesture -> PreviewPickerValue.NAVIGATION_GESTURE
      }
}

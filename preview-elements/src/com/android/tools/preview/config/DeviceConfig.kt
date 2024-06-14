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
@file:Suppress("EnumEntryName")

package com.android.tools.preview.config

import com.android.ide.common.util.enumValueOfOrNull
import com.android.resources.Density
import com.android.sdklib.devices.Device
import com.android.tools.configurations.DEVICE_CLASS_DESKTOP_ID
import com.android.tools.configurations.DEVICE_CLASS_FOLDABLE_ID
import com.android.tools.configurations.DEVICE_CLASS_PHONE_ID
import com.android.tools.configurations.DEVICE_CLASS_TABLET_ID
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_CHIN_SIZE_ZERO
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_DPI
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_HEIGHT_DP
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_IS_ROUND
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_ORIENTATION
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_SHAPE
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_UNIT
import com.android.tools.preview.config.Preview.DeviceSpec.DEFAULT_WIDTH_DP
import com.android.tools.preview.config.Preview.DeviceSpec.OPERATOR
import com.android.tools.preview.config.Preview.DeviceSpec.PARAMETER_CHIN_SIZE
import com.android.tools.preview.config.Preview.DeviceSpec.PARAMETER_DPI
import com.android.tools.preview.config.Preview.DeviceSpec.PARAMETER_ID
import com.android.tools.preview.config.Preview.DeviceSpec.PARAMETER_IS_ROUND
import com.android.tools.preview.config.Preview.DeviceSpec.PARAMETER_ORIENTATION
import com.android.tools.preview.config.Preview.DeviceSpec.PARAMETER_PARENT
import com.android.tools.preview.config.Preview.DeviceSpec.PARAMETER_SHAPE
import com.android.tools.preview.config.Preview.DeviceSpec.PARAMETER_UNIT
import com.android.tools.preview.config.Preview.DeviceSpec.SEPARATOR
import com.android.utils.HashCodes
import kotlin.math.roundToInt
import kotlin.properties.ObservableProperty
import kotlin.reflect.KProperty

/**
 * List of the definitions of reference devices in `Device.kt` in the `ui-tooling` library. The
 * devices do not have a consistent device id used in their definitions so this patches the device
 * id so it shows correctly in our metrics.
 *
 * Remember to update the keys in case Devices.kt is changes in future versions
 */
val referenceDeviceIds =
  mapOf(
    "spec:id=reference_phone,shape=Normal,width=411,height=891,unit=dp,dpi=420" to DEVICE_CLASS_PHONE_ID,
    "spec:id=reference_foldable,shape=Normal,width=673,height=841,unit=dp,dpi=420" to DEVICE_CLASS_FOLDABLE_ID,
    "spec:id=reference_tablet,shape=Normal,width=1280,height=800,unit=dp,dpi=240" to DEVICE_CLASS_TABLET_ID,
    "spec:id=reference_desktop,shape=Normal,width=1920,height=1080,unit=dp,dpi=160" to DEVICE_CLASS_DESKTOP_ID
  )

/**
 * Map linking reference device ids to their real density, so that it can be used when displaying
 * the preview in
 */
private val referenceDeviceRealDensities =
  mapOf(
    DEVICE_CLASS_PHONE_ID to 420,
    DEVICE_CLASS_FOLDABLE_ID to 420,
    DEVICE_CLASS_TABLET_ID to 240,
    DEVICE_CLASS_DESKTOP_ID to 160
  )

/**
 * Defines some hardware parameters of a Device. Can be encoded using [deviceSpec] and decoded using
 * [DeviceConfig.toDeviceConfigOrNull].
 *
 * @param deviceId The device ID if any.
 * @param dimUnit Determines the unit of the given [width] and [height]. Ie: For [DimUnit.px] they
 *   will be considered as pixels.
 * @param shape Shape of the device screen, may affect how the screen behaves, or it may add a
 *   cutout (like with wearables).
 * @param chinSize For round devices only, defines the height of the flat surface on a screen,
 *   measured from the bottom.
 * @param parentDeviceId ID of the parent device used as template for this configuration.
 */
open class DeviceConfig(
  val deviceId: String? = null,
  open val width: Float = DEFAULT_WIDTH_DP.toFloat(),
  open val height: Float = DEFAULT_HEIGHT_DP.toFloat(),
  open val dimUnit: DimUnit = DEFAULT_UNIT,
  open val dpi: Int = DEFAULT_DPI,
  open val shape: Shape = DEFAULT_SHAPE,
  open val chinSize: Float = DEFAULT_CHIN_SIZE_ZERO.toFloat(),
  open val orientation: Orientation = DEFAULT_ORIENTATION,
  open val parentDeviceId: String? = null
) {
  /**
   * String representation of the width as it is used in DeviceSpec Language.
   *
   * @see convertToDeviceSpecDimension
   */
  val widthString: String
    get() = convertToDeviceSpecDimension(width).toString()

  /**
   * String representation of the height as it is used in DeviceSpec Language.
   *
   * @see convertToDeviceSpecDimension
   */
  val heightString: String
    get() = convertToDeviceSpecDimension(height).toString()

  /**
   * String representation of the chinSize as it is used in DeviceSpec Language.
   *
   * @see convertToDeviceSpecDimension
   */
  val chinSizeString: String
    get() = convertToDeviceSpecDimension(chinSize).toString()

  open val isRound: Boolean
    get() = shape == Shape.Round

  /** Returns a string that defines the Device in the current state of [DeviceConfig] */
  fun deviceSpec(): String {
    if (deviceId != null) {
      // In case we have a device class device we don't need to rebuild any string,
      // we can just take the key from the reference device id map
      referenceDeviceIds.entries
        .firstOrNull { (_, deviceClassId) -> deviceClassId == deviceId }
        ?.key
        ?.let { referenceString ->
          return referenceString
        }
    }
    val builder = StringBuilder(DEVICE_BY_SPEC_PREFIX)
    if (parentDeviceId != null) {
      // When there's a backing Device ID, only print the parameters that are not inherent to a
      // device, e.g: orientation
      builder.appendParamValue(PARAMETER_PARENT, parentDeviceId.toString())
      builder.addOrientationIfNeeded()
      return builder.toString()
    }
    builder.appendParamValue(PARAMETER_WIDTH, widthString + dimUnit.name)
    builder.appendSeparator()
    builder.appendParamValue(PARAMETER_HEIGHT, heightString + dimUnit.name)
    if (dpi != DEFAULT_DPI) {
      builder.appendSeparator()
      builder.appendParamValue(PARAMETER_DPI, dpi.toString())
    }
    if (isRound) {
      builder.appendSeparator()
      builder.appendParamValue(PARAMETER_IS_ROUND, isRound.toString())
      if (chinSize.roundToInt() != DEFAULT_CHIN_SIZE_ZERO) {
        // ChinSize is only applicable to round devices, see
        // com.android.sdklib.devices.Screen#getChin
        builder.appendSeparator()
        builder.appendParamValue(PARAMETER_CHIN_SIZE, chinSizeString + dimUnit.name)
      }
    }
    builder.addOrientationIfNeeded()
    return builder.toString()
  }

  /**
   * Adds the Orientation parameter if it's relevant. That means, that the orientation value doesn't
   * match the implicit orientation from the width and height.
   */
  private fun StringBuilder.addOrientationIfNeeded() {
    if (
      height > width && orientation == Orientation.landscape ||
        width > height && orientation == Orientation.portrait
    ) {
      appendSeparator()
      appendParamValue(PARAMETER_ORIENTATION, orientation.name)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (other !is DeviceConfig) {
      return false
    }
    return deviceSpec() == other.deviceSpec()
  }

  override fun hashCode(): Int {
    return HashCodes.mix(
      width.hashCode(),
      height.hashCode(),
      dpi,
      shape.hashCode(),
      dimUnit.hashCode(),
      chinSize.hashCode(),
      isRound.hashCode(),
      orientation.hashCode(),
      parentDeviceId.hashCode()
    )
  }

  companion object {
    /**
     * Parses [serialized] into a mutable [DeviceConfig] instance. It may specify a custom Device by
     * its properties, such as Width and Height, or it may specify an existing Device with a slight
     * modification, in which case, [availableDevices] should not be empty.
     *
     * @see [DeviceConfig.deviceSpec]
     */
    fun toMutableDeviceConfigOrNull(
      serialized: String?,
      availableDevices: Collection<Device>
    ): MutableDeviceConfig? {
      return toDeviceConfigOrNull(serialized, availableDevices)?.toMutableConfig()
    }

    /**
     * Parses [serialized] into a [DeviceConfig] instance. It may specify a custom Device by its
     * properties, such as Width and Height, or it may specify an existing Device with a slight
     * modification, in which case, [availableDevices] should not be empty.
     *
     * @see [DeviceConfig.deviceSpec]
     */
    fun toDeviceConfigOrNull(
      serialized: String?,
      availableDevices: Collection<Device>
    ): DeviceConfig? {
      if (serialized == null || !serialized.startsWith(DEVICE_BY_SPEC_PREFIX)) return null
      val configString = serialized.substringAfter(DEVICE_BY_SPEC_PREFIX)
      // Find if the given spec belongs to a reference device and if it does, use that as device id.
      val referenceDeviceId = referenceDeviceIds[serialized]
      val deviceIdMap = referenceDeviceId?.let { mapOf(PARAMETER_ID to it) } ?: emptyMap()
      val paramsMap =
        deviceIdMap +
          configString
            .split(SEPARATOR)
            .filter { it.length >= 3 && it.contains(OPERATOR) }
            .associate { paramString ->
              Pair(
                paramString.substringBefore(OPERATOR).trim(),
                paramString.substringAfter(OPERATOR).trim()
              )
            }

      if (!paramsMap.containsKey(PARAMETER_SHAPE)) {
        return parseDeviceSpecLanguage(paramsMap, availableDevices)
      }

      // This format supports (and requires) 5 parameters: shape, width, height, unit, dpi
      // This should only be called because the Preview Inspection didn't find anything wrong, so we
      // can just worry about parsing the
      // parameters we use and ignore everything else
      val shape =
        enumValueOfOrNull<Shape>(paramsMap.getOrDefault(PARAMETER_SHAPE, "")) ?: return null
      val width = paramsMap.getOrDefault(PARAMETER_WIDTH, "").toIntOrNull() ?: return null
      val height = paramsMap.getOrDefault(PARAMETER_HEIGHT, "").toIntOrNull() ?: return null
      val dimUnit =
        enumValueOfOrNull<DimUnit>(paramsMap.getOrDefault(PARAMETER_UNIT, "").lowercase())
          ?: return null
      val dpi =
        if (referenceDeviceId != null) {
          referenceDeviceRealDensities[referenceDeviceId]!!
        } else {
          paramsMap.getOrDefault(PARAMETER_DPI, "").toIntOrNull() ?: return null
        }
      return DeviceConfig(
        deviceId = referenceDeviceId,
        width = width.toFloat(),
        height = height.toFloat(),
        dimUnit = dimUnit,
        dpi = dpi,
        shape = shape,
        orientation = if (width > height) Orientation.landscape else Orientation.portrait
      )
    }

    /**
     * Parse the DeviceSpec as defined by the DeviceSpec Language.
     *
     * There are two supported formats:
     * - Based on an existing [Device], given by the [PARAMETER_PARENT], with an optional
     *   [PARAMETER_ORIENTATION].
     * - Fully custom device, requires at least [PARAMETER_WIDTH] and [PARAMETER_HEIGHT], with all
     *   other screen related parameters being optional.
     *
     * May return null if the required parameters aren't found or if there's an issue parsing any
     * found parameter.
     *
     * @param params Name-value map of the parameters to parse
     * @param availableDevices Collection used to find the [Device] referenced by [PARAMETER_PARENT]
     *   (if it's present)
     */
    private fun parseDeviceSpecLanguage(
      params: Map<String, String>,
      availableDevices: Collection<Device>
    ): DeviceConfig? {
      val parentId = params[PARAMETER_PARENT]
      if (parentId != null) {
        val matchingDevice = availableDevices.firstOrNull { it.id == parentId } ?: return null
        return parseFromExistingDeviceWithDeviceSpecLanguage(matchingDevice, params)
      } else {
        return parseFromCustomDeviceSpecLanguage(params)
      }
    }

    private fun parseFromExistingDeviceWithDeviceSpecLanguage(
      device: Device,
      params: Map<String, String>
    ): DeviceConfig? {
      val initialConfig = device.toDeviceConfig()
      if (params[PARAMETER_ORIENTATION] != null) {
        // Return null if parsing fails
        val orientation =
          enumValueOfOrNull<Orientation>(params.getOrDefault(PARAMETER_ORIENTATION, ""))
          ?: return null

        // Override orientation
        return initialConfig
          .toMutableConfig()
          .apply { this.orientation = orientation }
          .toImmutableConfig()
      }
      return initialConfig
    }

    private fun parseFromCustomDeviceSpecLanguage(params: Map<String, String>): DeviceConfig? {
      // Width & height are required
      val width = parseAndroidNumberOrNull(params[PARAMETER_WIDTH]) ?: return null
      val height = parseAndroidNumberOrNull(params[PARAMETER_HEIGHT]) ?: return null
      val chinSize = parseAndroidNumberOrNull(params[PARAMETER_CHIN_SIZE]) // Chin size is optional
      if (width.unit != height.unit) {
        // We currently require the units of all dimensions to match
        return null
      } else if (
        params[PARAMETER_CHIN_SIZE] != null && (chinSize == null || chinSize.unit != width.unit)
      ) {
        // If chinSize is present, but parsing failed (chinSize == null) or it doesn't match the
        // width & height unit
        return null
      }

      val dimUnit = width.unit
      val dpi =
        if (params[PARAMETER_DPI] != null) {
          // Only return null if the parsing itself failed
          params[PARAMETER_DPI]?.toIntOrNull() ?: return null
        } else {
          // Default value for optional parameter
          DEFAULT_DPI
        }
      val isRound =
        if (params[PARAMETER_IS_ROUND] != null) {
          // Only return null if the parsing itself failed
          params[PARAMETER_IS_ROUND]?.toBooleanStrictOrNull() ?: return null
        } else {
          // Default value for optional parameter
          DEFAULT_IS_ROUND
        }
      val chinSizeValue =
        if (params[PARAMETER_CHIN_SIZE] != null) {
          // Only return null if the parsing itself failed
          chinSize?.value ?: return null
        } else {
          // Default value for optional parameter
          DEFAULT_CHIN_SIZE_ZERO.toFloat()
        }

      val orientation =
        if (params[PARAMETER_ORIENTATION] != null) {
          enumValueOfOrNull<Orientation>(params.getOrDefault(PARAMETER_ORIENTATION, ""))
            ?: return null
        } else {
          if (width.value > height.value) {
            Orientation.landscape
          } else {
            Orientation.portrait
          }
        }
      return DeviceConfig(
        deviceId = params[PARAMETER_ID],
        width = width.value,
        height = height.value,
        dimUnit = dimUnit,
        dpi = dpi,
        shape = if (isRound || chinSizeValue > 0) Shape.Round else Shape.Normal,
        chinSize = chinSizeValue,
        orientation = orientation,
        parentDeviceId =
          null // Not supported when explicitly declaring width, height, dpi, shape, chinSize
      )
    }
  }
}

/**
 * Mutable equivalent of [DeviceConfig].
 *
 * Note that modifying [MutableDeviceConfig.dimUnit] or [MutableDeviceConfig.orientation] will also
 * change the width and height values.
 */
class MutableDeviceConfig(
  id: String = "",
  initialWidth: Float = DEFAULT_WIDTH_DP.toFloat(),
  initialHeight: Float = DEFAULT_HEIGHT_DP.toFloat(),
  initialDimUnit: DimUnit = DEFAULT_UNIT,
  initialDpi: Int = DEFAULT_DPI,
  initialShape: Shape = DEFAULT_SHAPE,
  initialChinSize: Float = DEFAULT_CHIN_SIZE_ZERO.toFloat(),
  initialOrientation: Orientation = DEFAULT_ORIENTATION,
  initialBackingDeviceId: String? = null
) : DeviceConfig(id, initialWidth, initialHeight, initialDimUnit, initialDpi, initialShape) {

  /**
   * ID of the device this configuration represents, null if it's a custom device.
   *
   * This means that changes on other device properties may nullify this property.
   */
  override var parentDeviceId: String? = initialBackingDeviceId

  /** Changes to this property nullifies [parentDeviceId]. */
  override var width: Float by invalidateIdOnPropertyChangeDelegate(initialWidth)

  /** Changes to this property nullifies [parentDeviceId]. */
  override var height: Float by invalidateIdOnPropertyChangeDelegate(initialHeight)

  /** Changes to this property nullifies [parentDeviceId]. */
  override var chinSize: Float by invalidateIdOnPropertyChangeDelegate(initialChinSize)

  /** Changes to this property nullifies [parentDeviceId]. */
  override var dpi: Int by invalidateIdOnPropertyChangeDelegate(initialDpi)

  /** Changes to this property nullifies [parentDeviceId]. */
  override var shape: Shape by invalidateIdOnPropertyChangeDelegate(initialShape)

  /**
   * Defines the unit in which [width] and [height] should be considered. Modifying this property
   * also changes [width] and [height].
   */
  override var dimUnit: DimUnit = initialDimUnit
    set(newValue) {
      if (newValue != field) {
        field = newValue
        val baseDpi = Density.MEDIUM.dpiValue
        val dpiFactor =
          when (newValue) {
            DimUnit.px -> 1.0f * dpi / baseDpi
            DimUnit.dp -> 1.0f * baseDpi / dpi
          }
        width *= dpiFactor
        height *= dpiFactor
        chinSize *= dpiFactor
      }
    }

  override var orientation: Orientation = initialOrientation

  /**
   * Returns a property delegate that nullifies the [parentDeviceId] whenever the property sees a
   * different value.
   */
  private fun <T> invalidateIdOnPropertyChangeDelegate(initialValue: T) =
    object : ObservableProperty<T>(initialValue) {
      override fun afterChange(property: KProperty<*>, oldValue: T, newValue: T) {
        if (newValue != oldValue) {
          parentDeviceId = null
        }
      }
    }
}

/** Returns an immutable copy of this [MutableDeviceConfig] instance. */
internal fun MutableDeviceConfig.toImmutableConfig(): DeviceConfig =
  DeviceConfig(
    shape = this.shape,
    width = this.width,
    height = this.height,
    dimUnit = this.dimUnit,
    dpi = this.dpi,
    orientation = this.orientation,
    parentDeviceId = this.parentDeviceId
  )

/** Returns a mutable copy of this [DeviceConfig] instance. */
fun DeviceConfig.toMutableConfig(): MutableDeviceConfig =
  MutableDeviceConfig(
    initialShape = this.shape,
    initialWidth = this.width,
    initialHeight = this.height,
    initialDimUnit = this.dimUnit,
    initialDpi = this.dpi,
    initialChinSize = this.chinSize,
    initialOrientation = this.orientation,
    initialBackingDeviceId = this.parentDeviceId
  )

/** Convenience class to define an Android dimension by its number [value] and [unit]. */
private class AndroidDimension(val value: Float, val unit: DimUnit)

private fun parseAndroidNumberOrNull(text: String?): AndroidDimension? {
  if (text == null) return null
  val unit = text.takeLast(2)
  val dimUnit = enumValueOfOrNull<DimUnit>(unit) ?: return null
  val value = text.dropLast(2).toFloatOrNull() ?: return null
  return AndroidDimension(value = value, unit = dimUnit)
}

private fun StringBuilder.appendParamValue(parameterName: String, value: String): StringBuilder =
  append("$parameterName$OPERATOR$value")

private fun StringBuilder.appendSeparator(): StringBuilder = append(SEPARATOR)

/** The visual shape of the Device, usually applied as cutout. */
enum class Shape {
  Normal,
  Round,
}

/** Unit for the Device's width and height. */
enum class DimUnit {
  px,
  dp
}

enum class Orientation {
  portrait,
  landscape
}

/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.streaming.device

import com.android.utils.Base128InputStream
import com.android.utils.Base128OutputStream
import java.util.EnumSet
import kotlin.text.Charsets.UTF_8

/** Similar to the `android.hardware.devicestate.DeviceState` class. */
internal data class DeviceState(
  val id: Int,
  val name: String,
  val systemProperties: Set<Property> = emptySet(),
  val physicalProperties: Set<Property> = emptySet(),
) {

  fun serialize(stream: Base128OutputStream) {
    stream.writeInt(id);
    stream.writeBytes(name.toByteArray())
    stream.writeUInt(systemProperties.toMask())
    stream.writeUInt(physicalProperties.toMask())
  }

  override fun hashCode(): Int {
    return id
  }

  private fun Set<Property>.toMask(): UInt {
    var result = 0u
    for (property in this) {
      result = result or property.mask
    }
    return result
  }

  companion object {

    fun deserialize(stream: Base128InputStream): DeviceState {
      val id = stream.readInt()
      val name = stream.readBytes().toString(UTF_8)
      val systemProperties = maskToProperties(stream.readUInt())
      val physicalProperties = maskToProperties(stream.readUInt())
      return DeviceState(id, name, systemProperties, physicalProperties)
    }

    private fun maskToProperties(mask: UInt): Set<Property> {
      val result = EnumSet.noneOf(Property::class.java)
      for (property in Property.values()) {
        if ((mask and property.mask) != 0u) {
          result.add(property)
        }
      }
      return result
    }
  }

  enum class Property(val mask: UInt) {
    PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_CLOSED(1u shl 0),
    PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_HALF_OPEN(1u shl 1),
    PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_OPEN(1u shl 2),
    PROPERTY_POLICY_CANCEL_OVERRIDE_REQUESTS(1u shl 3),
    PROPERTY_POLICY_CANCEL_WHEN_REQUESTER_NOT_ON_TOP(1u shl 4),
    PROPERTY_POLICY_UNSUPPORTED_WHEN_THERMAL_STATUS_CRITICAL(1u shl 5),
    PROPERTY_POLICY_UNSUPPORTED_WHEN_POWER_SAVE_MODE(1u shl 6),
    PROPERTY_POLICY_AVAILABLE_FOR_APP_REQUEST(1u shl 7),
    PROPERTY_APP_INACCESSIBLE(1u shl 8),
    PROPERTY_EMULATED_ONLY(1u shl 9),
    PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_OUTER_PRIMARY(1u shl 10),
    PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_INNER_PRIMARY(1u shl 11),
    PROPERTY_POWER_CONFIGURATION_TRIGGER_SLEEP(1u shl 12),
    PROPERTY_POWER_CONFIGURATION_TRIGGER_WAKE(1u shl 13),
    PROPERTY_EXTENDED_DEVICE_STATE_EXTERNAL_DISPLAY(1u shl 14),
    PROPERTY_FEATURE_REAR_DISPLAY(1u shl 15),
    PROPERTY_FEATURE_DUAL_DISPLAY_INTERNAL_DEFAULT(1u shl 16),
  }
}

private fun Base128InputStream.readUInt(): UInt = readInt().toUInt()
private fun Base128OutputStream.writeUInt(value: UInt) = writeInt(value.toInt())


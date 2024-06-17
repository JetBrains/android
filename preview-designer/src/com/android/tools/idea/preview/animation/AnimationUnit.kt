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
package com.android.tools.idea.preview.animation

import com.android.tools.idea.preview.PreviewBundle.message

/** Units represented as multi-dimensional properties for animation inspection. */
object AnimationUnit {

  /**
   * Parses a given value and creates a corresponding [NumberUnit].
   *
   * @param value The value to parse, which can be an Int, Double, or Float.
   * @return The parsed [NumberUnit] or null if the value type is not supported.
   */
  fun parseNumberUnit(value: Any?): NumberUnit<*>? {
    return when (value) {
      is Int -> IntUnit(value)
      is Double -> DoubleUnit(value)
      is Float -> FloatUnit(value)
      else -> null
    }
  }

  /**
   * Represents a single property value at a specific time point in an animation.
   *
   * @property propertyLabel The name or label of the animated property.
   * @property unit The unit representing the property's value.
   */
  class TimelineUnit(val propertyLabel: String, val unit: Unit<*>)

  /**
   * Interface representing a multi-dimensional property.
   *
   * @param A The type of the components in the property.
   */
  interface Unit<A> {
    /** The components of the multi-dimensional property. */
    val components: List<A>

    /**
     * Returns a string representation of the component at the specified index. If the index is out
     * of bounds, returns an underscore ("_").
     *
     * @param componentId The index of the component.
     */
    fun toString(componentId: Int): String

    /** Returns a string representation of the entire property. */
    override fun toString(): String

    /**
     * Parses a string representation of a unit into a corresponding [Unit] object.
     *
     * @param getValue A function to get the string value for each component index.
     * @return The parsed [Unit] or null if parsing fails.
     */
    fun parseUnit(getValue: (Int) -> String?): Unit<*>?

    /** Returns a title suitable for a picker for this type of unit. */
    fun getPickerTitle(): String
  }

  /**
   * Interface representing a multi-dimensional property with numeric components.
   *
   * @param A The type of the numeric components.
   */
  interface NumberUnit<A : Number> : Unit<A> {
    fun componentAsDouble(componentId: Int) = components[componentId].toDouble()
  }

  /**
   * Abstract base class for multi-dimensional units.
   *
   * @param A The type of the components in the property.
   * @param components The component values of the property.
   */
  abstract class BaseUnit<A>(vararg components: A) : Unit<A> {
    override val components = components.toList()

    override fun toString(): String =
      if (components.size == 1) components[0].toString()
      else
        components.joinToString(prefix = "( ", postfix = " )", separator = " , ") { it.toString() }

    override fun hashCode(): Int {
      return components.fold(1) { hash, element -> 31 * hash + (element?.hashCode() ?: 0) }
    }

    override fun toString(componentId: Int): String {
      return buildString {
        append("( ")
        for (i in components.indices) {
          if (i == componentId) {
            append(components[i])
          } else {
            append("_")
          }
          if (i < components.size - 1) {
            append(" , ")
          }
        }
        append(" )")
      }
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true // Same reference
      if (other !is BaseUnit<*>) return false // Different type
      if (components.size != other.components.size) return false // Different size
      // Compare components pairwise, handling nulls
      return components.zip(other.components).all { (a, b) -> a == b || (a == null && b == null) }
    }
  }

  class IntUnit(value: Int) : BaseUnit<Int>(value), NumberUnit<Int> {

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      return try {
        getValue(0)?.toInt()?.let { IntUnit(it) }
      } catch (_: NumberFormatException) {
        null
      }
    }

    override fun getPickerTitle() = message("animation.inspector.picker.int")
  }

  class DoubleUnit(value: Double) : BaseUnit<Double>(value), NumberUnit<Double> {

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      return try {
        getValue(0)?.toDouble()?.let { DoubleUnit(it) }
      } catch (_: NumberFormatException) {
        null
      }
    }

    override fun getPickerTitle() = message("animation.inspector.picker.double")
  }

  class FloatUnit(value: Float) : BaseUnit<Float>(value), NumberUnit<Float> {

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      return try {
        getValue(0)?.toFloat()?.let { FloatUnit(it) }
      } catch (_: NumberFormatException) {
        null
      }
    }

    override fun getPickerTitle() = message("animation.inspector.picker.float")
  }

  class StringUnit(value: String) : BaseUnit<String>(value) {

    override fun parseUnit(getValue: (Int) -> String?) = getValue(0)?.let { StringUnit(it) }

    override fun getPickerTitle() = message("animation.inspector.picker.string")
  }

  open class UnitUnknown(val any: Any?) : Unit<Any?> {
    override val components = listOf(any)

    override fun hashCode(): Int = any.hashCode()

    override fun equals(other: Any?): Boolean {
      return other is UnitUnknown && any == other.any
    }

    override fun toString(componentId: Int) = any.toString()

    override fun toString(): String = any.toString()

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      return null
    }

    override fun getPickerTitle() = message("animation.inspector.picker.value")
  }

  interface Color {
    val color: java.awt.Color?
  }
}

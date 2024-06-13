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

/** Units represented as multi-dimensional properties. */
object AnimationUnit {

  /**
   * Parses and creates a [NumberUnit]
   *
   * @return a property which could 1, 2, 3 or 4 - dimensional property - [Unit1D], [Unit2D],
   *   [Unit3D], [Unit4D] respectively.
   */
  fun parseNumberUnit(value: Any?): NumberUnit<*>? {
    if (value == null) return null
    return when (value.javaClass.kotlin.qualifiedName) {
      "kotlin.Int" -> if (value is Int) IntUnit(value) else null
      "kotlin.Double" -> if (value is Double) DoubleUnit(value) else null
      "kotlin.Float" -> if (value is Float) FloatUnit(value) else null
      else -> UnknownNumberUnit(value)
    }
  }

  /**
   * Represents a single unit of property information within an animation inspection context.
   *
   * @property propertyLabel The name or label of the animated property.
   * @property unit The core unit representing the property's value at a specific time point in the
   *   animation. This could be a number, a color, or other custom types.
   */
  class TimelineUnit(val propertyLabel: String, val unit: NumberUnit<*>?)

  /** Multidimensional property with each dimension of the type [A]. */
  interface Unit<A> {
    val components: List<A>

    fun toString(componentId: Int): String

    override fun toString(): String

    fun parseUnit(getValue: (Int) -> String?): Unit<*>?

    fun getPickerTitle(): String
  }

  /** Multidimensional property with each dimension of the type [A]. */
  interface NumberUnit<A> : Unit<A> where A : Number, A : Comparable<A> {
    /**
     * Transforms a component to a [Double]. It unifies painting of the curves in [InspectorPainter]
     * .
     */
    fun componentAsDouble(componentId: Int) = components[componentId].toDouble()
  }

  abstract class Unit1D<A>(val component1: A) : Unit<A> {
    override val components = listOf(component1)

    override fun toString(componentId: Int) = component1.toString()

    override fun toString(): String = components.joinToString { it.toString() }
  }

  abstract class Unit2D<A>(val component1: A, val component2: A) : Unit<A> {
    override val components = listOf(component1, component2)

    override fun toString(componentId: Int) =
      "( " +
        "${if (componentId == 0) component1 else "_"} , " +
        "${if (componentId == 1) component2 else "_"} )"

    override fun toString(): String =
      components.joinToString(prefix = "( ", postfix = " )", separator = " , ") { it.toString() }
  }

  abstract class Unit3D<A>(val component1: A, val component2: A, val component3: A) : Unit<A> {
    override val components = listOf(component1, component2, component3)

    override fun toString(componentId: Int) =
      "( " +
        "${if (componentId == 0) component1 else "_"} , " +
        "${if (componentId == 1) component2 else "_"} , " +
        "${if (componentId == 2) component3 else "_"} )"

    override fun toString(): String =
      components.joinToString(prefix = "( ", postfix = " )", separator = " , ") { it.toString() }
  }

  abstract class Unit4D<A>(
    val component1: A,
    val component2: A,
    val component3: A,
    val component4: A,
  ) : Unit<A> {
    override val components = listOf(component1, component2, component3, component4)

    override fun toString(componentId: Int) =
      "( " +
        "${if (componentId == 0) component1 else "_"} , " +
        "${if (componentId == 1) component2 else "_"} , " +
        "${if (componentId == 2) component3 else "_"} , " +
        "${if (componentId == 3) component4 else "_"} )"

    override fun toString(): String =
      components.joinToString(prefix = "( ", postfix = " )", separator = " , ") { it.toString() }
  }

  class IntUnit(value: Int) : Unit1D<Int>(value), NumberUnit<Int> {

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      return try {
        getValue(0)?.toInt()?.let { IntUnit(it) }
      } catch (_: NumberFormatException) {
        null
      }
    }

    override fun getPickerTitle() = message("animation.inspector.picker.int")
  }

  class DoubleUnit(value: Double) : Unit1D<Double>(value), NumberUnit<Double> {

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      return try {
        getValue(0)?.toDouble()?.let { DoubleUnit(it) }
      } catch (_: NumberFormatException) {
        null
      }
    }

    override fun getPickerTitle() = message("animation.inspector.picker.double")
  }

  class FloatUnit(value: Float) : Unit1D<Float>(value), NumberUnit<Float> {

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      return try {
        getValue(0)?.toFloat()?.let { FloatUnit(it) }
      } catch (_: NumberFormatException) {
        null
      }
    }

    override fun getPickerTitle() = message("animation.inspector.picker.float")
  }

  class StringUnit(value: String) : Unit1D<String>(value) {

    override fun parseUnit(getValue: (Int) -> String?) = getValue(0)?.let { StringUnit(it) }

    override fun getPickerTitle() = message("animation.inspector.picker.string")
  }

  open class UnitUnknown(val any: Any) : Unit1D<Int>(0) {
    override val components = listOf(0)

    override fun toString(componentId: Int) = any.toString()

    override fun toString(): String = any.toString()

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      return null
    }

    override fun getPickerTitle() = message("animation.inspector.picker.value")
  }

  class UnknownNumberUnit(any: Any) : UnitUnknown(any), NumberUnit<Int>

  interface Color {
    val color: java.awt.Color?
  }
}

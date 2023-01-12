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
package com.android.tools.idea.compose.preview.animation

import androidx.compose.animation.tooling.ComposeAnimatedProperty
import com.android.tools.adtui.model.stdui.EDITOR_NO_ERROR
import com.android.tools.idea.compose.preview.animation.picker.AnimatedPropertyItem
import com.android.tools.idea.compose.preview.animation.validation.DoubleValidation
import com.android.tools.idea.compose.preview.animation.validation.DpValidation
import com.android.tools.idea.compose.preview.animation.validation.FloatValidation
import com.android.tools.idea.compose.preview.animation.validation.IntValidation
import com.android.tools.idea.compose.preview.message
import com.intellij.ui.ColorUtil
import java.lang.reflect.Method

/** Compose units represented as multi-dimensional properties. */
object ComposeUnit {

  class TimelineUnit(val propertyLabel: String, val unit: NumberUnit<*>?)

  /** Multi-dimensional property with each dimension of the type [A]. */
  interface Unit<A> {
    val components: List<A>
    fun toString(componentId: Int): String
    override fun toString(): String

    fun createProperties(prefix: String): List<AnimatedPropertyItem> =
      components.mapIndexed { index, component ->
        AnimatedPropertyItem("${prefix}.$index", "$component", { EDITOR_NO_ERROR }, "Any")
      }

    fun parseUnit(getValue: (Int) -> String?): Unit<*>?

    fun getPickerTitle(): String
  }

  /** Multi-dimensional property with each dimension of the type [A]. */
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
    override fun toString(): String = components.joinToString() { it.toString() }
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
    val component4: A
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

  /**
   * Parses and creates a [Unit] from [ComposeAnimatedProperty.value].
   *
   * @return a property which could 1, 2, 3 or 4 - dimensional property - [Unit1D], [Unit2D],
   * [Unit3D], [Unit4D] respectively.
   */
  fun parse(property: ComposeAnimatedProperty): NumberUnit<*>? = parseNumberUnit(property.value)

  /**
   * Parses and creates a [NumberUnit]
   *
   * @return a property which could 1, 2, 3 or 4 - dimensional property - [Unit1D], [Unit2D],
   * [Unit3D], [Unit4D] respectively.
   */
  fun parseNumberUnit(value: Any?): NumberUnit<*>? {
    if (value == null) return null
    return when (value.javaClass.kotlin.qualifiedName) {
      Color.CLASS_NAME -> Color.create(value)
      Dp.CLASS_NAME -> Dp.create(value)
      Size.CLASS_NAME -> Size.create(value)
      Rect.CLASS_NAME -> Rect.create(value)
      IntOffset.CLASS_NAME -> IntOffset.create(value)
      IntSize.CLASS_NAME -> IntSize.create(value)
      Offset.CLASS_NAME -> Offset.create(value)
      "kotlin.Int" -> if (value is Int) IntUnit(value) else null
      "kotlin.Double" -> if (value is Double) DoubleUnit(value) else null
      "kotlin.Float" -> if (value is Float) FloatUnit(value) else null
      else -> UnknownNumberUnit(value)
    }
  }

  class IntUnit(value: Int) : Unit1D<Int>(value), NumberUnit<Int> {
    override fun createProperties(prefix: String) =
      listOf(AnimatedPropertyItem(prefix, "$component1", IntValidation, "Int"))

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
    override fun createProperties(prefix: String) =
      listOf(AnimatedPropertyItem(prefix, "$component1", DoubleValidation, "Double"))

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
    override fun createProperties(prefix: String) =
      listOf(AnimatedPropertyItem(prefix, "$component1", FloatValidation, "Float"))

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      return try {
        getValue(0)?.toFloat()?.let { FloatUnit(it) }
      } catch (_: NumberFormatException) {
        null
      }
    }

    override fun getPickerTitle() = message("animation.inspector.picker.float")
  }

  open class UnitUnknown(val any: Any) : Unit1D<Int>(0) {
    override val components = listOf(0)
    override fun toString(componentId: Int) = any.toString()
    override fun toString(): String = any.toString()

    override fun createProperties(prefix: String) =
      listOf(AnimatedPropertyItem(prefix, "$any", { EDITOR_NO_ERROR }, "Any"))

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      // TODO Not supported at the moment.
      return null
    }

    override fun getPickerTitle() = message("animation.inspector.picker.value")
  }

  class UnknownNumberUnit(any: Any) : UnitUnknown(any), NumberUnit<Int>

  class IntSize(component1: Int, component2: Int) :
    Unit2D<Int>(component1, component2), NumberUnit<Int> {
    companion object {
      const val CLASS_NAME = "androidx.compose.ui.unit.IntSize"
      private val COMPONENT_NAMES = arrayOf("width", "height")
      fun create(property: Any?): IntSize? {
        property?.also {
          val value = findMethodByName("unbox-impl", property)?.invoke(property)
          if (value !is Long) return null
          val width = findMethodByName("getWidth-impl", property)?.invoke(null, value)
          val height = findMethodByName("getHeight-impl", property)?.invoke(null, value)
          if (width is Int && height is Int) return IntSize(width, height)
        }
        return null
      }
    }

    override fun toString(componentId: Int) =
      "${COMPONENT_NAMES[componentId]} ${super.toString(componentId)}"

    override fun createProperties(prefix: String) =
      components.mapIndexed { index, component ->
        AnimatedPropertyItem(COMPONENT_NAMES[index], "$component", IntValidation, "Int")
      }

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      try {
        val component1 = getValue(0)?.toInt() ?: return null
        val component2 = getValue(1)?.toInt() ?: return null
        return IntSize(component1, component2)
      } catch (_: NumberFormatException) {
        return null
      }
    }

    override fun getPickerTitle() = message("animation.inspector.picker.int.size")
  }

  class IntOffset(component1: Int, component2: Int) :
    Unit2D<Int>(component1, component2), NumberUnit<Int> {
    companion object {
      const val CLASS_NAME = "androidx.compose.ui.unit.IntOffset"
      private val COMPONENT_NAMES = arrayOf("x", "y")
      fun create(property: Any?): IntOffset? {
        property?.also {
          val value = findMethodByName("unbox-impl", property)?.invoke(property)
          if (value !is Long) return null
          val x = findMethodByName("getX-impl", property)?.invoke(null, value)
          val y = findMethodByName("getY-impl", property)?.invoke(null, value)
          if (x is Int && y is Int) return IntOffset(x, y)
        }
        return null
      }
    }

    override fun toString(componentId: Int) =
      "${COMPONENT_NAMES[componentId]} ${super.toString(componentId)}"

    override fun createProperties(prefix: String) =
      components.mapIndexed { index, component ->
        AnimatedPropertyItem(COMPONENT_NAMES[index], "$component", IntValidation, "Int")
      }

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      return try {
        val component1 = getValue(0)?.toInt() ?: return null
        val component2 = getValue(1)?.toInt() ?: return null
        return IntOffset(component1, component2)
      } catch (_: NumberFormatException) {
        null
      }
    }

    override fun getPickerTitle() = message("animation.inspector.picker.int.offset")
  }

  class Dp(component1: Float) : Unit1D<Float>(component1), NumberUnit<Float> {
    companion object {
      const val CLASS_NAME = "androidx.compose.ui.unit.Dp"
      fun create(property: Any?): Dp? {
        property?.also {
          val value = findMethodByName("getValue", property)?.invoke(property)
          if (value is Float) return Dp(value)
        }
        return null
      }
    }

    override fun toString(componentId: Int) = "${component1}dp"
    override fun toString(): String = "${component1}dp"

    override fun createProperties(prefix: String) =
      listOf(AnimatedPropertyItem(prefix, "$component1", DpValidation, "Float"))

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      return try {
        getValue(0)?.toFloat()?.let { Dp(it) }
      } catch (_: NumberFormatException) {
        null
      }
    }

    override fun getPickerTitle() = message("animation.inspector.picker.dp")
  }

  class Size(component1: Float, component2: Float) :
    Unit2D<Float>(component1, component2), NumberUnit<Float> {
    companion object {
      const val CLASS_NAME = "androidx.compose.ui.geometry.Size"
      private val COMPONENT_NAMES = arrayOf("width", "height")
      fun create(property: Any?): Size? {
        property?.also {
          val value = findMethodByName("unbox-impl", property)?.invoke(property)
          if (value !is Long) return null
          val width = findMethodByName("getWidth-impl", property)?.invoke(null, value)
          val height = findMethodByName("getHeight-impl", property)?.invoke(null, value)
          if (width is Float && height is Float) return Size(width, height)
        }
        return null
      }
    }

    override fun toString(componentId: Int) =
      "${COMPONENT_NAMES[componentId]} ${super.toString(componentId)}"

    override fun createProperties(prefix: String) =
      components.mapIndexed { index, component ->
        AnimatedPropertyItem(COMPONENT_NAMES[index], "$component", FloatValidation, "Float")
      }

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      try {
        val component1 = getValue(0)?.toFloat() ?: return null
        val component2 = getValue(1)?.toFloat() ?: return null
        return Size(component1, component2)
      } catch (_: NumberFormatException) {
        return null
      }
    }

    override fun getPickerTitle() = message("animation.inspector.picker.size")
  }

  class Rect(component1: Float, component2: Float, component3: Float, component4: Float) :
    Unit4D<Float>(component1, component2, component3, component4), NumberUnit<Float> {
    companion object {
      const val CLASS_NAME = "androidx.compose.ui.geometry.Rect"
      private val COMPONENT_NAMES = arrayOf("left", "top", "right", "bottom")
      fun create(property: Any?): Rect? {
        property?.also {
          val left = findMethodByName("getLeft", property)?.invoke(property)
          val top = findMethodByName("getTop", property)?.invoke(property)
          val right = findMethodByName("getRight", property)?.invoke(property)
          val bottom = findMethodByName("getBottom", property)?.invoke(property)
          if (left is Float && right is Float && top is Float && bottom is Float)
            return Rect(left, top, right, bottom)
        }
        return null
      }
    }

    override fun toString(componentId: Int) =
      "${COMPONENT_NAMES[componentId]} ${super.toString(componentId)}"

    override fun createProperties(prefix: String) =
      components.mapIndexed { index, component ->
        AnimatedPropertyItem(COMPONENT_NAMES[index], "$component", FloatValidation, "Float")
      }

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      try {
        val component1 = getValue(0)?.toFloat() ?: return null
        val component2 = getValue(1)?.toFloat() ?: return null
        val component3 = getValue(2)?.toFloat() ?: return null
        val component4 = getValue(3)?.toFloat() ?: return null
        return Rect(component1, component2, component3, component4)
      } catch (_: NumberFormatException) {
        return null
      }
    }

    override fun getPickerTitle() = message("animation.inspector.picker.rect")
  }

  class Offset(component1: Float, component2: Float) :
    Unit2D<Float>(component1, component2), NumberUnit<Float> {
    companion object {
      const val CLASS_NAME = "androidx.compose.ui.geometry.Offset"
      private val COMPONENT_NAMES = arrayOf("x", "y")
      fun create(property: Any?): Offset? {
        property?.also {
          val value = findMethodByName("unbox-impl", property)?.invoke(property)
          if (value !is Long) return null
          val x = findMethodByName("getX-impl", property)?.invoke(null, value)
          val y = findMethodByName("getY-impl", property)?.invoke(null, value)
          if (x is Float && y is Float) return Offset(x, y)
        }
        return null
      }
    }

    override fun toString(componentId: Int) =
      "${COMPONENT_NAMES[componentId]} ${super.toString(componentId)}"

    override fun createProperties(prefix: String) =
      components.mapIndexed { index, component ->
        AnimatedPropertyItem(COMPONENT_NAMES[index], "$component", FloatValidation, "Float")
      }

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      try {
        val component1 = getValue(0)?.toFloat() ?: return null
        val component2 = getValue(1)?.toFloat() ?: return null
        return Offset(component1, component2)
      } catch (_: NumberFormatException) {
        return null
      }
    }

    override fun getPickerTitle() = message("animation.inspector.picker.offset")
  }

  class Color(component1: Float, component2: Float, component3: Float, component4: Float) :
    Unit4D<Float>(component1, component2, component3, component4), NumberUnit<Float> {
    companion object {
      const val CLASS_NAME = "androidx.compose.ui.graphics.Color"
      private val COMPONENT_NAMES = arrayOf("red", "green", "blue", "alpha")
      fun create(property: Any?): Color? {
        property?.also {
          val value = findMethodByName("unbox-impl", property)?.invoke(property)
          if (value !is Long) return null
          val red = findMethodByName("getRed-impl", property)?.invoke(null, value)
          val green = findMethodByName("getGreen-impl", property)?.invoke(null, value)
          val blue = findMethodByName("getBlue-impl", property)?.invoke(null, value)
          val alpha = findMethodByName("getAlpha-impl", property)?.invoke(null, value)
          if (red is Float && green is Float && blue is Float && alpha is Float)
            return Color(red, green, blue, alpha)
        }
        return null
      }

      fun create(color: java.awt.Color) =
        Color(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)
    }

    val color: java.awt.Color? =
      try {
        java.awt.Color(component1, component2, component3, component4)
      } catch (_: IllegalArgumentException) {
        null
      }

    override fun toString(componentId: Int) =
      "${COMPONENT_NAMES[componentId]} ${super.toString(componentId)}"

    /** Hex String if [color] is available. */
    override fun toString(): String {
      return color?.let { "0x${ColorUtil.toHex(it, true).uppercase()}" } ?: super.toString()
    }

    override fun createProperties(prefix: String) =
      components.mapIndexed { index, component ->
        AnimatedPropertyItem(COMPONENT_NAMES[index], "$component", FloatValidation, "Color")
      }

    override fun parseUnit(getValue: (Int) -> String?): Unit<*>? {
      try {
        val component1 = getValue(0)?.toFloat() ?: return null
        val component2 = getValue(1)?.toFloat() ?: return null
        val component3 = getValue(2)?.toFloat() ?: return null
        val component4 = getValue(3)?.toFloat() ?: return null
        return Color(component1, component2, component3, component4).takeIf { it.color != null }
      } catch (_: NumberFormatException) {
        return null
      }
    }

    override fun getPickerTitle() = message("animation.inspector.picker.color")
  }

  private fun findMethodByName(methodName: String, property: Any): Method? {
    return property::class.java.methods.singleOrNull { it.name == methodName }?.apply {
      this.isAccessible = true
    }
  }
}

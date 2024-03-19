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
import com.android.tools.idea.preview.animation.AnimationUnit
import com.intellij.ui.ColorUtil
import java.lang.reflect.Method

/** Compose units represented as multi-dimensional properties. */
object ComposeUnit {

  /**
   * Parses and creates a [Unit] from [ComposeAnimatedProperty.value].
   *
   * @return a property which could 1, 2, 3 or 4 - dimensional property - [Unit1D], [Unit2D],
   *   [Unit3D], [Unit4D] respectively.
   */
  fun parse(property: ComposeAnimatedProperty): AnimationUnit.NumberUnit<*>? =
    parseNumberUnit(property.value)

  /**
   * Parses and creates a [NumberUnit]
   *
   * @return a property which could 1, 2, 3 or 4 - dimensional property - [Unit1D], [Unit2D],
   *   [Unit3D], [Unit4D] respectively.
   */
  fun parseNumberUnit(value: Any?): AnimationUnit.NumberUnit<*>? {
    if (value == null) return null
    return when (value.javaClass.kotlin.qualifiedName) {
      Color.CLASS_NAME -> Color.create(value)
      Dp.CLASS_NAME -> Dp.create(value)
      Size.CLASS_NAME -> Size.create(value)
      Rect.CLASS_NAME -> Rect.create(value)
      IntOffset.CLASS_NAME -> IntOffset.create(value)
      IntSize.CLASS_NAME -> IntSize.create(value)
      Offset.CLASS_NAME -> Offset.create(value)
      else -> AnimationUnit.parseNumberUnit(value)
    }
  }

  /**
   * Parses and creates a [Unit]
   *
   * @return a property which could 1, 2, 3 or 4 - dimensional property - [Unit1D], [Unit2D],
   *   [Unit3D], [Unit4D] respectively.
   */
  fun parseStateUnit(value: Any?): AnimationUnit.Unit<*>? {
    return parseNumberUnit(value)?.takeIf { it !is AnimationUnit.UnknownNumberUnit }
      ?: value?.let {
        when (value) {
          is String -> AnimationUnit.StringUnit(value)
          else -> AnimationUnit.UnitUnknown(value)
        }
      }
  }

  class IntSize(component1: Int, component2: Int) :
    AnimationUnit.Unit2D<Int>(component1, component2), AnimationUnit.NumberUnit<Int> {
    companion object {
      internal const val CLASS_NAME = "androidx.compose.ui.unit.IntSize"
      internal val COMPONENT_NAMES = arrayOf("width", "height")

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

    override fun parseUnit(getValue: (Int) -> String?): AnimationUnit.Unit<*>? {
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
    AnimationUnit.Unit2D<Int>(component1, component2), AnimationUnit.NumberUnit<Int> {
    companion object {
      internal const val CLASS_NAME = "androidx.compose.ui.unit.IntOffset"
      internal val COMPONENT_NAMES = arrayOf("x", "y")

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

    override fun parseUnit(getValue: (Int) -> String?): AnimationUnit.Unit<*>? {
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

  class Dp(component1: Float) :
    AnimationUnit.Unit1D<Float>(component1), AnimationUnit.NumberUnit<Float> {
    companion object {
      internal const val CLASS_NAME = "androidx.compose.ui.unit.Dp"

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

    override fun parseUnit(getValue: (Int) -> String?): AnimationUnit.Unit<*>? {
      return try {
        getValue(0)?.toFloat()?.let { Dp(it) }
      } catch (_: NumberFormatException) {
        null
      }
    }

    override fun getPickerTitle() = message("animation.inspector.picker.dp")
  }

  class Size(component1: Float, component2: Float) :
    AnimationUnit.Unit2D<Float>(component1, component2), AnimationUnit.NumberUnit<Float> {
    companion object {
      internal const val CLASS_NAME = "androidx.compose.ui.geometry.Size"
      val COMPONENT_NAMES = arrayOf("width", "height")

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

    override fun parseUnit(getValue: (Int) -> String?): AnimationUnit.Unit<*>? {
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
    AnimationUnit.Unit4D<Float>(component1, component2, component3, component4),
    AnimationUnit.NumberUnit<Float> {
    companion object {
      internal const val CLASS_NAME = "androidx.compose.ui.geometry.Rect"
      internal val COMPONENT_NAMES = arrayOf("left", "top", "right", "bottom")

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

    override fun parseUnit(getValue: (Int) -> String?): AnimationUnit.Unit<*>? {
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
    AnimationUnit.Unit2D<Float>(component1, component2), AnimationUnit.NumberUnit<Float> {
    companion object {
      internal const val CLASS_NAME = "androidx.compose.ui.geometry.Offset"
      internal val COMPONENT_NAMES = arrayOf("x", "y")

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

    override fun parseUnit(getValue: (Int) -> String?): AnimationUnit.Unit<*>? {
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
    AnimationUnit.Unit<Float>,
    AnimationUnit.Unit4D<Float>(component1, component2, component3, component4),
    AnimationUnit.NumberUnit<Float>,
    AnimationUnit.Color {
    companion object {
      internal const val CLASS_NAME = "androidx.compose.ui.graphics.Color"
      internal val COMPONENT_NAMES = arrayOf("red", "green", "blue", "alpha")

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

    override val color: java.awt.Color? =
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

    override fun parseUnit(getValue: (Int) -> String?): AnimationUnit.Unit<*>? {
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
    return property::class
      .java
      .methods
      .singleOrNull { it.name == methodName }
      ?.apply { this.isAccessible = true }
  }

  fun AnimationUnit.Unit<*>.createProperties(prefix: String): List<AnimatedPropertyItem> =
    when (this) {
      is Rect -> {
        components.mapIndexed { index, component ->
          AnimatedPropertyItem(Rect.COMPONENT_NAMES[index], "$component", FloatValidation, "Float")
        }
      }
      is Color -> {
        components.mapIndexed { index, component ->
          AnimatedPropertyItem(Color.COMPONENT_NAMES[index], "$component", FloatValidation, "Color")
        }
      }
      is IntSize -> {
        components.mapIndexed { index, component ->
          AnimatedPropertyItem(IntSize.COMPONENT_NAMES[index], "$component", IntValidation, "Int")
        }
      }
      is IntOffset -> {
        components.mapIndexed { index, component ->
          AnimatedPropertyItem(IntOffset.COMPONENT_NAMES[index], "$component", IntValidation, "Int")
        }
      }
      is Dp -> listOf(AnimatedPropertyItem(prefix, "$component1", DpValidation, "Float"))
      is Size ->
        components.mapIndexed { index, component ->
          AnimatedPropertyItem(Size.COMPONENT_NAMES[index], "$component", FloatValidation, "Float")
        }
      is Offset ->
        components.mapIndexed { index, component ->
          AnimatedPropertyItem(
            Offset.COMPONENT_NAMES[index],
            "$component",
            FloatValidation,
            "Float",
          )
        }
      is AnimationUnit.StringUnit ->
        listOf(AnimatedPropertyItem(prefix, component1, { EDITOR_NO_ERROR }, "String"))
      is AnimationUnit.IntUnit ->
        listOf(AnimatedPropertyItem(prefix, "$component1", IntValidation, "Int"))
      is AnimationUnit.DoubleUnit ->
        listOf(AnimatedPropertyItem(prefix, "$component1", DoubleValidation, "Double"))
      is AnimationUnit.FloatUnit ->
        listOf(AnimatedPropertyItem(prefix, "$component1", FloatValidation, "Float"))
      is AnimationUnit.UnitUnknown ->
        components.mapIndexed { index, component ->
          AnimatedPropertyItem("${prefix}.$index", "$component", { EDITOR_NO_ERROR }, "Any")
        }
      else ->
        components.mapIndexed { index, component ->
          AnimatedPropertyItem("${prefix}.$index", "$component", { EDITOR_NO_ERROR }, "Any")
        }
    }
}

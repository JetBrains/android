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
import java.awt.Color
import java.lang.reflect.Method

/** Compose units represented as multi-dimensional properties. */
object ComposeUnit {

  /** Parses and creates a [AnimationUnit.Unit] from [ComposeAnimatedProperty.value]. */
  fun parse(property: ComposeAnimatedProperty): AnimationUnit.Unit<*> = parseUnit(property.value)

  /** Parses and creates a [AnimationUnit.Unit]. */
  private fun parseUnit(value: Any?): AnimationUnit.Unit<*> {
    if (value == null) return AnimationUnit.UnitUnknown(value)
    return when (value::class.qualifiedName) {
      Color.CLASS_NAME -> Color.create(value)
      Dp.CLASS_NAME -> Dp.create(value)
      Size.CLASS_NAME -> Size.create(value)
      Rect.CLASS_NAME -> Rect.create(value)
      IntOffset.CLASS_NAME -> IntOffset.create(value)
      IntSize.CLASS_NAME -> IntSize.create(value)
      Offset.CLASS_NAME -> Offset.create(value)
      else -> AnimationUnit.parseNumberUnit(value) ?: AnimationUnit.UnitUnknown(value)
    }
  }

  /**
   * Base class for Compose-specific units.
   *
   * @param A The type of the components in the property.
   * @param components The component values of the property.
   * @param componentNames The names of the components.
   */
  abstract class ComposeUnit<A>(vararg components: A, val componentNames: Array<String>) :
    AnimationUnit.BaseUnit<A>(*components) {

    override fun toString(componentId: Int) =
      "${componentNames[componentId]} ${super.toString(componentId)}"

    abstract fun create(property: Any): ComposeUnit<A>

    open fun createProperties(prefix: String): List<AnimatedPropertyItem> =
      components.mapIndexed { index, component ->
        AnimatedPropertyItem(componentNames[index], "$component", { EDITOR_NO_ERROR }, "Any")
      }
  }

  /** Parses and creates a [AnimationUnit.Unit] */
  fun parseStateUnit(value: Any?): AnimationUnit.Unit<*> {
    val unit = parseUnit(value)
    if (unit !is AnimationUnit.UnitUnknown) {
      return unit
    }

    return when (value) {
      is String -> AnimationUnit.StringUnit(value)
      else -> AnimationUnit.UnitUnknown(value)
    }
  }

  class IntSize(width: Int, height: Int) :
    ComposeUnit<Int>(width, height, componentNames = arrayOf("width", "height")),
    AnimationUnit.NumberUnit<Int> {
    companion object {
      internal const val CLASS_NAME = "androidx.compose.ui.unit.IntSize"
      internal val COMPONENT_NAMES = arrayOf("width", "height")

      fun create(property: Any): IntSize {
        val value = findMethodByName("unbox-impl", property)?.invoke(property)
        if (value is Long) {
          val width = findMethodByName("getWidth-impl", property)?.invoke(null, value)
          val height = findMethodByName("getHeight-impl", property)?.invoke(null, value)
          if (width is Int && height is Int) return IntSize(width, height)
        }
        throw ComposeParseException(IntSize::class.simpleName)
      }
    }

    override fun create(property: Any): IntSize = IntSize.create(property)

    override fun createProperties(prefix: String): List<AnimatedPropertyItem> {
      return components.mapIndexed { index, component ->
        AnimatedPropertyItem(COMPONENT_NAMES[index], "$component", IntValidation, "Int")
      }
    }

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

  class IntOffset(x: Int, y: Int) :
    ComposeUnit<Int>(x, y, componentNames = arrayOf("x", "y")), AnimationUnit.NumberUnit<Int> {
    companion object {
      internal const val CLASS_NAME = "androidx.compose.ui.unit.IntOffset"
      internal val COMPONENT_NAMES = arrayOf("x", "y")

      fun create(property: Any): IntOffset {
        val value = findMethodByName("unbox-impl", property)?.invoke(property)
        if (value is Long) {
          val x = findMethodByName("getX-impl", property)?.invoke(null, value)
          val y = findMethodByName("getY-impl", property)?.invoke(null, value)
          if (x is Int && y is Int) return IntOffset(x, y)
        }
        throw ComposeParseException(IntOffset::class.simpleName)
      }
    }

    override fun create(property: Any): IntOffset = IntOffset.create(property)

    override fun createProperties(prefix: String): List<AnimatedPropertyItem> {
      return components.mapIndexed { index, component ->
        AnimatedPropertyItem(COMPONENT_NAMES[index], "$component", IntValidation, "Int")
      }
    }

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

  class ComposeParseException(className: String?) :
    IllegalArgumentException("Can't parse $className from library")

  class Dp(component: Float) :
    ComposeUnit<Float>(component, componentNames = arrayOf("dp")), AnimationUnit.NumberUnit<Float> {

    companion object {
      internal const val CLASS_NAME = "androidx.compose.ui.unit.Dp"

      fun create(property: Any): Dp {
        val value = findMethodByName("getValue", property)?.invoke(property)
        if (value is Float) return Dp(value)
        throw ComposeParseException(Dp::class.simpleName)
      }
    }

    override fun create(property: Any): Dp = Dp.create(property)

    override fun createProperties(prefix: String): List<AnimatedPropertyItem> {
      return listOf(AnimatedPropertyItem(prefix, "${components[0]}", DpValidation, "Float"))
    }

    override fun toString(): String = "${components[0]}dp"

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
    ComposeUnit<Float>(component1, component2, componentNames = arrayOf("width", "height")),
    AnimationUnit.NumberUnit<Float> {
    companion object {
      internal const val CLASS_NAME = "androidx.compose.ui.geometry.Size"
      val COMPONENT_NAMES = arrayOf("width", "height")

      fun create(property: Any): Size {
        val value = findMethodByName("unbox-impl", property)?.invoke(property)
        if (value is Long) {
          val width = findMethodByName("getWidth-impl", property)?.invoke(null, value)
          val height = findMethodByName("getHeight-impl", property)?.invoke(null, value)
          if (width is Float && height is Float) return Size(width, height)
        }
        throw ComposeParseException(Size::class.simpleName)
      }
    }

    override fun create(property: Any): Size = Size.create(property)

    override fun createProperties(prefix: String): List<AnimatedPropertyItem> {
      return components.mapIndexed { index, component ->
        AnimatedPropertyItem(COMPONENT_NAMES[index], "$component", FloatValidation, "Float")
      }
    }

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
    ComposeUnit<Float>(
      component1,
      component2,
      component3,
      component4,
      componentNames = arrayOf("left", "top", "right", "bottom"),
    ),
    AnimationUnit.NumberUnit<Float> {
    companion object {
      internal const val CLASS_NAME = "androidx.compose.ui.geometry.Rect"
      internal val COMPONENT_NAMES = arrayOf("left", "top", "right", "bottom")

      fun create(property: Any): Rect {
        val left = findMethodByName("getLeft", property)?.invoke(property)
        val top = findMethodByName("getTop", property)?.invoke(property)
        val right = findMethodByName("getRight", property)?.invoke(property)
        val bottom = findMethodByName("getBottom", property)?.invoke(property)
        if (left is Float && right is Float && top is Float && bottom is Float)
          return Rect(left, top, right, bottom)
        throw ComposeParseException(Rect::class.simpleName)
      }
    }

    override fun create(property: Any): Rect = Rect.create(property)

    override fun createProperties(prefix: String): List<AnimatedPropertyItem> {
      return components.mapIndexed { index, component ->
        AnimatedPropertyItem(COMPONENT_NAMES[index], "$component", FloatValidation, "Float")
      }
    }

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
    ComposeUnit<Float>(component1, component2, componentNames = arrayOf("x", "y")),
    AnimationUnit.NumberUnit<Float> {
    companion object {
      internal const val CLASS_NAME = "androidx.compose.ui.geometry.Offset"
      internal val COMPONENT_NAMES = arrayOf("x", "y")

      fun create(property: Any): Offset {
        val value = findMethodByName("unbox-impl", property)?.invoke(property)
        if (value is Long) {
          val x = findMethodByName("getX-impl", property)?.invoke(null, value)
          val y = findMethodByName("getY-impl", property)?.invoke(null, value)
          if (x is Float && y is Float) return Offset(x, y)
        }
        throw ComposeParseException(Offset::class.simpleName)
      }
    }

    override fun create(property: Any): Offset = Offset.create(property)

    override fun createProperties(prefix: String): List<AnimatedPropertyItem> {
      return components.mapIndexed { index, component ->
        AnimatedPropertyItem(COMPONENT_NAMES[index], "$component", FloatValidation, "Float")
      }
    }

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
    ComposeUnit<Float>(
      component1,
      component2,
      component3,
      component4,
      componentNames = arrayOf("red", "green", "blue", "alpha"),
    ),
    AnimationUnit.Color<Float, Color> {
    companion object {
      internal const val CLASS_NAME = "androidx.compose.ui.graphics.Color"
      internal val COMPONENT_NAMES = arrayOf("red", "green", "blue", "alpha")

      fun create(property: Any): Color {
        val value = findMethodByName("unbox-impl", property)?.invoke(property)
        if (value is Long) {
          val red = findMethodByName("getRed-impl", property)?.invoke(null, value)
          val green = findMethodByName("getGreen-impl", property)?.invoke(null, value)
          val blue = findMethodByName("getBlue-impl", property)?.invoke(null, value)
          val alpha = findMethodByName("getAlpha-impl", property)?.invoke(null, value)
          if (red is Float && green is Float && blue is Float && alpha is Float)
            return Color(red, green, blue, alpha)
        }
        throw ComposeParseException(Color::class.simpleName)
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

    override fun create(property: Any): Color = Color.create(property)

    override fun create(color: java.awt.Color): Color = Color.create(color)

    override fun createProperties(prefix: String): List<AnimatedPropertyItem> {
      return components.mapIndexed { index, component ->
        AnimatedPropertyItem(COMPONENT_NAMES[index], "$component", FloatValidation, "Color")
      }
    }

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
      is ComposeUnit -> this.createProperties(prefix)
      is AnimationUnit.StringUnit ->
        listOf(AnimatedPropertyItem(prefix, components[0], { EDITOR_NO_ERROR }, "String"))
      is AnimationUnit.IntUnit ->
        listOf(AnimatedPropertyItem(prefix, "${components[0]}", IntValidation, "Int"))
      is AnimationUnit.DoubleUnit ->
        listOf(AnimatedPropertyItem(prefix, "${components[0]}", DoubleValidation, "Double"))
      is AnimationUnit.FloatUnit ->
        listOf(AnimatedPropertyItem(prefix, "${components[0]}", FloatValidation, "Float"))
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

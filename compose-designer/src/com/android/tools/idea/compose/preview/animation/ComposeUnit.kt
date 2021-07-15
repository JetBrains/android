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
import java.lang.reflect.Method
import java.math.RoundingMode
import java.text.DecimalFormat

/**
 * Compose units represented as multi-dimensional properties.
 */
object ComposeUnit {

  class TimelineUnit(val property: ComposeAnimatedProperty, val unit: Unit<*>?)

  /** Multi-dimensional property with each dimension of the type [A]. */
  interface Unit<A> where A : Number, A : Comparable<A> {
    val components: List<A>
    fun toString(componentId: Int): String

    /** Transforms a component to a [Double]. It unifies painting of the curves in [CurvePainter]. */
    fun componentAsDouble(componentId: Int) = components[componentId].toDouble()
  }

  abstract class Unit1D<A>(val component1: A) :
    Unit<A> where A : Number, A : Comparable<A> {
    override val components = listOf(component1)
    override fun toString(componentId: Int): String = component1.toString()
  }

  abstract class Unit2D<A>(val component1: A, val component2: A) :
    Unit<A> where A : Number, A : Comparable<A> {
    override val components = listOf(component1, component2)
    override fun toString(componentId: Int): String = "( " +
                                                      "${if (componentId == 0) component1 else "_"} , " +
                                                      "${if (componentId == 1) component2 else "_"} )"

  }

  abstract class Unit3D<A>(val component1: A, val component2: A, val component3: A) :
    Unit<A> where A : Number, A : Comparable<A> {
    override val components = listOf(component1, component2, component3)
    override fun toString(componentId: Int): String = "( " +
                                                      "${if (componentId == 0) component1 else "_"} , " +
                                                      "${if (componentId == 1) component2 else "_"} , " +
                                                      "${if (componentId == 2) component3 else "_"} )"

  }

  abstract class Unit4D<A>(val component1: A, val component2: A, val component3: A, val component4: A) :
    Unit<A> where A : Number, A : Comparable<A> {
    override val components = listOf(component1, component2, component3, component4)
    override fun toString(componentId: Int): String = "( " +
                                                      "${if (componentId == 0) component1 else "_"} , " +
                                                      "${if (componentId == 1) component2 else "_"} , " +
                                                      "${if (componentId == 2) component3 else "_"} , " +
                                                      "${if (componentId == 3) component4 else "_"} )"
  }

  /**
   * Parses and creates a [Unit1D] from [ComposeAnimatedProperty.value].
   * @return a property which could 1, 2, 3 or 4 - dimensional property - [Unit1D], [Unit2D], [Unit3D], [Unit4D] respectively.
   */
  fun parse(property: ComposeAnimatedProperty): Unit<*>? =
    when (property.value.javaClass.kotlin.qualifiedName) {
      Color.CLASS_NAME -> Color.create(property.value)
      Dp.CLASS_NAME -> Dp.create(property.value)
      Size.CLASS_NAME -> Size.create(property.value)
      Rect.CLASS_NAME -> Rect.create(property.value)
      IntOffset.CLASS_NAME -> IntOffset.create(property.value)
      IntSize.CLASS_NAME -> IntSize.create(property.value)
      Offset.CLASS_NAME -> Offset.create(property.value)
      "kotlin.Int" -> if (property.value is Int) object : Unit1D<Int>(property.value as Int) {} else null
      "kotlin.Double" -> if (property.value is Double) object : Unit1D<Double>(property.value as Double) {} else null
      "kotlin.Float" -> if (property.value is Float) object : Unit1D<Float>(property.value as Float) {} else null
      else -> null
    }

  class IntSize(component1: Int, component2: Int) : Unit2D<Int>(component1, component2) {
    companion object {
      const val CLASS_NAME = "androidx.compose.ui.unit.IntSize"
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
  }

  class IntOffset(component1: Int, component2: Int) : Unit2D<Int>(component1, component2) {
    companion object {
      const val CLASS_NAME = "androidx.compose.ui.unit.IntOffset"
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
  }

  class Dp(component1: Float) : Unit1D<Float>(component1) {
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

    override fun toString(componentId: Int): String = "${component1}dp"
  }

  class Size(component1: Float, component2: Float) : Unit2D<Float>(component1, component2) {
    companion object {
      const val CLASS_NAME = "androidx.compose.ui.geometry.Size"
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
  }

  class Rect(
    component1: Float,
    component2: Float,
    component3: Float,
    component4: Float
  ) : Unit4D<Float>(component1, component2, component3, component4) {
    companion object {
      const val CLASS_NAME = "androidx.compose.ui.geometry.Rect"
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
  }

  class Offset(component1: Float, component2: Float) : Unit2D<Float>(component1, component2) {
    companion object {
      const val CLASS_NAME = "androidx.compose.ui.geometry.Offset"
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
  }

  class Color(
    component1: Float,
    component2: Float,
    component3: Float,
    component4: Float
  ) : Unit4D<Float>(component1, component2, component3, component4) {
    companion object {
      const val CLASS_NAME = "androidx.compose.ui.graphics.Color"
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
    }
  }

  private fun findMethodByName(methodName: String, property: Any): Method? {
    return property::class.java.methods.singleOrNull { it.name == methodName }
      ?.apply { this.isAccessible = true }
  }
}
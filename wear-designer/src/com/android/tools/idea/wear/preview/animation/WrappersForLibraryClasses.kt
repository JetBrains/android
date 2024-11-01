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
package com.android.tools.idea.wear.preview.animation

import java.lang.reflect.Method

const val DYNAMIC_TYPE_ANIMATOR_CLASS =
  "androidx.wear.protolayout.expression.pipeline.DynamicTypeAnimator"

/**
 * Represents an animation in the Wear preview.
 *
 * All methods should be invoked on Render thread!
 *
 * @param animator The DynamicTypeAnimator object.
 */
class ProtoAnimation(private val animator: Any) {

  /** The type of the animation. */
  enum class TYPE {
    /** An integer animation. */
    INT,

    /** A floating-point animation. */
    FLOAT,

    /** A color animation. */
    COLOR,

    /** An unknown animation type. */
    UNKNOWN,
  }

  init {
    val protoAnimatorInterface =
      animator.javaClass.classLoader?.loadClass(DYNAMIC_TYPE_ANIMATOR_CLASS)
    require(protoAnimatorInterface != null && protoAnimatorInterface.isInstance(animator)) {
      "Animator must implement DynamicTypeAnimator interface"
    }
  }

  private fun getTypeEvaluator(): Any? = delegateMethodCall("getTypeEvaluator")

  fun setFloatValues(vararg values: Float) = delegateMethodCall("setFloatValues", values)

  fun setIntValues(vararg values: Int) = delegateMethodCall("setIntValues", values)

  /**
   * Sets the current time of the animation.
   *
   * @param newValue The new time in milliseconds.
   */
  fun setTime(newTime: Long) = delegateMethodCall("advanceToAnimationTime", newTime)

  /** The most recent value calculated by this ValueAnimator */
  val value: Any?
    get() = delegateMethodCall("getCurrentValue")

  /** The start value of the animation */
  val startValueInt: Int?
    get() = delegateMethodCall("getStartValue") as? Int

  /** The end value of the animation */
  val endValueInt: Int?
    get() = delegateMethodCall("getEndValue") as? Int

  /** The start value of the animation */
  val startValueFloat: Float?
    get() = delegateMethodCall("getStartValue") as? Float

  /** The end value of the animation */
  val endValueFloat: Float?
    get() = delegateMethodCall("getEndValue") as? Float

  /** The duration of the animation in milliseconds. */
  val durationMs: Long
    get() = delegateMethodCall("getDurationMs") as Long

  val startDelayMs: Long
    get() = delegateMethodCall("getStartDelayMs") as Long

  val isTerminal: Boolean
    get() = delegateMethodCall("isTerminal") as Boolean

  private fun delegateMethodCall(methodName: String, vararg args: Any?): Any? {
    val method: Method =
      animator.javaClass.getMethod(
        methodName,
        *args
          .map {
            when (it) {
              is Int -> Int::class.javaPrimitiveType
              is Long -> Long::class.javaPrimitiveType
              is Float -> Float::class.javaPrimitiveType
              else -> it?.javaClass
            }
          }
          .toTypedArray(),
      )
    method.isAccessible = true
    return method.invoke(animator, *args)
  }

  /** The name of the animation. */
  val name: String
    get() = "$type Animation"

  /** The type of the animation. */
  val type: TYPE by lazy {
    val evaluatorClass = getTypeEvaluator() ?: return@lazy TYPE.UNKNOWN
    when (evaluatorClass::class.simpleName) {
      "ArgbEvaluator" -> TYPE.COLOR
      "IntEvaluator" -> TYPE.INT
      "FloatEvaluator" -> TYPE.FLOAT
      else -> TYPE.UNKNOWN
    }
  }
}

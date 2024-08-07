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
import java.util.logging.Level
import java.util.logging.Logger

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
  fun setTime(newTime: Long) = delegateMethodCall("setAnimationFrameTime", newTime)

  private fun getPropertyValuesHolder(): Any? =
    (delegateMethodCall("getPropertyValuesHolders") as? Array<*>)?.getOrNull(0)

  /** The most recent value calculated by this ValueAnimator */
  val value: Any?
    get() = delegateMethodCall("getLastAnimatedValue")

  /** The duration of the animation in milliseconds. */
  val durationMs: Long
    get() = delegateMethodCall("getDurationMs") as Long

  val startDelayMs: Long
    get() = delegateMethodCall("getStartDelayMs") as Long

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

  private fun getStartAndEndValues(): Pair<Any?, Any?> {
    return getPropertyValuesHolder()?.let { getStartAndEndValues(it) } ?: Pair(null, null)
  }

  fun getInts(): Pair<Int, Int> {
    val (start, end) = getStartAndEndValues()
    return ((start as? Int) ?: 0) to ((end as? Int) ?: 0)
  }

  fun getFloats(): Pair<Float, Float> {
    val (start, end) = getStartAndEndValues()
    return ((start as? Float) ?: 0f) to ((end as? Float) ?: 0f)
  }

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

/** Returns the start and end values of a PropertyValuesHolder object. */
private fun getStartAndEndValues(propertyValuesHolder: Any): Pair<Any?, Any?> {
  // Check if it's a subclass of PropertyValuesHolder
  val propertyValuesHolderClass = Class.forName("android.animation.PropertyValuesHolder")
  if (!propertyValuesHolderClass.isAssignableFrom(propertyValuesHolder.javaClass)) {
    throw IllegalArgumentException(
      "propertyValuesHolder must be an instance of PropertyValuesHolder"
    )
  }
  return try {
    // 1. Get Keyframes using reflection
    val keyframesField = propertyValuesHolderClass.getDeclaredField("mKeyframes")
    keyframesField.isAccessible = true
    val keyframes = keyframesField.get(propertyValuesHolder)

    // 2. Get Keyframes List
    val keyframesList =
      keyframes::class
        .java
        .getMethod("getKeyframes")
        .also { it.isAccessible = true }
        .invoke(keyframes) as List<Any>

    // 3. Extract Start and End Values
    val startValue =
      keyframesList[0]::class
        .java
        .getMethod("getValue")
        .also { it.isAccessible = true }
        .invoke(keyframesList[0])
    val endValue =
      keyframesList[keyframesList.size - 1]::class
        .java
        .getMethod("getValue")
        .also { it.isAccessible = true }
        .invoke(keyframesList[keyframesList.size - 1])

    startValue to endValue
  } catch (e: Exception) {
    Logger.getLogger("PropertyValuesHolder")
      .log(Level.WARNING, "Can't get start and end values for Wear TILE ANIMATION", e)
    Pair(null, null)
  }
}

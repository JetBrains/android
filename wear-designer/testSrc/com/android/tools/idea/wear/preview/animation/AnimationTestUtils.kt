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

import androidx.wear.protolayout.expression.pipeline.DynamicTypeAnimator
import org.jetbrains.android.dom.animator.PropertyValuesHolder

class TestDynamicTypeAnimator(private val duration: Long = 100, private val startDelay: Long = 10) :
  DynamicTypeAnimator {
  override var typeEvaluator: DynamicTypeAnimator.TypeEvaluator<*> =
    object : DynamicTypeAnimator.TypeEvaluator<Any> {
      override fun evaluate(fraction: Float, startValue: Any, endValue: Any): Any {
        return if (fraction == 0f) startValue else endValue
      }
    }

  private var animationFrameTime: Long = 0

  private var _floatValues: FloatArray = FloatArray(2)

  override fun setFloatValues(vararg values: Float) {
    _floatValues = values
  }

  fun getFloatValues(): FloatArray {
    return _floatValues.copyOf() // Return a copy to prevent external modification
  }

  private var _intValues: IntArray = IntArray(2)

  override fun setIntValues(vararg values: Int) {
    _intValues = values
  }

  fun getIntValues(): IntArray {
    return _intValues.copyOf() // Return a copy to prevent external modification
  }

  override fun advanceToAnimationTime(newTime: Long) {
    animationFrameTime = newTime
  }

  override fun getPropertyValuesHolders(): Array<PropertyValuesHolder?>? {
    return null
  }

  private var _lastAnimatedValue: Any? = null

  fun setLastAnimatedValue(newValue: Any?) {
    _lastAnimatedValue = newValue
  }

  override fun getLastAnimatedValue(): Any? {
    return _lastAnimatedValue
  }

  override fun getDurationMs(): Long {
    return duration
  }

  override fun getStartDelayMs(): Long {
    return startDelay
  }
}

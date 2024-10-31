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

class TestDynamicTypeAnimator(type: ProtoAnimation.TYPE = ProtoAnimation.TYPE.FLOAT) :
  DynamicTypeAnimator {
  class FloatEvaluator : DynamicTypeAnimator.TypeEvaluator<Float> {
    override fun evaluate(fraction: Float, startValue: Float, endValue: Float): Float {
      return if (fraction == 0f) startValue else endValue
    }
  }

  class IntEvaluator : DynamicTypeAnimator.TypeEvaluator<Int> {
    override fun evaluate(fraction: Float, startValue: Int, endValue: Int): Int {
      return if (fraction == 0f) startValue else endValue
    }
  }

  class ArgbEvaluator : DynamicTypeAnimator.TypeEvaluator<Int> {
    override fun evaluate(fraction: Float, startValue: Int, endValue: Int): Int {
      return if (fraction == 0f) startValue else endValue
    }
  }

  class Unknowm : DynamicTypeAnimator.TypeEvaluator<Int> {
    override fun evaluate(fraction: Float, startValue: Int, endValue: Int): Int {
      return if (fraction == 0f) startValue else endValue
    }
  }

  var isTerminalInternal: Boolean = true
  var duration: Long = 100
  var startDelay: Long = 10

  override var typeEvaluator =
    when (type) {
      ProtoAnimation.TYPE.FLOAT -> FloatEvaluator()
      ProtoAnimation.TYPE.INT -> IntEvaluator()
      ProtoAnimation.TYPE.COLOR -> ArgbEvaluator()
      else -> Unknowm()
    }

  private var _startValue: Any? = null

  private var _endValue: Any? = null

  var currentTime: Long = 0

  private var _floatValues: FloatArray = FloatArray(2)

  override fun setFloatValues(vararg values: Float) {
    _floatValues = values
    _startValue = values[0]
    _endValue = values[1]
  }

  fun getFloatValues(): FloatArray {
    return _floatValues.copyOf() // Return a copy to prevent external modification
  }

  private var _intValues: IntArray = IntArray(2)

  override fun setIntValues(vararg values: Int) {
    _intValues = values
    _startValue = values[0]
    _endValue = values[1]
  }

  fun getIntValues(): IntArray {
    return _intValues.copyOf() // Return a copy to prevent external modification
  }

  override fun advanceToAnimationTime(newTime: Long) {
    currentTime = newTime
  }

  override fun getStartValue(): Any? {
    return _startValue
  }

  override fun getEndValue(): Any? {
    return _endValue
  }

  private var _lastCurrentValue: Any? = null

  fun setCurrentValue(newValue: Any?) {
    _lastCurrentValue = newValue
  }

  override fun getCurrentValue(): Any? {
    return _lastCurrentValue ?: _startValue
  }

  override fun getDurationMs(): Long {
    return duration
  }

  override fun getStartDelayMs(): Long {
    return startDelay
  }

  override fun isTerminal(): Boolean {
    return isTerminalInternal
  }
}

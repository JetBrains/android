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

class TestDynamicTypeAnimator : DynamicTypeAnimator {
  override var typeEvaluator: DynamicTypeAnimator.TypeEvaluator<*> =
    object : DynamicTypeAnimator.TypeEvaluator<Any> {
      override fun evaluate(fraction: Float, startValue: Any, endValue: Any): Any {
        return if (fraction == 0f) startValue else endValue
      }
    }
  private var floatValues: FloatArray? = null
  private var intValues: IntArray? = null
  private var animationFrameTime: Long = 0
  override var propertyValuesHolders: Array<PropertyValuesHolder?>? = null
  override var lastAnimatedValue: Any? = null
  override var duration: Long = 0
  override var startDelay: Long = 0

  override fun setFloatValues(vararg values: Float) {
    floatValues = values
  }

  override fun setIntValues(vararg values: Int) {
    intValues = values
  }

  override fun setAnimationFrameTime(newTime: Long) {
    animationFrameTime = newTime
  }
}

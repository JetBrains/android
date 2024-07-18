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

const val DYNAMIC_TYPE_ANIMATOR_CLASS = "androidx.wear.protolayout.expression.pipeline.DynamicTypeAnimator"

/**
 * Represents an animation in the Wear preview.
 *
 * All methods should be invoked on Render thread!
 *
 * @param animator The DynamicTypeAnimator object.
 */
class ProtoAnimation(private val animator: Any) {
  init {
    val protoAnimatorInterface = animator.javaClass.classLoader?.loadClass(DYNAMIC_TYPE_ANIMATOR_CLASS)
    require(protoAnimatorInterface != null && protoAnimatorInterface.isInstance(animator)) {
      "Animator must implement DynamicTypeAnimator interface"
    }
  }
}

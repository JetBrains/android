/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.preview.animation.timeline

/** State of Animation shared between the [TimelineElement]s in timeline. */
data class ElementState(
  /** The offset in ms for which the animation is shifted. */
  val valueOffset: Int = 0,
  /** If element is frozen in specified [frozenValue]. */
  val frozen: Boolean = false,
  /** The value in ms in which the animation is frozen. */
  val frozenValue: Int = 0,
  val expanded: Boolean = false,
)

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
package com.android.tools.idea.compose.preview.animation

/**
 * Animation transition for selected from/to states.
 *
 * @param properties
 * - map of properties for this Animation, it maps the index of the property to an
 *   [AnimatedProperty].
 */
data class Transition(val properties: Map<Int, AnimatedProperty<Double>?> = mutableMapOf()) {
  val startMillis = properties.values.filterNotNull().minOfOrNull { it.startMs }
  val endMillis = properties.values.filterNotNull().maxOfOrNull { it.endMs }
  val duration = if (startMillis != null) endMillis?.minus(startMillis) else 0
}

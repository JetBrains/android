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

import androidx.compose.animation.tooling.ComposeAnimation
import androidx.compose.animation.tooling.ComposeAnimationType
import com.android.tools.idea.compose.preview.message

/**
 * Tab titles mapped to the amount of tabs using that title. The count is used to differentiate tabs
 * when there are multiple tabs with the same name. For example, we should have "tabTitle",
 * "tabTitle (1)", "tabTitle (2)", etc., instead of multiple "tabTitle" tabs.
 */
class TabNamesGenerator {

  private val tabNamesCount = HashMap<String, Int>()

  fun createName(animation: ComposeAnimation): String {
    val tabName =
      animation.label
        ?: when (animation.type) {
          ComposeAnimationType.ANIMATED_VALUE ->
            message("animation.inspector.tab.animated.value.default.title")
          ComposeAnimationType.ANIMATED_VISIBILITY ->
            message("animation.inspector.tab.animated.visibility.default.title")
          ComposeAnimationType.TRANSITION_ANIMATION ->
            message("animation.inspector.tab.transition.animation.default.title")
          ComposeAnimationType.ANIMATABLE ->
            message("animation.inspector.tab.animatable.default.title")
          ComposeAnimationType.ANIMATE_CONTENT_SIZE ->
            message("animation.inspector.tab.animate.content.size.default.title")
          ComposeAnimationType.ANIMATE_X_AS_STATE ->
            message("animation.inspector.tab.animate.as.state.default.title")
          ComposeAnimationType.ANIMATED_CONTENT ->
            message("animation.inspector.tab.animated.content.default.title")
          ComposeAnimationType.DECAY_ANIMATION ->
            message("animation.inspector.tab.decay.animation.default.title")
          ComposeAnimationType.INFINITE_TRANSITION ->
            message("animation.inspector.tab.infinite.animation.default.title")
          ComposeAnimationType.TARGET_BASED_ANIMATION ->
            message("animation.inspector.tab.target.based.animation.default.title")
          ComposeAnimationType.UNSUPPORTED ->
            message("animation.inspector.tab.unsupported.default.title")
        }
    val count = tabNamesCount.getOrDefault(tabName, 0)
    tabNamesCount[tabName] = count + 1
    return "$tabName${if (count > 0) " ($count)" else ""}"
  }

  fun clear() {
    tabNamesCount.clear()
  }
}

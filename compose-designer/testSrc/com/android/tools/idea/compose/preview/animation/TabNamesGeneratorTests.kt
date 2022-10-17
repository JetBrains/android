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

import androidx.compose.animation.tooling.ComposeAnimationType
import com.android.tools.idea.compose.preview.animation.TestUtils.createComposeAnimation
import org.junit.Test
import kotlin.test.assertEquals


class TabNamesGeneratorTests {

  @Test
  fun `create labels for animations`() {
    val names = TabNamesGenerator()
    var label = names.createName(createComposeAnimation("Label"))
    assertEquals("Label", label)
    label = names.createName(createComposeAnimation("Label"))
    assertEquals("Label (1)", label)
    label = names.createName(createComposeAnimation("Label"))
    assertEquals("Label (2)", label)
    label = names.createName(createComposeAnimation("Label"))
    assertEquals("Label (3)", label)
    // After clear, labels table should reset.
    names.clear()
    label = names.createName(createComposeAnimation("Label"))
    assertEquals("Label", label)
    label = names.createName(createComposeAnimation("Label"))
    assertEquals("Label (1)", label)
    // No number if labels are not yet in the table.
    label = names.createName(createComposeAnimation("Another label"))
    assertEquals("Another label", label)
    label = names.createName(createComposeAnimation("One more label"))
    assertEquals("One more label", label)
    label = names.createName(createComposeAnimation(null, ComposeAnimationType.UNSUPPORTED))
    assertEquals("Animation", label)
    label = names.createName(createComposeAnimation(null, ComposeAnimationType.TRANSITION_ANIMATION))
    assertEquals("Transition Animation", label)
    label = names.createName(createComposeAnimation(null, ComposeAnimationType.ANIMATED_VALUE))
    assertEquals("Animated Value", label)
    label = names.createName(createComposeAnimation(null, ComposeAnimationType.ANIMATED_VISIBILITY))
    assertEquals("Animated Visibility", label)
  }
}
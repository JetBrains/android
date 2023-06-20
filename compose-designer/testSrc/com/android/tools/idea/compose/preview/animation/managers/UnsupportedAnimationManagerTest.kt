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
package com.android.tools.idea.compose.preview.animation.managers

import com.android.tools.idea.compose.preview.animation.LabelCard
import com.android.tools.idea.compose.preview.animation.TestUtils
import com.android.tools.idea.compose.preview.animation.timeline.UnsupportedLabel
import com.intellij.testFramework.assertInstanceOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

class UnsupportedAnimationManagerTest {

  val animation = TestUtils.createComposeAnimation("Label")

  @Test
  fun `default states`() {
    val manager = UnsupportedAnimationManager(animation, "Label")
    assertNotNull(manager.elementState)
    assertNotNull(manager.card)
    assertInstanceOf<LabelCard>(manager.card)
    assertNull(manager.timelineMaximumMs)
    manager.loadProperties()
  }

  @Test
  fun `setup callback is called`() {
    val manager = UnsupportedAnimationManager(animation, "Label")
    var callback = 0
    manager.setup { callback++ }
    assertEquals(1, callback)
  }

  @Test
  fun `create timeline element`() {
    val manager = UnsupportedAnimationManager(animation, "Label")
    val slider = TestUtils.createTestSlider()
    assertInstanceOf<UnsupportedLabel>(
      manager.createTimelineElement(slider, 0, slider.sliderUI.positionProxy)
    )
  }
}

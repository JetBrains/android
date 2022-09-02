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

import javax.swing.JSlider
import kotlin.test.assertTrue
import org.junit.Test

class SliderClockControlTest {

  private val clockControl =
    SliderClockControl(
      JSlider().apply {
        minimum = 10
        maximum = 100
      }
    )

  @Test
  fun `jump to start`() {
    clockControl.jumpToStart()
    assertTrue { clockControl.isAtStart() }
  }

  @Test
  fun `jump to end`() {
    clockControl.jumpToEnd()
    assertTrue { clockControl.isAtEnd() }
  }

  @Test
  fun `increment clock`() {
    clockControl.updateMaxDuration(50)
    clockControl.jumpToStart()
    clockControl.incrementClockBy(40)
    assertTrue { clockControl.isAtEnd() }
    clockControl.incrementClockBy(-40)
    assertTrue { clockControl.isAtStart() }
  }

  @Test
  fun `increment clock by large value`() {
    clockControl.updateMaxDuration(100)
    clockControl.jumpToStart()
    // Increment by large value, but the actual value will not go above max e.g 100.
    clockControl.incrementClockBy(3456)
    assertTrue { clockControl.isAtEnd() }
    clockControl.incrementClockBy(-90)
    assertTrue { clockControl.isAtStart() }
  }

  @Test
  fun `decrement clock by large value`() {
    clockControl.updateMaxDuration(100)
    clockControl.jumpToEnd()
    // Decrement by large value, but the actual value will not go below min e.g 10
    clockControl.incrementClockBy(-3456)
    assertTrue { clockControl.isAtStart() }
    clockControl.incrementClockBy(90)
    assertTrue { clockControl.isAtEnd() }
  }

  @Test
  fun `change speed x2`() {
    clockControl.jumpToStart()
    clockControl.speed = PlaybackControls.TimelineSpeed.X_2
    clockControl.incrementClockBy(50)
    assertTrue { clockControl.isAtEnd() }
  }

  @Test
  fun `change speed x0_1`() {
    clockControl.jumpToStart()
    clockControl.speed = PlaybackControls.TimelineSpeed.X_0_1
    clockControl.incrementClockBy(1000)
    assertTrue { clockControl.isAtEnd() }
  }

  @Test
  fun `change max while at the end`() {
    clockControl.updateMaxDuration(500)
    clockControl.jumpToEnd() // Clock at 500
    clockControl.updateMaxDuration(100) // Clock at 100
    clockControl.incrementClockBy(-90) // Clock at 10 (minimum)
    assertTrue { clockControl.isAtStart() }
  }
}

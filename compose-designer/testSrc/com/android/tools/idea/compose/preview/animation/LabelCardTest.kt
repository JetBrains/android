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

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.compose.preview.animation.timeline.ElementState
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import java.awt.Dimension
import kotlin.test.assertTrue
import org.junit.Test

class LabelCardTest {

  private val minimumSize = Dimension(10, 10)

  @Test
  fun `create animation card`(): Unit {
    val card = LabelCard(ElementState("Title"))
    card.component.setSize(100, card.getCurrentHeight())
    card.setDuration(0)

    assertTrue { card.getCurrentHeight() > minimumSize.height }

    invokeAndWaitIfNeeded {
      val ui = FakeUi(card.component)
      ui.layout()

      // Transition name label.
      card.component.components[0].also {
        assertTrue(it.isVisible)
        TestUtils.assertBigger(minimumSize, it.size)
      }
      // Uncomment to preview
      // ui.render()
    }
  }
}

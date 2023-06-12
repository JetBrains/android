/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.compose.preview.animation.TestUtils.findAllCards
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class UnsupportedManagerTests(private val animationType: ComposeAnimationType) : InspectorTests() {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun parameters() =
      listOf(
        arrayOf(ComposeAnimationType.ANIMATED_VALUE),
        arrayOf(ComposeAnimationType.ANIMATABLE),
        arrayOf(ComposeAnimationType.ANIMATE_CONTENT_SIZE),
        arrayOf(ComposeAnimationType.DECAY_ANIMATION),
        arrayOf(ComposeAnimationType.TARGET_BASED_ANIMATION),
        arrayOf(ComposeAnimationType.UNSUPPORTED)
      )
  }

  @Test
  fun unsupportedAnimationInspector() {
    val inspector = createAndOpenInspector()

    val animation =
      object : ComposeAnimation {
        override val animationObject = Any()
        override val type = animationType
        override val states = emptySet<Any>()
      }

    val clock = TestClockWithCoordination()

    ComposePreviewAnimationManager.onAnimationSubscribed(clock, animation)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    invokeAndWaitIfNeeded {
      val ui = FakeUi(inspector.component.apply { size = Dimension(500, 400) })
      ui.updateToolbars()
      ui.layoutAndDispatchEvents()
      val cards = findAllCards(inspector.component)
      assertEquals(1, cards.size)
      assertTrue(cards.first() is LabelCard)
    }
  }
}

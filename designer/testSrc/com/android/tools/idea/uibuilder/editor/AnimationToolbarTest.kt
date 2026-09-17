/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.editor

import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.testing.disposable
import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.runInEdtAndGet
import javax.swing.JButton
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock

class AnimationToolbarTest {
  @JvmField @Rule val projectRule = ProjectRule()

  @Test
  fun testControlFunctions() {
    val toolbar = runInEdtAndGet {
      AnimationToolbar.createUnlimitedAnimationToolbar(projectRule.disposable, EMPTY_ANIMATION_LISTENER, 10L, 0L)
    }
    val listener = mock<AnimationControllerListener>()
    toolbar.registerAnimationControllerListener(listener)

    // We use callback to test only.

    toolbar.play()
    Mockito.verify(listener).onPlayStatusChanged(PlayStatus.PLAY)

    toolbar.pause()
    Mockito.verify(listener).onPlayStatusChanged(PlayStatus.PAUSE)

    toolbar.play()
    Mockito.verify(listener, Mockito.times(2)).onPlayStatusChanged(PlayStatus.PLAY)

    toolbar.stop()
    Mockito.verify(listener).onPlayStatusChanged(PlayStatus.STOP)
  }

  @Test
  fun testAnimationComplete() {
    // Set animation length as 2 second.
    val toolbar = runInEdtAndGet {
      AnimationToolbar.createAnimationToolbar(projectRule.disposable, EMPTY_ANIMATION_LISTENER, 10L, 0L, 2 * 1000L)
    }
    toolbar.setLooping(false)
    val listener = mock<AnimationControllerListener>()
    toolbar.registerAnimationControllerListener(listener)

    toolbar.play()
    Mockito.verify(listener).onPlayStatusChanged(PlayStatus.PLAY)

    // Wait for animation complete. Wait for 5 seconds in case of race condition.
    Mockito.verify(listener, Mockito.after(5 * 1000L)).onPlayStatusChanged(PlayStatus.COMPLETE)
  }

  @Test
  fun testDisabledIconSet() {
    val toolbar = runInEdtAndGet {
      // Needed so the actual icons are loaded and the disabled vs non-disabled verification can be done.
      IconLoader.activate()
      AnimationToolbar.createUnlimitedAnimationToolbar(projectRule.disposable, EMPTY_ANIMATION_LISTENER, 10L, 0L)
    }

    val buttons = TreeWalker(toolbar).descendants().filterIsInstance<JButton>()
    assertNotEquals(0, buttons.size)
    for (button in buttons) {
      assertNotNull("Disabled icon should be set for button ${button.name}", button.disabledIcon)
      assertNotEquals("Disabled icon should be different from regular icon for button ${button.name}", button.icon, button.disabledIcon)
    }
  }
}

private val EMPTY_ANIMATION_LISTENER =
  object : AnimationListener {
    override fun animateTo(controller: AnimationController, framePositionMs: Long) = Unit
  }

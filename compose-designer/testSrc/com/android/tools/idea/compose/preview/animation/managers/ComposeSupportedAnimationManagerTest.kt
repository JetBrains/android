/*
 * Copyright (C) 2025 The Android Open Source Project
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

import androidx.compose.animation.tooling.ComposeAnimatedProperty
import androidx.compose.animation.tooling.ComposeAnimation
import androidx.compose.animation.tooling.ComposeAnimationType
import com.android.tools.idea.compose.preview.animation.AnimationClock
import com.android.tools.idea.compose.preview.animation.NoopComposeAnimationTracker
import com.android.tools.idea.compose.preview.animation.TestClock
import com.android.tools.idea.compose.preview.animation.state.EmptyState
import com.android.tools.idea.preview.animation.AnimationTabs
import com.android.tools.idea.preview.animation.AnimationUnit
import com.android.tools.idea.preview.animation.PlaybackControls
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

internal class ComposeSupportedAnimationManagerTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  val animation =
    object : ComposeAnimation {
      override val animationObject = Any()
      override val type = ComposeAnimationType.TRANSITION_ANIMATION
      override val states = setOf(1, 2)
    }

  val testClock =
    object : TestClock() {
      override fun getAnimatedProperties(animation: Any): List<ComposeAnimatedProperty> {
        return listOf(
          ComposeAnimatedProperty("Int", 1),
          ComposeAnimatedProperty("Float", 1f),
          ComposeAnimatedProperty("Double", 1.0),
        )
      }
    }

  @Test
  fun animatedPropertiesAtCurrentTimeAreLoaded() = runTest {
    val manager = createManager(backgroundScope)
    manager.loadAnimatedPropertiesAtCurrentTime(false)
    assertEquals(3, manager.animatedPropertiesAtCurrentTime.size)
    manager.animatedPropertiesAtCurrentTime[0].let {
      assertEquals("Int", it.propertyLabel)
      assertTrue { it.unit is AnimationUnit.IntUnit }
      assertEquals(listOf(1), it.unit.components)
    }
    manager.animatedPropertiesAtCurrentTime[1].let {
      assertEquals("Float", it.propertyLabel)
      assertTrue { it.unit is AnimationUnit.FloatUnit }
      assertEquals(listOf(1f), it.unit.components)
    }
    manager.animatedPropertiesAtCurrentTime[2].let {
      assertEquals("Double", it.propertyLabel)
      assertTrue { it.unit is AnimationUnit.DoubleUnit }
      assertEquals(listOf(1.0), it.unit.components)
    }
  }

  private fun createManager(scope: CoroutineScope): ComposeSupportedAnimationManager {
    return object :
      ComposeSupportedAnimationManager(
        animation = animation,
        tabTitle = "Title",
        tracker = NoopComposeAnimationTracker,
        animationClock = AnimationClock(testClock),
        maxDurationPerIteration = MutableStateFlow(100L),
        getCurrentTime = { 0 },
        executeInRenderSession = { _, _, job -> job() },
        tabbedPane = AnimationTabs(projectRule.project, projectRule.testRootDisposable),
        rootComponent = JPanel(),
        playbackControls = mock<PlaybackControls>(),
        updateTimelineElementsCallback = {},
        parentScope = scope,
      ) {

      override val animationState = EmptyState()

      override suspend fun syncAnimationWithState() {}

      override suspend fun setupInitialAnimationState() {}
    }
  }
}

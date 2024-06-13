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
package com.android.tools.idea.uibuilder.analytics

import com.android.tools.idea.uibuilder.editor.AnimationToolbarAction
import com.android.tools.idea.uibuilder.editor.AnimationToolbarType
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AnimationPreviewEvent
import com.google.wireless.android.sdk.stats.LayoutEditorEvent
import java.util.function.Consumer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AnimationToolbarAnalyticsManagerTest {

  @Test
  fun testLimitedLog() {
    val logger = TestLogger()
    val manager = AnimationToolbarAnalyticsManager(logger, false)

    manager.trackAction(AnimationToolbarType.LIMITED, AnimationToolbarAction.PLAY)
    manager.trackAction(AnimationToolbarType.LIMITED, AnimationToolbarAction.STOP)
    manager.trackAction(AnimationToolbarType.LIMITED, AnimationToolbarAction.FRAME_FORWARD)
    manager.trackAction(AnimationToolbarType.LIMITED, AnimationToolbarAction.FRAME_BACKWARD)
    manager.trackAction(AnimationToolbarType.LIMITED, AnimationToolbarAction.PAUSE)
    manager.trackAction(AnimationToolbarType.LIMITED, AnimationToolbarAction.FRAME_CONTROL)

    assertLog(
      logger.logs[0],
      AnimationPreviewEvent.ToolbarType.LIMITED_ANIMATION,
      AnimationPreviewEvent.UserAction.PLAY,
    )
    assertLog(
      logger.logs[1],
      AnimationPreviewEvent.ToolbarType.LIMITED_ANIMATION,
      AnimationPreviewEvent.UserAction.STOP,
    )
    assertLog(
      logger.logs[2],
      AnimationPreviewEvent.ToolbarType.LIMITED_ANIMATION,
      AnimationPreviewEvent.UserAction.FRAME_FORWARD,
    )
    assertLog(
      logger.logs[3],
      AnimationPreviewEvent.ToolbarType.LIMITED_ANIMATION,
      AnimationPreviewEvent.UserAction.FRAME_BACKWARD,
    )
    assertLog(
      logger.logs[4],
      AnimationPreviewEvent.ToolbarType.LIMITED_ANIMATION,
      AnimationPreviewEvent.UserAction.PAUSE,
    )
    assertLog(
      logger.logs[5],
      AnimationPreviewEvent.ToolbarType.LIMITED_ANIMATION,
      AnimationPreviewEvent.UserAction.FRAME_CONTROL,
    )
  }

  @Test
  fun testUnlimitedLog() {
    val logger = TestLogger()
    val manager = AnimationToolbarAnalyticsManager(logger, false)

    manager.trackAction(AnimationToolbarType.UNLIMITED, AnimationToolbarAction.PLAY)
    manager.trackAction(AnimationToolbarType.UNLIMITED, AnimationToolbarAction.STOP)
    manager.trackAction(AnimationToolbarType.UNLIMITED, AnimationToolbarAction.FRAME_FORWARD)
    manager.trackAction(AnimationToolbarType.UNLIMITED, AnimationToolbarAction.FRAME_BACKWARD)
    manager.trackAction(AnimationToolbarType.UNLIMITED, AnimationToolbarAction.PAUSE)

    assertLog(
      logger.logs[0],
      AnimationPreviewEvent.ToolbarType.UNLIMITED_ANIMATION,
      AnimationPreviewEvent.UserAction.PLAY,
    )
    assertLog(
      logger.logs[1],
      AnimationPreviewEvent.ToolbarType.UNLIMITED_ANIMATION,
      AnimationPreviewEvent.UserAction.STOP,
    )
    assertLog(
      logger.logs[2],
      AnimationPreviewEvent.ToolbarType.UNLIMITED_ANIMATION,
      AnimationPreviewEvent.UserAction.FRAME_FORWARD,
    )
    assertLog(
      logger.logs[3],
      AnimationPreviewEvent.ToolbarType.UNLIMITED_ANIMATION,
      AnimationPreviewEvent.UserAction.FRAME_BACKWARD,
    )
    assertLog(
      logger.logs[4],
      AnimationPreviewEvent.ToolbarType.UNLIMITED_ANIMATION,
      AnimationPreviewEvent.UserAction.PAUSE,
    )
  }

  @Test
  fun testAnimatedSelectorLog() {
    val logger = TestLogger()
    val manager = AnimationToolbarAnalyticsManager(logger, false)

    manager.trackAction(AnimationToolbarType.ANIMATED_SELECTOR, AnimationToolbarAction.PLAY)
    manager.trackAction(AnimationToolbarType.ANIMATED_SELECTOR, AnimationToolbarAction.STOP)
    manager.trackAction(
      AnimationToolbarType.ANIMATED_SELECTOR,
      AnimationToolbarAction.FRAME_FORWARD,
    )
    manager.trackAction(
      AnimationToolbarType.ANIMATED_SELECTOR,
      AnimationToolbarAction.FRAME_BACKWARD,
    )
    manager.trackAction(AnimationToolbarType.ANIMATED_SELECTOR, AnimationToolbarAction.PAUSE)
    manager.trackAction(
      AnimationToolbarType.ANIMATED_SELECTOR,
      AnimationToolbarAction.SELECT_ANIMATION,
    )

    assertLog(
      logger.logs[0],
      AnimationPreviewEvent.ToolbarType.ANIMATED_SELECTOR,
      AnimationPreviewEvent.UserAction.PLAY,
    )
    assertLog(
      logger.logs[1],
      AnimationPreviewEvent.ToolbarType.ANIMATED_SELECTOR,
      AnimationPreviewEvent.UserAction.STOP,
    )
    assertLog(
      logger.logs[2],
      AnimationPreviewEvent.ToolbarType.ANIMATED_SELECTOR,
      AnimationPreviewEvent.UserAction.FRAME_FORWARD,
    )
    assertLog(
      logger.logs[3],
      AnimationPreviewEvent.ToolbarType.ANIMATED_SELECTOR,
      AnimationPreviewEvent.UserAction.FRAME_BACKWARD,
    )
    assertLog(
      logger.logs[4],
      AnimationPreviewEvent.ToolbarType.ANIMATED_SELECTOR,
      AnimationPreviewEvent.UserAction.PAUSE,
    )
    assertLog(
      logger.logs[5],
      AnimationPreviewEvent.ToolbarType.ANIMATED_SELECTOR,
      AnimationPreviewEvent.UserAction.SELECT_ANIMATION,
    )
  }
}

private fun assertLog(
  event: AndroidStudioEvent,
  toolbarType: AnimationPreviewEvent.ToolbarType,
  userAction: AnimationPreviewEvent.UserAction,
) {
  assertEquals(AndroidStudioEvent.EventCategory.LAYOUT_EDITOR, event.category)
  assertEquals(AndroidStudioEvent.EventKind.LAYOUT_EDITOR_EVENT, event.kind)

  assertEquals(
    LayoutEditorEvent.LayoutEditorEventType.ANIMATION_PREVIEW,
    event.layoutEditorEvent.type,
  )

  assertEquals(toolbarType, event.layoutEditorEvent.animationPreviewEvent.toolbarType)
  assertEquals(userAction, event.layoutEditorEvent.animationPreviewEvent.userAction)
}

private class TestLogger : Consumer<AndroidStudioEvent.Builder> {
  val logs = mutableListOf<AndroidStudioEvent>()

  override fun accept(t: AndroidStudioEvent.Builder) {
    logs.add(t.build())
  }
}

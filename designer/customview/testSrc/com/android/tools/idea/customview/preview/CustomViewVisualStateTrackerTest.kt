/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.customview.preview

import com.android.tools.idea.customview.preview.CustomViewPreviewManager.NotificationsState
import com.android.tools.idea.customview.preview.CustomViewVisualStateTracker.BuildState
import com.android.tools.idea.customview.preview.CustomViewVisualStateTracker.FileState
import com.android.tools.idea.customview.preview.CustomViewVisualStateTracker.PreviewState
import com.android.tools.idea.customview.preview.CustomViewVisualStateTracker.VisualState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * This tests exhaustively tests expected notification and preview states depending on the input
 * file, build and visual states. There are 2 file states, 3 build states and 3 visual states,
 * therefore 3x3x2 = 18 possible input states overall.
 */
@RunWith(Parameterized::class)
class CustomViewVisualStateTrackerTest(
  private val fileState: FileState,
  private val buildState: BuildState,
  private val visualState: VisualState,
  private val expectedNotificationsState: NotificationsState,
  private val expectedPreviewState: PreviewState,
) {
  companion object {
    @Parameterized.Parameters(
      name = "file={0}, build={1}, visual={2}, notification={3}, preview={4}"
    )
    @JvmStatic
    fun params(): List<Array<Any>> =
      listOf(
        arrayOf<Any>(
          FileState.UP_TO_DATE,
          BuildState.SUCCESSFUL,
          VisualState.RENDERING,
          NotificationsState.NO_NOTIFICATIONS,
          PreviewState.RENDERING,
        ),
        arrayOf<Any>(
          FileState.UP_TO_DATE,
          BuildState.SUCCESSFUL,
          VisualState.OK,
          NotificationsState.NO_NOTIFICATIONS,
          PreviewState.OK,
        ),
        arrayOf<Any>(
          FileState.UP_TO_DATE,
          BuildState.SUCCESSFUL,
          VisualState.NONE,
          NotificationsState.NO_NOTIFICATIONS,
          PreviewState.OK,
        ),
        arrayOf<Any>(
          FileState.UP_TO_DATE,
          BuildState.FAILED,
          VisualState.RENDERING,
          NotificationsState.BUILD_FAILED,
          PreviewState.RENDERING,
        ),
        arrayOf<Any>(
          FileState.UP_TO_DATE,
          BuildState.FAILED,
          VisualState.OK,
          NotificationsState.BUILD_FAILED,
          PreviewState.OK,
        ),
        arrayOf<Any>(
          FileState.UP_TO_DATE,
          BuildState.FAILED,
          VisualState.NONE,
          NotificationsState.BUILD_FAILED,
          PreviewState.BUILD_FAILED,
        ),
        arrayOf<Any>(
          FileState.UP_TO_DATE,
          BuildState.IN_PROGRESS,
          VisualState.RENDERING,
          NotificationsState.BUILDING,
          PreviewState.RENDERING,
        ),
        arrayOf<Any>(
          FileState.UP_TO_DATE,
          BuildState.IN_PROGRESS,
          VisualState.OK,
          NotificationsState.BUILDING,
          PreviewState.OK,
        ),
        arrayOf<Any>(
          FileState.UP_TO_DATE,
          BuildState.IN_PROGRESS,
          VisualState.NONE,
          NotificationsState.NO_NOTIFICATIONS,
          PreviewState.BUILDING,
        ),
        arrayOf<Any>(
          FileState.MODIFIED,
          BuildState.SUCCESSFUL,
          VisualState.RENDERING,
          NotificationsState.NO_NOTIFICATIONS,
          PreviewState.RENDERING,
        ),
        arrayOf<Any>(
          FileState.MODIFIED,
          BuildState.SUCCESSFUL,
          VisualState.OK,
          NotificationsState.CODE_MODIFIED,
          PreviewState.OK,
        ),
        arrayOf<Any>(
          FileState.MODIFIED,
          BuildState.SUCCESSFUL,
          VisualState.NONE,
          NotificationsState.CODE_MODIFIED,
          PreviewState.OK,
        ),
        arrayOf<Any>(
          FileState.MODIFIED,
          BuildState.FAILED,
          VisualState.RENDERING,
          NotificationsState.CODE_MODIFIED,
          PreviewState.RENDERING,
        ),
        arrayOf<Any>(
          FileState.MODIFIED,
          BuildState.FAILED,
          VisualState.OK,
          NotificationsState.CODE_MODIFIED,
          PreviewState.OK,
        ),
        arrayOf<Any>(
          FileState.MODIFIED,
          BuildState.FAILED,
          VisualState.NONE,
          NotificationsState.CODE_MODIFIED,
          PreviewState.BUILD_FAILED,
        ),
        arrayOf<Any>(
          FileState.MODIFIED,
          BuildState.IN_PROGRESS,
          VisualState.RENDERING,
          NotificationsState.BUILDING,
          PreviewState.RENDERING,
        ),
        arrayOf<Any>(
          FileState.MODIFIED,
          BuildState.IN_PROGRESS,
          VisualState.OK,
          NotificationsState.BUILDING,
          PreviewState.OK,
        ),
        arrayOf<Any>(
          FileState.MODIFIED,
          BuildState.IN_PROGRESS,
          VisualState.NONE,
          NotificationsState.NO_NOTIFICATIONS,
          PreviewState.BUILDING,
        ),
      )
  }

  @Test
  fun test() {
    val stateTracker = CustomViewVisualStateTracker()
    stateTracker.setVisualState(visualState)
    stateTracker.setBuildState(buildState)
    stateTracker.setFileState(fileState)

    assertEquals(expectedNotificationsState, stateTracker.notificationsState)
    assertEquals(expectedPreviewState, stateTracker.previewState)
  }
}

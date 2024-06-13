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

class CustomViewVisualStateTracker(
  private var fileState: FileState = FileState.UP_TO_DATE,
  private var buildState: BuildState = BuildState.SUCCESSFUL,
  private var visualState: VisualState = VisualState.NONE,
  private val onNotificationStateChanged:
    (newState: CustomViewPreviewManager.NotificationsState) -> Unit =
    {},
  private val onPreviewStateChanged: (newState: PreviewState) -> Unit = {},
) {
  enum class FileState {
    MODIFIED,
    UP_TO_DATE,
  }

  enum class BuildState {
    FAILED,
    SUCCESSFUL,
    IN_PROGRESS,
  }

  enum class VisualState {
    NONE,
    RENDERING,
    OK,
  }

  enum class PreviewState {
    RENDERING,
    OK,
    BUILDING,
    BUILD_FAILED,
  }

  private fun recalculateStates() {
    previewState =
      when (visualState) {
        VisualState.OK -> PreviewState.OK
        VisualState.RENDERING -> PreviewState.RENDERING
        VisualState.NONE -> {
          when (buildState) {
            BuildState.FAILED -> PreviewState.BUILD_FAILED
            BuildState.IN_PROGRESS -> PreviewState.BUILDING
            else -> PreviewState.OK
          }
        }
      }
    notificationsState =
      when {
        buildState == BuildState.IN_PROGRESS && previewState != PreviewState.BUILDING ->
          CustomViewPreviewManager.NotificationsState.BUILDING
        fileState == FileState.MODIFIED &&
          (buildState == BuildState.FAILED ||
            (buildState == BuildState.SUCCESSFUL && previewState != PreviewState.RENDERING)) ->
          CustomViewPreviewManager.NotificationsState.CODE_MODIFIED
        fileState == FileState.UP_TO_DATE && buildState == BuildState.FAILED ->
          CustomViewPreviewManager.NotificationsState.BUILD_FAILED
        else -> CustomViewPreviewManager.NotificationsState.NO_NOTIFICATIONS
      }
  }

  @get:Synchronized
  var notificationsState: CustomViewPreviewManager.NotificationsState =
    CustomViewPreviewManager.NotificationsState.NO_NOTIFICATIONS
    private set(value) {
      if (value != field) {
        field = value
        onNotificationStateChanged(field)
      }
    }

  @get:Synchronized
  var previewState: PreviewState = PreviewState.OK
    private set(value) {
      if (value != field) {
        field = value
        onPreviewStateChanged(field)
      }
    }

  init {
    recalculateStates()
    onNotificationStateChanged(notificationsState)
    onPreviewStateChanged(previewState)
  }

  @Synchronized
  fun setFileState(s: FileState) {
    fileState = s
    recalculateStates()
  }

  @Synchronized
  fun setBuildState(s: BuildState) {
    buildState = s
    recalculateStates()
  }

  @Synchronized
  fun setVisualState(s: VisualState) {
    visualState = s
    recalculateStates()
  }
}

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
package com.android.tools.profilers.taskbased.pastrecordings

import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.taskbased.TaskEntranceTabModel
import com.android.tools.profilers.taskbased.home.selections.recordings.RecordingListModel
import com.google.common.annotations.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The PastRecordingsTabModel serves as the data model for the past recordings tab. It owns the recording list model to manage the
 * available recordings the user can select from, as well as current recording selection. It also implements the behavior on open Profiler
 * task button click, reading the recording and Profiler task selection and using such values to launch the Profiler task.
 */
class PastRecordingsTabModel(profilers: StudioProfilers) : TaskEntranceTabModel(profilers) {
  val recordingListModel = RecordingListModel(profilers, taskHandlers, taskGridModel::resetTaskSelection, taskGridModel::onTaskSelection,
                                              ::onEnterTaskButtonClick)

  @VisibleForTesting
  val selectedRecording get() = recordingListModel.selectedRecording.value

  private val _isBannerClosed = MutableStateFlow(false)
  val isBannerClosed = _isBannerClosed.asStateFlow()

  fun onRecordingBannerClose() {
    _isBannerClosed.value = true
  }

  fun onRecordingBannerDontShowAgainClick() {
    // Clicking "Don't show again" should also close the banner, effectively performing the same behavior as clicking the close button,
    // but with the additional save of permanent preference to never show the banner again.
    onRecordingBannerClose()
    profilers.ideServices.persistentProfilerPreferences.setBoolean(DONT_SHOWN_AGAIN_RECORDING_BANNER, true)
  }

  fun isRecordingBannerNotShownAgain(): Boolean {
    return profilers.ideServices.persistentProfilerPreferences.getBoolean(DONT_SHOWN_AGAIN_RECORDING_BANNER, false)
  }

  override fun doEnterTaskButton() {
    val selectedSession = selectedRecording!!.session
    // Update the currently selected task type used to launch the recording with before setting the session. This guarantees that the
    // most up-to-date Profiler task will be used to handle the set session's data.
    profilers.sessionsManager.currentTaskType = selectedTaskType
    profilers.sessionsManager.setSession(selectedSession)
  }

  companion object {
    const val DONT_SHOWN_AGAIN_RECORDING_BANNER = "DONT_SHOW_AGAIN_RECORDING_BANNER"
  }
}
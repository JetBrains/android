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
package com.android.tools.profilers.taskbased.home.selections.recordings

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.sessions.SessionAspect
import com.android.tools.profilers.sessions.SessionItem
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.TaskSupportUtils
import com.android.tools.profilers.tasks.taskhandlers.ProfilerTaskHandler
import com.google.common.annotations.VisibleForTesting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * This class serves as the model for the list of past and imported recordings. The user can select any of the listed recordings to launch
 * a task from.
 *
 * Note: In the Task-Based UX, a past recording is structurally equivalent to a SessionItem as SessionItems have the ability to wrap past
 * recordings as artifacts. This equivalence has precedence as imported files are converted to SessionItems in the Sessions-Based UX.
 */
class RecordingListModel(val profilers: StudioProfilers,
                         private val taskHandlers: Map<ProfilerTaskType, ProfilerTaskHandler>,
                         private val resetTaskSelection: () -> Unit,
                         private val setTaskSelection: (ProfilerTaskType) -> Unit = {},
                         private val openProfilerTask: () -> Unit) : AspectObserver() {
  private val _recordingList = MutableStateFlow(listOf<SessionItem>())
  val recordingList = _recordingList.asStateFlow()
  private val _selectedRecording = MutableStateFlow<SessionItem?>(null)
  val selectedRecording = _selectedRecording.asStateFlow()

  init {
    profilers.sessionsManager.addDependency(this)
      .onChange(SessionAspect.SESSIONS) { sessionItemsUpdated() }
  }

  fun onRecordingSelection(newRecording: SessionItem?) {
    val recordingTaskType = newRecording?.getTaskType() ?: ProfilerTaskType.UNSPECIFIED
    setTaskSelection(recordingTaskType)
    _selectedRecording.value = newRecording
    updateTaskSelection()
  }

  fun isSelectedRecordingExportable() = _selectedRecording.value.let {
    it != null && it.containsExactlyOneArtifact() && (it.getChildArtifacts().firstOrNull()?.canExport ?: false)
  }

  val exportableArtifact get() = if (isSelectedRecordingExportable()) selectedRecording.value!!.getChildArtifacts().first() else null

  fun isRecordingSelected() = _selectedRecording.value != null

  fun doDeleteSelectedRecording() {
    assert(isRecordingSelected())
    _selectedRecording.value!!.deleteSession()
    resetTaskSelection()
    resetRecordingSelection()
  }

  private fun resetRecordingSelection() {
    _selectedRecording.value = null
  }

  private fun updateTaskSelection() {
    // If only one task is supported after the recording is selected, that task is auto-selected.
    val supportedTasks = taskHandlers.entries.toList().filter {
      _selectedRecording.value != null && TaskSupportUtils.isTaskSupportedByRecording(it.key, it.value, _selectedRecording.value!!) }
    if (supportedTasks.size == 1) {
      setTaskSelection(supportedTasks.first().key)
    }
  }

  private fun sessionItemsUpdated() {
    val sessionItems = profilers.sessionsManager.sessionArtifacts.filterIsInstance<SessionItem>().filter {
      !it.isOngoing && it.getTaskType() != ProfilerTaskType.UNSPECIFIED
    }
    val newRecordingList = mutableListOf<SessionItem>()
    newRecordingList.addAll(sessionItems)
    val oldRecordingList = _recordingList.value.toList()
    _recordingList.value = newRecordingList
    autoOpenRecordingIfNewlyImported(oldRecordingList, newRecordingList)
  }

  /**
   * Checks whether the updated recording list indicates the addition of a single, new imported recording. If so, the recording is
   * automatically selected and opened.
   */
  private fun autoOpenRecordingIfNewlyImported(oldRecordingList: List<SessionItem>, newRecordingList: List<SessionItem>) {
    val difference = newRecordingList.toSet() - oldRecordingList.toSet()
    // Confirm there is one, new recording added. If so, auto open the new recording.
    if (newRecordingList.size > oldRecordingList.size && difference.size == 1 && difference.first().isImported()) {
      openRecording(difference.first())
    }
  }

  /**
   * Selects the recording and launches it in the task tab with its respective task type.
   */
  fun openRecording(recording: SessionItem) {
    onRecordingSelection(recording)
    openProfilerTask()
  }

  @VisibleForTesting
  fun setRecordingList(recordingList: List<SessionItem>) {
    _recordingList.value = recordingList
  }
}
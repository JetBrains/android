/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.attribution

import com.android.annotations.concurrency.UiThread
import com.android.build.attribution.BuildAttributionStateReporter.State
import com.android.build.attribution.BuildAttributionStateReporter.State.AGP_VERSION_LOW
import com.android.build.attribution.BuildAttributionStateReporter.State.FEATURE_TURNED_OFF
import com.android.build.attribution.BuildAttributionStateReporter.State.NO_DATA
import com.android.build.attribution.BuildAttributionStateReporter.State.NO_DATA_BUILD_FAILED_TO_FINISH
import com.android.build.attribution.BuildAttributionStateReporter.State.NO_DATA_BUILD_RUNNING
import com.android.build.attribution.BuildAttributionStateReporter.State.REPORT_DATA_READY
import com.android.build.attribution.ui.BuildAttributionUiManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.attribution.isAgpVersionHigherOrEqualToMinimal
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.sync.GradleSyncState
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive
import com.intellij.util.messages.Topic


interface BuildAttributionStateReporter : Disposable {
  companion object {
    val FEATURE_STATE_TOPIC: Topic<Notifier> =
      Topic.create("Build Analyzer state change", Notifier::class.java)
  }

  fun currentState(): State

  interface Notifier {
    @UiThread
    fun stateUpdated(newState: State)
  }

  enum class State {
    REPORT_DATA_READY,
    NO_DATA,
    NO_DATA_BUILD_RUNNING,
    NO_DATA_BUILD_FAILED_TO_FINISH,
    AGP_VERSION_LOW,
    FEATURE_TURNED_OFF
  }
}

class BuildAttributionStateReporterImpl(
  private val project: Project,
  private val uiManager: BuildAttributionUiManager
) : BuildAttributionStateReporter {

  private var state: State

  init {
    Disposer.register(project, this)
    state = calculateInitState()

    GradleBuildState.subscribe(project, object : GradleBuildListener.Adapter() {
      override fun buildFinished(status: BuildStatus, context: BuildContext?) {
        if (!status.isBuildSuccessful) {
          changeStateTo(newStateOnBuildFail())
        }
      }

      override fun buildStarted(context: BuildContext) {
        changeStateTo(newStateOnBuildStart())
      }
    }, this)
    GradleSyncState.subscribe(project, object : GradleSyncListener {
      override fun syncSucceeded(project: Project) {
        changeStateTo(newStateOnSync())
      }
    }, this)
  }

  fun setStateDataExist() {
    changeStateTo(REPORT_DATA_READY)
  }

  override fun currentState(): State {
    return state
  }

  override fun dispose() = Unit

  private fun calculateInitState(): State = when {
    !StudioFlags.BUILD_ATTRIBUTION_ENABLED.get() -> FEATURE_TURNED_OFF
    !isAgpVersionHigherOrEqualToMinimal(project) -> AGP_VERSION_LOW
    uiManager.hasDataToShow() -> REPORT_DATA_READY
    else -> NO_DATA
  }

  private fun newStateOnBuildStart(): State = when (state) {
    NO_DATA, NO_DATA_BUILD_FAILED_TO_FINISH -> NO_DATA_BUILD_RUNNING
    else -> state
  }

  private fun newStateOnBuildFail(): State = when (state) {
    NO_DATA_BUILD_RUNNING, REPORT_DATA_READY -> NO_DATA_BUILD_FAILED_TO_FINISH
    else -> state
  }

  private fun newStateOnSync(): State = calculateInitState()

  private fun changeStateTo(newState: State) {
    if (state == newState) return
    // State update should happen in the UI thread to make it thread safe.
    invokeLaterIfProjectAlive(project) {
      state = newState
      project.messageBus.syncPublisher(BuildAttributionStateReporter.FEATURE_STATE_TOPIC).stateUpdated(state)
    }
  }
}

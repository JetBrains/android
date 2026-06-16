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
package com.android.tools.idea.insights

import com.android.tools.idea.insights.ai.AiInsightToolkit
import com.android.tools.idea.insights.analytics.IssueSelectionSource
import com.android.tools.idea.insights.events.actions.Action
import com.android.tools.idea.insights.experiments.InsightFeedback
import com.android.tools.idea.insights.model.connection.Connection
import com.android.tools.idea.insights.model.event.Device
import com.android.tools.idea.insights.model.event.OperatingSystemInfo
import com.android.tools.idea.insights.model.event.Version
import com.android.tools.idea.insights.model.issue.AppInsightsCrash
import com.android.tools.idea.insights.model.issue.FailureType
import com.android.tools.idea.insights.model.issue.IssueVariant
import com.android.tools.idea.insights.model.issue.SignalType
import com.android.tools.idea.insights.model.issue.VisibilityType
import com.android.tools.idea.insights.model.note.Note
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/** The source-based controller which provides lifecycle and App Insights crash state data. */
interface AppInsightsCrashController : AppInsightsProjectLevelController {

  /**
   * This flow represents the App Insights crash state of a host Android app module.
   *
   * The state includes:
   * * Active and available [Connection]s of a project.
   * * Active and available crashes of the app.
   * * Active and available filters used to fetch the above crashes.
   *
   * It contains many pieces of data all of which can change independently resulting in a new value produced, as a result it is more
   * convenient to [map] this flow into multiple sub flows that "focus" on a subset of the data you care about. e.g.
   *
   * ```kotlin
   * val connections: StateFlow<Selection<Connection>> = ctrl.connections
   * val crashes: Flow<LoadingState<Timed<Selection<AppInsightsCrash>>>> = ctrl.state.map { it.issues }.distinctUntilChanged()
   * val selectedCrash: Flow<AppInsightsCrash?> = crashes.map { it.valueOrNull()?.value?.selected }
   * ```
   */
  val state: Flow<AppInsightsCrashState>

  /** [CoroutineScope] whose lifecycle is tied to current configuration of the host module. */
  val coroutineScope: CoroutineScope

  /** The project this controller is associated with. */
  val project: Project

  /** The set of tools used to assist with Ai */
  val aiInsightToolkit: AiInsightToolkit

  fun selectIssue(value: AppInsightsCrash?, selectionSource: IssueSelectionSource)

  fun selectVersions(values: Set<Version>)

  fun selectDevices(values: Set<Device>)

  fun selectOperatingSystems(values: Set<OperatingSystemInfo>)

  fun selectTimeInterval(value: TimeIntervalFilter)

  fun toggleFailureType(value: FailureType)

  fun enterOfflineMode()

  fun insightsInFile(file: PsiFile): List<AppInsight>

  fun revertToSnapshot(state: AppInsightsCrashState)

  fun selectSignal(value: SignalType)

  fun selectConnection(value: Connection?)

  fun nextEvent()

  fun previousEvent()

  fun openIssue(issue: AppInsightsCrash)

  fun closeIssue(issue: AppInsightsCrash)

  fun addNote(issue: AppInsightsCrash, message: String)

  fun deleteNote(note: Note)

  fun selectVisibilityType(value: VisibilityType)

  fun selectIssueVariant(variant: IssueVariant?)

  fun refreshInsight(regenerateWithContext: Boolean, forceGenerateNewInsight: Boolean = false)

  fun submitInsightFeedback(insightFeedback: InsightFeedback)

  /** Disables the [action]. Use [enableAction] to enable the action. */
  fun disableAction(action: KClass<out Action>)

  /**
   * Enables the [action].
   *
   * **Enabling an action does not call it**. It is the enabler's responsibility to call the enabled action.
   *
   * Use [disableAction] to disable the action.
   */
  fun enableAction(action: KClass<out Action>)
}

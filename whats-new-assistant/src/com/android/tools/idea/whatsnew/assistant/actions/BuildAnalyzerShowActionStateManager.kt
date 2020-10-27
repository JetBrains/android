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
package com.android.tools.idea.whatsnew.assistant.actions

import com.android.build.attribution.BuildAttributionStateReporter
import com.android.build.attribution.BuildAttributionStateReporter.State
import com.android.build.attribution.ui.BuildAttributionUiManager
import com.android.tools.idea.assistant.AssistActionState
import com.android.tools.idea.assistant.AssistActionStateManager
import com.android.tools.idea.assistant.StatefulButtonNotifier
import com.android.tools.idea.assistant.datamodel.ActionData
import com.android.tools.idea.assistant.view.StatefulButtonMessage
import com.android.tools.idea.assistant.view.UIUtils
import com.android.tools.idea.whatsnew.assistant.WhatsNewMetricsTracker
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.util.messages.MessageBusConnection
import java.awt.Color
import javax.swing.Icon

class BuildAnalyzerShowActionStateManager : AssistActionStateManager() {

  override fun getId(): String = BuildAnalyzerShowAction.ACTION_KEY

  private val projectToConnection: MutableMap<Project, MessageBusConnection> = mutableMapOf()

  override fun init(project: Project, actionData: ActionData) {
    projectToConnection.computeIfAbsent(project, this::connectToNewProject)
    WhatsNewMetricsTracker.getInstance().actionButtonCreated(project, BuildAnalyzerShowAction.ACTION_KEY)
  }

  private fun connectToNewProject(newProject: Project): MessageBusConnection {
    val connection = newProject.messageBus.connect(newProject)
    Disposer.register(connection, Disposable { projectToConnection.remove(newProject) })
    connection.subscribe(BuildAttributionStateReporter.FEATURE_STATE_TOPIC, object : BuildAttributionStateReporter.Notifier {
      override fun stateUpdated(newState: State) {
        requestButtonRefresh(newProject)
        WhatsNewMetricsTracker.getInstance().stateUpdateActionButton(newProject, BuildAnalyzerShowAction.ACTION_KEY)
      }
    })
    return connection
  }

  override fun getState(project: Project, actionData: ActionData): AssistActionState {
    return featureState(project).toButtonState()
  }

  override fun getStateDisplay(project: Project, actionData: ActionData, successMessage: String?): StatefulButtonMessage? {
    val featureState = featureState(project)
    return StatefulButtonMessage(featureState.toButtonMessage(), featureState.toButtonState())
  }

  private fun featureState(project: Project): State =
    BuildAttributionUiManager.getInstance(project).stateReporter.currentState()

  private fun State.toButtonState(): AssistActionState = when (this) {
    State.REPORT_DATA_READY -> ActionState.REPORT_DATA_READY
    State.NO_DATA -> ActionState.BUILD_REQUIRED
    State.NO_DATA_BUILD_RUNNING -> ActionState.BUILD_RUNNING
    State.NO_DATA_BUILD_FAILED_TO_FINISH -> ActionState.BUILD_FAILED_TO_COMPLETE
    State.AGP_VERSION_LOW -> ActionState.AGP_UPDATE_REQUIRED
    State.FEATURE_TURNED_OFF -> ActionState.FEATURE_TURNED_OFF
  }

  private fun State.toButtonMessage(): String = when (this) {
    State.REPORT_DATA_READY -> "Previous build's report is available. Click to open."
    State.NO_DATA -> "No report is available.<br/>" +
                     "Click Analyze Build to build your project<br/>" +
                     "and open the Build Analyzer with the new report."
    State.NO_DATA_BUILD_RUNNING -> "Generating build report now."
    State.NO_DATA_BUILD_FAILED_TO_FINISH -> "Build failed to complete.<br/>" +
                                            "Resolve any errors and try again."
    State.AGP_VERSION_LOW -> "Android Gradle Plugin 4.0.0 or higher<br/>" +
                             "is required to use the Build Analyzer."
    State.FEATURE_TURNED_OFF -> ""
  }

  private fun requestButtonRefresh(project: Project) =
    project.messageBus.syncPublisher(StatefulButtonNotifier.BUTTON_STATE_TOPIC).stateUpdated()

  private enum class ActionState(
    private val buttonActive: Boolean,
    private val messageForegroundColor: Color,
    private val messageIcon: Icon
  ) : AssistActionState {
    REPORT_DATA_READY(true, UIUtils.getSuccessColor(), AllIcons.RunConfigurations.TestPassed),
    BUILD_REQUIRED(true, UIUtils.getFailureColor(), AllIcons.General.Warning),
    BUILD_RUNNING(false, JBColor.BLACK, AnimatedIcon.Default.INSTANCE),
    BUILD_FAILED_TO_COMPLETE(true, UIUtils.getFailureColor(), AllIcons.General.Error),
    AGP_UPDATE_REQUIRED(false, JBColor.BLACK, AllIcons.General.Information),
    FEATURE_TURNED_OFF(false, JBColor.BLACK, AllIcons.General.Information);


    override fun isButtonVisible(): Boolean = true

    override fun isButtonEnabled(): Boolean = buttonActive

    override fun isMessageVisible(): Boolean = true

    override fun getIcon(): Icon? = messageIcon

    override fun getForeground(): Color = messageForegroundColor
  }
}

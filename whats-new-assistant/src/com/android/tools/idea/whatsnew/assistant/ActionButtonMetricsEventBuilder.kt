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
package com.android.tools.idea.whatsnew.assistant

import com.android.tools.idea.whatsnew.assistant.actions.AppInspectionShowAction
import com.google.wireless.android.sdk.stats.WhatsNewAssistantUpdateEvent.ActionButtonEvent
import com.intellij.openapi.project.Project

class ActionButtonMetricsEventBuilder {

  private val createdActions = mutableSetOf<String>()

  fun generateEventsForAllCreatedBeforeActions(project: Project): List<ActionButtonEvent.Builder> {
    return createdActions.map { actionCreated(project, it) }
  }

  fun actionCreated(project: Project, actionKey: String): ActionButtonEvent.Builder {
    return actionEventBuilder(project, actionKey, ActionButtonEvent.EventType.BUTTON_CREATED)
  }

  fun clickAction(project: Project, actionKey: String): ActionButtonEvent.Builder {
    return actionEventBuilder(project, actionKey, ActionButtonEvent.EventType.BUTTON_CLICK)
  }

  fun stateUpdateAction(project: Project, actionKey: String): ActionButtonEvent.Builder {
    return actionEventBuilder(project, actionKey, ActionButtonEvent.EventType.BUTTON_STATE_UPDATED)
  }

  private fun actionEventBuilder(
    project: Project,
    actionKey: String,
    actionEventType: ActionButtonEvent.EventType
  ): ActionButtonEvent.Builder {
    createdActions.add(actionKey)
    return when (actionKey) {
      AppInspectionShowAction.ACTION_KEY -> appInspectionActionEventBuilder(project, actionEventType)
      else -> commonActionEventBuilder(ActionButtonEvent.ActionButtonType.UNKNOWN_BUTTON, actionEventType)
    }
  }

  private fun commonActionEventBuilder(
    actionButtonType: ActionButtonEvent.ActionButtonType,
    actionEventType: ActionButtonEvent.EventType
  ): ActionButtonEvent.Builder {
    return ActionButtonEvent.newBuilder().also {
      it.actionButtonType = actionButtonType
      it.eventType = actionEventType
    }
  }

  private fun appInspectionActionEventBuilder(project: Project, actionEventType: ActionButtonEvent.EventType): ActionButtonEvent.Builder {
    return commonActionEventBuilder(ActionButtonEvent.ActionButtonType.APP_INSPECTION_SHOW, actionEventType)
  }
}

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
package com.android.tools.idea.compose.preview.actions

import com.android.tools.idea.common.actions.IssueNotificationAction
import com.android.tools.idea.compose.preview.COMPOSE_PREVIEW_MANAGER
import com.android.tools.idea.compose.preview.message
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.AnimatedIcon
import javax.swing.Icon

private const val COMPOSE_ISSUE_NOTIFICATION_ACTION = "Android.Designer.ComposeIssueNotificationAction"

class ComposeIssueNotificationAction : IssueNotificationAction() {
  override fun getNoErrorsIconAndDescription(event: AnActionEvent): Pair<Icon?, String?> {
    return event.getData(COMPOSE_PREVIEW_MANAGER)?.let {
      when {
        it.status().hasSyntaxErrors -> AllIcons.General.Error to message("notification.syntax.errors")
        it.status().isRefreshing -> AnimatedIcon.Default() to message("notification.preview.refreshing")
        it.status().isOutOfDate -> AllIcons.General.Warning to message("notification.preview.out.of.date")
        else -> AllIcons.General.InspectionsOK  to message("notification.preview.up.to.date")
      }
    } ?: super.getNoErrorsIconAndDescription(event)
  }

  companion object {
    @JvmStatic
    fun getInstance(): ComposeIssueNotificationAction =
      ActionManager.getInstance().getAction(COMPOSE_ISSUE_NOTIFICATION_ACTION) as ComposeIssueNotificationAction
  }
}
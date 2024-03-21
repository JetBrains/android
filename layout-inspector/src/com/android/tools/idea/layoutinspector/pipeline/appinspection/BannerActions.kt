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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.StatusNotificationAction
import com.intellij.ide.BrowserUtil
import com.intellij.ui.EditorNotificationPanel.Status

private const val ACTIVITY_RESTART_KEY = "activity.restart"
// TODO(b/330406958): replace with redirect URL
// TODO(b/330406958): update documentation
private const val DEBUG_VIEW_ATTRIBUTES_DOCUMENTATION_URL =
  "https://developer.android.com/studio/debug/layout-inspector#activity-restart"

/**
 * Show a banner with "Activity Restarted" and a link to turn on "Layout inspection without an
 * activity restart".
 */
fun showActivityRestartedInBanner(notificationModel: NotificationModel) {
  val learnMoreAction =
    StatusNotificationAction(LayoutInspectorBundle.message("learn.more")) {
      BrowserUtil.browse(DEBUG_VIEW_ATTRIBUTES_DOCUMENTATION_URL)
    }

  notificationModel.addNotification(
    id = ACTIVITY_RESTART_KEY,
    text = LayoutInspectorBundle.message(ACTIVITY_RESTART_KEY),
    status = Status.Info,
    actions = listOf(learnMoreAction, notificationModel.dismissAction),
  )
}

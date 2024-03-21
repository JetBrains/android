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
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ui.EditorNotificationPanel.Status

const val ACTIVITY_RESTART_KEY = "activity.restart"

@VisibleForTesting
const val KEY_HIDE_ACTIVITY_RESTART_BANNER = "live.layout.inspector.activity.restart.banner.hide"

/**
 * Show a banner with "Activity Restarted" and a link to turn on "Layout inspection without an
 * activity restart".
 */
// TODO(b/330406958): adapt banner to reflect absence of running configuration
fun showActivityRestartedInBanner(notificationModel: NotificationModel) {
  if (PropertiesComponent.getInstance().getBoolean(KEY_HIDE_ACTIVITY_RESTART_BANNER)) {
    // The user already opted out of this banner.
    return
  }

  val doNotShowAgainAction =
    StatusNotificationAction(LayoutInspectorBundle.message("do.not.show.again")) { notification ->
      PropertiesComponent.getInstance().setValue(KEY_HIDE_ACTIVITY_RESTART_BANNER, true)
      notificationModel.dismissAction.invoke(notification)
    }

  notificationModel.addNotification(
    id = ACTIVITY_RESTART_KEY,
    text = LayoutInspectorBundle.message(ACTIVITY_RESTART_KEY),
    status = Status.Info,
    actions = listOf(doNotShowAgainAction, notificationModel.dismissAction),
  )
}

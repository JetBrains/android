/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.idea.gservices.DevServicesDeprecationData
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.ServiceDeprecationInfo.Panel
import com.google.wireless.android.sdk.stats.DevServiceDeprecationInfo
import com.intellij.ide.BrowserUtil
import com.intellij.ui.EditorNotificationPanel

class ServiceDeprecatedBanner
private constructor(
  deprecationData: DevServicesDeprecationData,
  updater: () -> Unit,
  moreInfo: () -> Unit,
) : EditorNotificationPanel(Status.Warning) {

  init {
    text = deprecationData.description
    if (deprecationData.showUpdateAction) {
      createActionLabel("Update Android Studio") { updater() }
    }
    if (deprecationData.moreInfoUrl.isNotEmpty()) {
      createActionLabel("More info") {
        BrowserUtil.browse(deprecationData.moreInfoUrl)
        moreInfo()
      }
    }
  }

  companion object {
    fun create(
      tracker: AppInsightsTracker,
      deprecationData: DevServicesDeprecationData,
      update: () -> Unit,
    ) =
      ServiceDeprecatedBanner(
        deprecationData,
        {
          update()
          tracker.logDeprecatedEvent(userClickedUpdate = true)
        },
      ) {
        tracker.logDeprecatedEvent(userClickedMoreInfo = true)
      }
  }
}

fun AppInsightsTracker.logDeprecatedEvent(
  userNotified: Boolean? = null,
  userClickedMoreInfo: Boolean? = null,
  userClickedUpdate: Boolean? = null,
  userClickedDismiss: Boolean? = null,
) =
  logServiceDeprecated(
    Panel.TAB_PANEL,
    DevServiceDeprecationInfo.DeliveryType.BANNER,
    userNotified,
    userClickedMoreInfo,
    userClickedUpdate,
    userClickedDismiss,
  )

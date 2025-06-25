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

import com.android.tools.idea.gservices.DeprecationBanner
import com.android.tools.idea.gservices.DevServiceDeprecationInfoBuilder
import com.android.tools.idea.gservices.DevServicesDeprecationData
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.google.wireless.android.sdk.stats.DevServiceDeprecationInfo
import com.intellij.openapi.project.Project

class AppInsightsDeprecationBanner(
  project: Project,
  private val deprecationData: DevServicesDeprecationData,
  private val tracker: AppInsightsTracker,
  closeAction: () -> Unit,
) : DeprecationBanner(project, deprecationData, false, closeAction) {
  override fun trackUserNotified() {
    logEvent(userNotified = true)
  }

  override fun trackUpdateClicked() {
    logEvent(userClickedUpdate = true)
  }

  override fun trackMoreInfoClicked() {
    logEvent(userClickedMoreInfo = true)
  }

  override fun trackBannerDismissed() {
    logEvent(bannerDismissed = true)
  }

  private fun logEvent(
    userNotified: Boolean? = null,
    userClickedMoreInfo: Boolean? = null,
    userClickedUpdate: Boolean? = null,
    bannerDismissed: Boolean? = null,
  ) =
    tracker.logServiceDeprecated(
      AppQualityInsightsUsageEvent.ServiceDeprecationInfo.Panel.TAB_PANEL,
      DevServiceDeprecationInfo.DeliveryType.PANEL,
      DevServiceDeprecationInfoBuilder(
        deprecationData.status,
        DevServiceDeprecationInfo.DeliveryType.BANNER,
        userNotified,
        userClickedMoreInfo,
        userClickedUpdate,
        bannerDismissed,
      ),
    )
}

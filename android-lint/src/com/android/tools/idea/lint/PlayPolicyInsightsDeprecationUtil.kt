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
package com.android.tools.idea.lint

import com.android.tools.idea.gservices.DevServicesDeprecationDataProvider
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.components.service

private const val DEPRECATION_SERVICE_NAME = "aqi/policy"
private const val DEPRECATION_USER_FRIENDLY_SERVICE_NAME = "Play Policy Insights"
@VisibleForTesting
const val DEPRECATION_PREFIX =
  "Outdated Insight: Play Policy Insights is no longer compatible with this version of Android Studio.\n"

fun Incident.updateMessageWithDeprecationInfo() {
  if (!message.contains(DEPRECATION_PREFIX)) {
    message = "$DEPRECATION_PREFIX $message"
  }
}

val isPlayPolicyInsightsUnsupported: Boolean
  get() =
    service<DevServicesDeprecationDataProvider>()
      .getCurrentDeprecationData(DEPRECATION_SERVICE_NAME, DEPRECATION_USER_FRIENDLY_SERVICE_NAME)
      .isUnsupported()

fun isPlayPolicyIssue(issue: Issue): Boolean = issue.category.fullName == "Play Policy"

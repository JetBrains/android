/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.jdk

import com.android.tools.analytics.UsageTracker
import com.android.tools.analytics.withProjectId
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.PROJECT_SYSTEM
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_JDK_INVALID
import com.google.wireless.android.sdk.stats.GradleJdkInvalidEvent
import com.google.wireless.android.sdk.stats.GradleJdkInvalidEvent.InvalidJdkReason
import com.intellij.openapi.project.Project

/**
 * Analytic tracker util that reports any JDK related events using [UsageTracker]
 */
object JdkAnalyticsTracker {

  fun reportInvalidJdkException(project: Project, reason: InvalidJdkReason) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setCategory(PROJECT_SYSTEM)
        .setKind(GRADLE_JDK_INVALID)
        .setGradleJdkInvalidEvent(GradleJdkInvalidEvent.newBuilder().setReason(reason))
        .withProjectId(project)
    )
  }
}
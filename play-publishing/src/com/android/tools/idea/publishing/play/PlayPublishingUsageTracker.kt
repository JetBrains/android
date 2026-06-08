/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.publishing.play

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.publishing.AppPublishingSource
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.PlayPublishingEvent
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.CreateAppDetails.CreateAppResult
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.CreateReleaseDetails.CreateReleaseResult
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.CreateReleaseDetails.TrackType
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.PlayPublishingEventType
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.WizardShownDetails.WizardInvocationSource

// TODO: android-merge; upstream builds every event below with the generated Kotlin DSL, `androidStudioEvent { ... }`,
//  `playPublishingEvent { ... }` and the four `*Details { ... }` builders. Those are top-level functions of the
//  generated proto, and the studio-platform jar declares no kotlin_module for com.google.wireless.android.sdk.stats,
//  so kotlinc cannot see them here. Same events, built with the generated Java builders.
object PlayPublishingUsageTracker {

  fun trackWizardShown(source: AppPublishingSource) {
    trackEvent(
      PlayPublishingEvent.newBuilder()
        .setEventType(PlayPublishingEventType.WIZARD_SHOWN)
        .setWizardShownDetails(
          PlayPublishingEvent.WizardShownDetails.newBuilder().setInvocationSource(source.toWizardInvocationSource())
        )
    )
  }

  fun trackChooseBundle(
    isPackageRegistered: Boolean?,
    isAppNameRead: Boolean,
    isPackageNameRead: Boolean,
    isVersionCodeRead: Boolean,
    isVersionNameRead: Boolean,
  ) {
    trackEvent(
      PlayPublishingEvent.newBuilder()
        .setEventType(PlayPublishingEventType.CHOOSE_BUNDLE)
        .setChooseBundleDetails(
          PlayPublishingEvent.ChooseBundleDetails.newBuilder()
            .setPackageRegistered(isPackageRegistered ?: false)
            .setAppNameRead(isAppNameRead)
            .setPackageNameRead(isPackageNameRead)
            .setVersionCodeRead(isVersionCodeRead)
            .setVersionNameRead(isVersionNameRead)
        )
    )
  }

  fun trackCreateApp(result: CreateAppResult) {
    trackEvent(
      PlayPublishingEvent.newBuilder()
        .setEventType(PlayPublishingEventType.CREATE_APP)
        .setCreateAppDetails(PlayPublishingEvent.CreateAppDetails.newBuilder().setCreateAppResult(result))
    )
  }

  fun trackCreateRelease(result: CreateReleaseResult, releaseTrackType: TrackType? = null, uploadTimeMs: Int? = null) {
    val createReleaseDetails = PlayPublishingEvent.CreateReleaseDetails.newBuilder().setCreateReleaseResult(result)
    releaseTrackType?.let { createReleaseDetails.setTrackType(it) }
    uploadTimeMs?.let { createReleaseDetails.setTimeToUploadBundleMs(it) }
    trackEvent(
      PlayPublishingEvent.newBuilder()
        .setEventType(PlayPublishingEventType.CREATE_RELEASE)
        .setCreateReleaseDetails(createReleaseDetails)
    )
  }

  private fun trackEvent(playPublishingEvent: PlayPublishingEvent.Builder) {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.PLAY_PUBLISHING_EVENT)
        .setPlayPublishingEvent(playPublishingEvent)
    )
  }
}

private fun AppPublishingSource.toWizardInvocationSource(): WizardInvocationSource =
  when (this) {
    AppPublishingSource.EXPORT_SIGNED_PACKAGE_WIZARD -> WizardInvocationSource.EXPORT_SIGNED_PACKAGE_WIZARD
    AppPublishingSource.BUILD_MENU -> WizardInvocationSource.BUILD_MENU
  }

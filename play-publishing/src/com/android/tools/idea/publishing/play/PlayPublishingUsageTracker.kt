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
import com.android.tools.idea.gservices.DevServiceDeprecationInfoBuilder
import com.android.tools.idea.gservices.DevServicesDeprecationStatus
import com.android.tools.idea.publishing.AppPublishingSource
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DevServiceDeprecationInfo.DeliveryType
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.CreateAppDetails.CreateAppResult
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.CreateReleaseDetails.CreateReleaseResult
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.CreateReleaseDetails.TrackType
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.PlayPublishingEventType
import com.google.wireless.android.sdk.stats.PlayPublishingEvent.WizardShownDetails.WizardInvocationSource
import com.google.wireless.android.sdk.stats.PlayPublishingEventKt
import com.google.wireless.android.sdk.stats.PlayPublishingEventKt.chooseBundleDetails
import com.google.wireless.android.sdk.stats.PlayPublishingEventKt.createAppDetails
import com.google.wireless.android.sdk.stats.PlayPublishingEventKt.createReleaseDetails
import com.google.wireless.android.sdk.stats.PlayPublishingEventKt.wizardShownDetails
import com.google.wireless.android.sdk.stats.androidStudioEvent
import com.google.wireless.android.sdk.stats.playPublishingEvent
import com.google.wireless.android.sdk.stats.studioDeprecationNotificationEvent

object PlayPublishingUsageTracker {

  fun trackWizardShown(source: AppPublishingSource) {
    trackEvent {
      eventType = PlayPublishingEventType.WIZARD_SHOWN
      wizardShownDetails = wizardShownDetails { invocationSource = source.toWizardInvocationSource() }
    }
  }

  fun trackChooseBundle(
    isPackageRegistered: Boolean?,
    isAppNameRead: Boolean,
    isPackageNameRead: Boolean,
    isVersionCodeRead: Boolean,
    isVersionNameRead: Boolean,
  ) {
    trackEvent {
      eventType = PlayPublishingEventType.CHOOSE_BUNDLE
      chooseBundleDetails = chooseBundleDetails {
        packageRegistered = isPackageRegistered ?: false
        appNameRead = isAppNameRead
        packageNameRead = isPackageNameRead
        versionCodeRead = isVersionCodeRead
        versionNameRead = isVersionNameRead
      }
    }
  }

  fun trackCreateApp(result: CreateAppResult) {
    trackEvent {
      eventType = PlayPublishingEventType.CREATE_APP
      createAppDetails = createAppDetails { createAppResult = result }
    }
  }

  fun trackCreateRelease(result: CreateReleaseResult, releaseTrackType: TrackType? = null, uploadTimeMs: Int? = null) {
    trackEvent {
      eventType = PlayPublishingEventType.CREATE_RELEASE
      createReleaseDetails = createReleaseDetails {
        createReleaseResult = result
        releaseTrackType?.let { trackType = it }
        uploadTimeMs?.let { timeToUploadBundleMs = it }
      }
    }
  }

  fun trackDeprecation(
    status: DevServicesDeprecationStatus,
    userNotified: Boolean? = null,
    moreInfoClicked: Boolean? = null,
    updateClicked: Boolean? = null,
    dismissed: Boolean? = null,
  ) {
    UsageTracker.log(
      androidStudioEvent {
        kind = AndroidStudioEvent.EventKind.STUDIO_DEPRECATION_NOTIFICATION_EVENT
        studioDeprecationNotificationEvent = studioDeprecationNotificationEvent {
          devServiceDeprecationInfo =
            DevServiceDeprecationInfoBuilder(
              deprecationStatus = status,
              deliveryType = DeliveryType.BANNER,
              userNotified = userNotified,
              moreInfoClicked = moreInfoClicked,
              updateClicked = updateClicked,
              deliveryDismissed = dismissed,
            )
        }
      }
    )
  }

  private fun trackEvent(block: PlayPublishingEventKt.Dsl.() -> Unit) {
    UsageTracker.log(
      androidStudioEvent {
        kind = AndroidStudioEvent.EventKind.PLAY_PUBLISHING_EVENT
        playPublishingEvent = playPublishingEvent(block)
      }
    )
  }
}

private fun AppPublishingSource.toWizardInvocationSource(): WizardInvocationSource =
  when (this) {
    AppPublishingSource.EXPORT_SIGNED_PACKAGE_WIZARD -> WizardInvocationSource.EXPORT_SIGNED_PACKAGE_WIZARD
    AppPublishingSource.BUILD_MENU -> WizardInvocationSource.BUILD_MENU
  }

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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gservices.DevServicesDeprecationDataProvider
import com.android.tools.idea.publishing.AppPublisher
import com.android.tools.idea.publishing.AppPublisherAvailability
import com.android.tools.idea.publishing.AppPublishingContext
import com.android.tools.idea.publishing.AppPublishingSource
import com.android.tools.idea.publishing.play.wizard.showPublishingWizard
import com.intellij.openapi.project.Project

private const val PLAY_PUBLISHER_ID = "Google Play"

class PlayAppPublisher : AppPublisher {

  override val id: String = PLAY_PUBLISHER_ID

  override val displayName: String = PLAY_PUBLISHER_ID

  override fun isPublisherAvailable(source: AppPublishingSource): AppPublisherAvailability {
    return when (source) {
      AppPublishingSource.EXPORT_SIGNED_PACKAGE_WIZARD -> {
        if (!StudioFlags.PLAY_PUBLISHING_WIZARD_INTEGRATION.get()) {
          AppPublisherAvailability.FlagDisabled
        } else {
          val deprecationData =
            DevServicesDeprecationDataProvider.getInstance().getCurrentDeprecationData("play/publishing", "Google Play Publishing")
          when {
            deprecationData.isUnsupported() ->
              AppPublisherAvailability.Unsupported(header = deprecationData.header, description = deprecationData.description)
            deprecationData.isDeprecated() -> AppPublisherAvailability.Deprecated
            else -> AppPublisherAvailability.Available
          }
        }
      }
      // Build menu action is always available if the flag is enabled
      AppPublishingSource.BUILD_MENU ->
        if (StudioFlags.PLAY_PUBLISHING_BUILD_MENU_ACTION.get()) {
          AppPublisherAvailability.Available
        } else {
          AppPublisherAvailability.FlagDisabled
        }
    }
  }

  override fun publishApp(project: Project, context: AppPublishingContext) {
    showPublishingWizard(project, context)
  }
}

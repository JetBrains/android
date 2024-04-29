/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.vitals

import com.google.gct.login2.LoginFeature
import icons.StudioIllustrations

class VitalsLoginFeature : LoginFeature {
  override val name = "Android Vitals"
  override val infoUrl = "https://play.google.com/console/developers/app/vitals/"
  override val infoUrlDisplayText = "Go to Play Console"
  override val settingsAction = null
  override val description =
    "See metrics and data about the apps in your Google Play Developer account. Used in " +
      "Android Vitals integration in App Quality Insights."
  override val oAuthScopes = listOf("https://www.googleapis.com/auth/playdeveloperreporting")

  override val onboardingWizardEntry: LoginFeature.OnboardingWizardEntry
    get() =
      LoginFeature.OnboardingWizardEntry(
        icon = StudioIllustrations.Common.PLAY_STORE,
        name = "<b>Google Play:</b> Enable viewing Android Vitals crash reports",
        description =
          """
          Android Vitals is a Google Play service that helps you discover
           and address top stability issues for your app. Enable this service
            to access detailed crash reports from Android Vitals directly from the IDE.
        """
            .trimIndent(),
      )
}

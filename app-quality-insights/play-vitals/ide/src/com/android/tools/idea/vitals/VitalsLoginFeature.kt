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

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.google.gct.login2.LoginFeature
import icons.StudioIllustrations
import icons.StudioIllustrationsCompose
import javax.swing.Icon
import org.jetbrains.jewel.ui.icon.IconKey

class VitalsLoginFeature : LoginFeature {
  override val key: String = "Android Vitals"
  override val title = "Android Vitals"
  override val infoUrl = "https://play.google.com/console/developers/app/vitals/"
  override val infoUrlDisplayText = "Go to Play Console"
  override val settingsAction = null
  override val description =
    "See metrics and data about the apps in your Google Play Developer account. Used in " +
      "Android Vitals integration in App Quality Insights."
  override val oAuthScopes = listOf("https://www.googleapis.com/auth/playdeveloperreporting")

  override val onboardingWizardEntry: LoginFeature.OnboardingWizardEntry
    get() =
      object : LoginFeature.OnboardingWizardEntry {
        override val icon: Icon = StudioIllustrations.Common.PLAY_STORE
        override val composeIconKey: IconKey = StudioIllustrationsCompose.Common.PlayStore
        override val title: String =
          "<b>Google Play:</b> Enable viewing Android Vitals crash reports"
        override val annotatedTitle: AnnotatedString = buildAnnotatedString {
          withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) { append("Google Play:") }
          append(" Enable viewing Android Vitals crash reports")
        }
        override val description: String =
          "Android Vitals is a Google Play service that helps you discover" +
            " and address top stability issues for your app. Enable this service" +
            " to access detailed crash reports from Android Vitals directly from the IDE."
      }
}

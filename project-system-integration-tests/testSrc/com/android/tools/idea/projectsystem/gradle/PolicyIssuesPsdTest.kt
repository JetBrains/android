/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import org.junit.Test

// TODO(b/289275521): This tests to be removed once google.play.sdk.index.show.sdk.policy.issues is removed
class PolicyIssuesPsdTest : SdkIndexTestBase() {
  @Test
  fun `policy issues not shown when flag false`() {
    system.installation.addVmOption("-Dgoogle.play.sdk.index.show.sdk.policy.issues=false")
    system.installation.addVmOption("-Didea.log.debug.categories=#com.android.tools.idea.gradle.structure.daemon.PsAnalyzerDaemon")
    verifySdkIndexIsInitializedAndUsedWhen(
      showFunction = { studio, _ ->
        openAndClosePSD(studio)
      },
      beforeClose = {
        verifyPsdIssues(numErrors = 3, numWarnings = 3)
      },
      expectedIssues = listOf(
        // Error
        listOf(
          "**[Prevents app release in Google Play Console]** com.google.android.gms:play-services-ads-lite version 19.4.0 has been reported as problematic by its author and will block publishing of your app to Play Console.",
          "**Note:** As of June 30th 2023, this version is sunset. For more information, please visit https://developers.google.com/admob/android/deprecation.",
        ),
        // Error (not shown since it is a policy issue, but is logged)
        listOf(
          "**[Prevents app release in Google Play Console]** com.startapp:inapp-sdk version 3.9.1 has Permissions policy issues that will block publishing of your app to Play Console.",
          "The library author recommends using versions:",
          "  - From 4.10.0 to 4.10.8",
          "  - 4.10.11 or higher",
          "These versions have not been reviewed by Google Play. They could contain vulnerabilities or policy violations. Carefully evaluate any third-party SDKs before integrating them into your app.",
        ),
        // Error
        listOf(
          "**[Prevents app release in Google Play Console]** com.startapp:inapp-sdk version 3.9.1 has been reported as problematic by its author and will block publishing of your app to Play Console.",
          "**Note:** Critical issue has been identified which causes intensive battery consumption.",
        ),
        // Error
        listOf(
          "**[Prevents app release in Google Play Console]** com.startapp:inapp-sdk version 3.9.1 has been reported as outdated by its author and will block publishing of your app to Play Console.",
          "The library author recommends using versions:", "  - From 4.10.0 to 4.10.8", "  - 4.10.11 or higher",
          "These versions have not been reviewed by Google Play. They could contain vulnerabilities or policy violations. Carefully evaluate any third-party SDKs before integrating them into your app.",
        ),
        // Warning
        listOf(
          "androidx.annotation:annotation version 1.0.0 has been reported as outdated by its author.",
          "The library author recommends using versions:",
          "  - 1.0.1 or higher",
        ),
        // Warning
        listOf(
          "com.google.ads.interactivemedia.v3:interactivemedia version 3.18.2 has been reported as outdated by its author.",
          "The library author recommends using versions:",
          "  - From 3.19.0 to 3.29.0",
          "  - From 3.30.2 to 3.31.0",
          "  - 3.33.0 or higher",
        ),
        // Warning
        listOf(
          "io.objectbox:objectbox-android version 2.5.1 has been reported as outdated by its author.",
          "The library author recommends using versions:",
          "  - 2.6.0 or higher",
          "These versions have not been reviewed by Google Play. They could contain vulnerabilities or policy violations. Carefully evaluate any third-party SDKs before integrating them into your app.",
        ),
        // Warning (not shown since it is a policy issue, but is logged)
        listOf(
          "com.paypal.android.sdk:data-collector version 3.20.0 has Permissions policy issues that will block publishing of your app to Play Console in the future.",
          "The library author recommends using versions:",
          "  - 3.20.1 or higher",
          "These versions have not been reviewed by Google Play. They could contain vulnerabilities or policy violations. Carefully evaluate any third-party SDKs before integrating them into your app.",
        ),
        // Info
        listOf(
          "com.google.android.gms:play-services-safetynet version 10.0.0 has an associated message from its author.",
          "**Note:** The SafetyNet Attestation API is being discontinued and replaced by the new Play Integrity API. Begin migration as soon as possible to avoid user disruption. The Play Integrity API includes all the integrity signals that SafetyNet Attestation offers and more, like Google Play licensing and better error messaging. Learn more and start migrating at https://developer.android.com/training/safetynet/deprecation-timeline",
        ),
        // Info
        listOf(
          "io.objectbox:objectbox-android version 2.5.1 has an associated message from its author.",
          "**Note:** This version has known issues that have been fixed in more recent versions. We also have made improvements to the API, performance and compatibility with devices. See https://docs.objectbox.io for details. Please upgrade to version 3.3.1 or newer (compatible with Android Plugin 3.4.0 or newer and Gradle 6.1 or newer). Note that newer versions are available on Maven Central.",
        ),
      )
    )
  }
}

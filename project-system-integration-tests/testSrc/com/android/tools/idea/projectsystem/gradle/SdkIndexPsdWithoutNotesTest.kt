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
package com.android.tools.idea.projectsystem.gradle

import org.junit.Test

class SdkIndexPsdWithoutNotesTest : SdkIndexTestBase() {
  @Test
  fun `Snapshot used by PSD without notes`() {
    system.installation.addVmOption("-Didea.log.debug.categories=#com.android.tools.idea.gradle.structure.daemon.PsAnalyzerDaemon")
    system.installation.addVmOption("-Dgoogle.play.sdk.index.show.sdk.index.notes=false")
    system.installation.addVmOption("-Dgoogle.play.sdk.index.show.sdk.index.recommended.versions=true")
    verifySdkIndexIsInitializedAndUsedWhen(
      showFunction = { studio, _ ->
        openAndClosePSD(studio)
      },
      beforeClose = {
        verifyPsdIssues(numErrors = 4, numWarnings = 4)
      },
      expectedIssues = listOf(
        // Error
        listOf(
          "**[Prevents app release in Google Play Console]** com.google.android.gms:play-services-ads-lite version 19.4.0 has been reported as problematic by its author and will block publishing of your app to Play Console",
        ),
        // Error
        listOf(
          "**[Prevents app release in Google Play Console]** com.startapp:inapp-sdk version 3.9.1 has Permissions policy issues that will block publishing of your app to Play Console.",
          "The library author recommends using versions:",
          "  - From 4.10.0 to 4.10.8",
          "  - 4.10.11 or higher",
          "These versions have not been reviewed by Google Play. They could contain vulnerabilities or policy violations. Carefully evaluate any third-party SDKs before integrating them into your app.",
        ),
        // Error
        listOf(
          "**[Prevents app release in Google Play Console]** com.startapp:inapp-sdk version 3.9.1 has been reported as problematic by its author and will block publishing of your app to Play Console",
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
          "These versions have not been reviewed by Google Play. They could contain vulnerabilities or policy violations. Carefully evaluate any third-party SDKs before integrating them into your app.",
        ),
        // Warning
        listOf(
          "com.google.ads.interactivemedia.v3:interactivemedia version 3.18.2 has been reported as outdated by its author.",
          "The library author recommends using versions:",
          "  - From 3.19.0 to 3.29.0",
          "  - From 3.30.2 to 3.31.0",
          "  - 3.33.0 or higher",
          "These versions have not been reviewed by Google Play. They could contain vulnerabilities or policy violations. Carefully evaluate any third-party SDKs before integrating them into your app.",
        ),
        // Warning
        listOf(
          "io.objectbox:objectbox-android version 2.5.1 has been reported as outdated by its author.",
          "The library author recommends using versions:",
          "  - 2.6.0 or higher",
          "These versions have not been reviewed by Google Play. They could contain vulnerabilities or policy violations. Carefully evaluate any third-party SDKs before integrating them into your app.",
        ),
        // Warning
        listOf(
          "com.paypal.android.sdk:data-collector version 3.20.0 has Permissions policy issues that will block publishing of your app to Play Console in the future.",
          "The library author recommends using versions:",
          "  - 3.20.1 or higher",
          "These versions have not been reviewed by Google Play. They could contain vulnerabilities or policy violations. Carefully evaluate any third-party SDKs before integrating them into your app.",
        ),
        // Info
        listOf(
          "com.google.android.gms:play-services-safetynet version 10.0.0 has an associated message from its author",
        ),
        // Info
        listOf(
          "io.objectbox:objectbox-android version 2.5.1 has an associated message from its author",
        ),
      )
    )
  }
}

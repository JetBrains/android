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

class SdkIndexPsdTest : SdkIndexTestBase() {
  @Test
  fun snapshotUsedByPsdTest() {
    system.installation.addVmOption("-Didea.log.debug.categories=#com.android.tools.idea.gradle.structure.daemon.PsAnalyzerDaemon")
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
          "**[Prevents app release in Google Play Console]** com.startapp:inapp-sdk version 3.9.1 has Permissions policy issues that will block publishing of your app to Play Console",
        ),
        // Error
        listOf(
          "**[Prevents app release in Google Play Console]** com.startapp:inapp-sdk version 3.9.1 has been reported as problematic by its author and will block publishing of your app to Play Console",
        ),
        // Error
        listOf(
          "**[Prevents app release in Google Play Console]** com.startapp:inapp-sdk version 3.9.1 has been reported as outdated by its author and will block publishing of your app to Play Console",
        ),
        // Warning
        listOf(
          "androidx.annotation:annotation version 1.0.0 has been reported as outdated by its author",
        ),
        // Warning
        listOf(
          "com.google.ads.interactivemedia.v3:interactivemedia version 3.18.2 has been reported as outdated by its author",
        ),
        // Warning
        listOf(
          "io.objectbox:objectbox-android version 2.5.1 has been reported as outdated by its author",
        ),
        // Warning
        listOf(
          "com.paypal.android.sdk:data-collector version 3.20.0 has Permissions policy issues that will block publishing of your app to Play Console in the future",
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

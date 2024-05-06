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
        // Two errors and one warning should be shown:
        //   - com.startapp:inapp-sdk:3.9.1 error (blocking critical)
        //   - com.startapp:inapp-sdk:3.9.1 error (blocking outdated)
        //   - com.mopub:mopub-sdk:4.16.0 warning (outdated)
        verifyPsdIssues(numWarnings = 1)
      },
      expectedIssues = setOf(
        "com.mopub:mopub-sdk version 4.16.0 has been marked as outdated by its author",
        "com.snowplowanalytics:snowplow-android-tracker version 1.4.1 has an associated message from its author",
        "com.startapp:inapp-sdk version 3.9.1 has been reported as problematic by its author and will block publishing of your app to Play Console",
        "com.startapp:inapp-sdk version 3.9.1 has been marked as outdated by its author and will block publishing of your app to Play Console",
        "com.stripe:stripe-android version 9.3.2 has policy issues that will block publishing of your app to Play Console in the future",
      )
    )
  }
}

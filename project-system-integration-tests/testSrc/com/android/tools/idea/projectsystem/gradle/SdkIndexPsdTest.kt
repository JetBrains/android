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
    verifySdkIndexIsInitializedAndUsedWhen(
      showFunction = { studio, _ ->
        // Open PSD using the menu since we can't use executeAction("AndroidShowStructureSettingsAction") here because it would spawn a
        // modal dialog with nothing to close it.
        studio.invokeComponent("File")
        studio.invokeComponent("Project Structure...")
      },
      closeFunction = { studio, _ ->
        // Close PSD
        studio.invokeComponent("OK")
      },
      expectedIssues = setOf(
        "com.mopub:mopub-sdk version 4.16.0 has been marked as outdated by its author",
        "com.startapp:inapp-sdk version 3.9.1 has been marked as outdated by its author",
        "com.snowplowanalytics:snowplow-android-tracker version 1.4.1 has an associated message from its author",
      )
    )
  }
}
/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.issues

import com.android.tools.idea.gradle.project.sync.issues.TestSyncIssueUsageReporter
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncIssueType
import java.io.File

class EmptyDimensionSyncErrorTest : AndroidGradleTestCase() {
  fun testSyncErrorOnEmptyFavorDimension() {
    val projectRoot = loadSimpleApplication()
    val buildFile = File(projectRoot, "app/build.gradle")
    buildFile.appendText(
      """
        android {
          flavorDimensions 'flv_dim1', 'flv_dim3'
          productFlavors {
            flv1 {
                dimension 'flv_dim1'
            }
          }
        }
      """.trimIndent()
    )
    val usageReporter = TestSyncIssueUsageReporter.replaceSyncMessagesService(project)

    val message: String = requestSyncAndGetExpectedFailure()
    assertThat(message).startsWith("No variants found for ':app'. Check ${buildFile.absolutePath} to ensure at least one variant exists and address any sync warnings and errors.")
    assertThat(usageReporter.collectedGradleSyncIssue?.type).isEqualTo(GradleSyncIssueType.TYPE_EMPTY_FLAVOR_DIMENSION)
  }
}
/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.builder.model.SyncIssue
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueUsageReporter.Companion.toGradleSyncIssueType
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [SyncIssueUsageReporter]
 */
class SyncIssueUsageReporterTest : AndroidGradleTestCase() {
  @Test
  fun testAllSyncIssueTypesHaveGradleSyncIssueType() {
    val nonGenericSyncIssues = SyncIssue::class.java.fields.filter { it.name.startsWith("TYPE_") && it.name != "TYPE_GENERIC" }
    for (syncIssue in nonGenericSyncIssues) {
      assertThat(syncIssue.getInt(SyncIssue::class.java).toGradleSyncIssueType())
        .named("SyncIssue.${syncIssue.name}.toGradleSyncIssueType()")
        .isNotNull()
    }
  }

  /**
   * Test that SyncIssues and IdeSyncIssues match.
   */
  @Test
  fun testSyncIssuesMatchIdeSyncIssues() {
    val syncIssues =
      SyncIssue::class.java.fields.filter { it.name.startsWith("TYPE_") }.map { Pair(it.name, it.getInt(SyncIssue::class.java)) }
    val ideSyncIssues =
      IdeSyncIssue::class.java.fields.filter { it.name.startsWith("TYPE_") }.map { Pair(it.name, it.getInt(IdeSyncIssue::class.java)) }
    assertThat(syncIssues).containsExactlyElementsIn(ideSyncIssues)
  }
}

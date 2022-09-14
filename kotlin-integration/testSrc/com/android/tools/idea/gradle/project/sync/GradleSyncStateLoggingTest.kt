/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync;

import com.android.testutils.TestUtils
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.testFramework.RunsInEdt
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class GradleSyncStateLoggingTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(tracker)
  }

  @Test
  fun testKotlinLogging() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.UNIT_TESTING)
    preparedProject.open { project ->

      val proto = tracker.usages
        .map { it.studioEvent }
        .last { it.kind == AndroidStudioEvent.EventKind.GRADLE_SYNC_ENDED }
        .kotlinSupport

      assertEquals(TestUtils.KOTLIN_VERSION_FOR_TESTS, proto.kotlinSupportVersion)
      assertFalse(proto.hasAndroidKtxVersion())
      // TODO(b/71803185): test for KTX once we have in prebuilts and can sync a project that uses it.
    }
  }
}

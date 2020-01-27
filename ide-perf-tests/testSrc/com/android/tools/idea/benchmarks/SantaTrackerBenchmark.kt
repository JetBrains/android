/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.benchmarks

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.openapi.fileTypes.StdFileTypes
import org.junit.BeforeClass
import org.junit.ClassRule

/**
 * Runs the FullProjectBenchmark tests on an old version of the Santa Tracker project which has only Java and XML files.
 */
class SantaTrackerBenchmark : FullProjectBenchmark() {
  companion object {
    @JvmField
    @ClassRule
    val staticRule = AndroidGradleProjectRule()
    const val PROJECT_NAME = "SantaTracker"

    @JvmStatic
    @BeforeClass
    fun setUpBeforeClass() {
      loadProject(staticRule, PROJECT_NAME)
    }
  }

  override val gradleRule = staticRule
  override val projectName = PROJECT_NAME
  override val fileTypes = listOf(StdFileTypes.JAVA, StdFileTypes.XML)
}
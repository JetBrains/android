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
package com.android.tools.idea.gradle.project.sync.memory

import com.android.tools.idea.gradle.project.sync.SUBSET_1000_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_100_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_2000_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_200_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_4200_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_500_NAME
import com.android.tools.idea.gradle.project.sync.SUBSET_50_NAME
import com.android.tools.idea.gradle.project.sync.mutateGradleProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import org.junit.rules.ExternalResource
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter


val SUBSET_TO_MAX_HEAP_MB = mapOf(
  SUBSET_50_NAME to 400,
  SUBSET_100_NAME to 600,
  SUBSET_200_NAME to 1300,
  SUBSET_500_NAME to 3600,
  SUBSET_1000_NAME to 9000,
  SUBSET_2000_NAME to 22000,
  SUBSET_4200_NAME to 60000
)
class MemoryConstrainedTestRule(
  private val projectName: String,
) : ExternalResource() {
  override fun before() {
    val memoryLimitMb = SUBSET_TO_MAX_HEAP_MB[projectName]!!
    mutateGradleProperties {
      setJvmArgs(jvmArgs.orEmpty().replace("-Xmx60g", "-Xmx${memoryLimitMb}m"))
    }
    startMemoryPolling()
    recordMemoryMeasurement("${projectName}_Max_Heap", value = memoryLimitMb.toLong() shl 20)
  }

  private fun startMemoryPolling() {
    // This is used just for logging and diagnosing issues in the test
    CoroutineScope(Dispatchers.IO).launch {
      while (true) {
        File("/proc/meminfo").readLines().filter { it.startsWith("Mem") }.forEach {
          // This will have MemAvailable, MemFree, MemTotal lines
          println("${getTimestamp()} - $it")
        }
        delay(Duration.ofSeconds(15))
      }
    }
  }

  private fun getTimestamp() = DateTimeFormatter
    .ofPattern("yyyy-MM dd-HH:mm:ss.SSS")
    .withZone(ZoneOffset.UTC)
    .format(Instant.now())
}
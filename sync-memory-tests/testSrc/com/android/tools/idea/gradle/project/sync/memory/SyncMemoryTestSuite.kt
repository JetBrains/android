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
package com.android.tools.idea.gradle.project.sync.memory

import org.junit.Rule
import org.junit.Test

class Benchmark50MemoryTest : MemoryBenchmarkTestSuite() {
  @get:Rule
  val memoryUsageBenchmarkRule = MemoryUsageBenchmarkRule(
    projectRule,
    projectName = "50Modules",
    memoryLimitMb = 400,
    lightweightMode = false
  )

  init {
    setUpProject("diff-50")
  }

  @Test
  fun testSyncMemory() {
    memoryUsageBenchmarkRule.openProjectAndMeasure()
  }
}

class Benchmark100MemoryTest : MemoryBenchmarkTestSuite() {
  @get:Rule
  val memoryUsageBenchmarkRule = MemoryUsageBenchmarkRule(
    projectRule,
    projectName = "100Modules",
    memoryLimitMb = 600,
    lightweightMode = false
  )

  init {
    setUpProject("diff-100")
  }

  @Test
  fun testSyncMemory() {
    memoryUsageBenchmarkRule.openProjectAndMeasure()
  }
}

class Benchmark200MemoryTest : MemoryBenchmarkTestSuite() {
  @get:Rule
  val memoryUsageBenchmarkRule = MemoryUsageBenchmarkRule(
    projectRule,
    projectName = "200Modules",
    memoryLimitMb = 1300,
    lightweightMode = false,
  )

  init {
    setUpProject("diff-200")
  }

  @Test
  fun testSyncMemory() {
    memoryUsageBenchmarkRule.openProjectAndMeasure()
  }
}

class Benchmark500MemoryTest : MemoryBenchmarkTestSuite() {
  @get:Rule
  val memoryUsageBenchmarkRule = MemoryUsageBenchmarkRule(
    projectRule,
    projectName = "500Modules",
    memoryLimitMb = 4200,
    lightweightMode = false
  )

  init {
    setUpProject("diff-500")
  }

  @Test
  fun testSyncMemory() {
    memoryUsageBenchmarkRule.openProjectAndMeasure()
  }
}

class Benchmark1000MemoryTest : MemoryBenchmarkTestSuite() {
  @get:Rule
  val memoryUsageBenchmarkRule = MemoryUsageBenchmarkRule(
    projectRule,
    projectName = "1000Modules",
    memoryLimitMb = 9000,
    lightweightMode = false
  )

  init {
    setUpProject("diff-1000")
  }

  @Test
  fun testSyncMemory() {
    memoryUsageBenchmarkRule.openProjectAndMeasure()
  }
}

class Benchmark2000MemoryTest : MemoryBenchmarkTestSuite() {
  @get:Rule
  val memoryUsageBenchmarkRule = MemoryUsageBenchmarkRule(
    projectRule,
    projectName = "2000Modules",
    memoryLimitMb = 25000,
    lightweightMode = true
  )

  init {
    setUpProject("diff-app")
  }

  @Test
  fun testSyncMemory() {
    memoryUsageBenchmarkRule.openProjectAndMeasure()
  }
}

class BenchmarkXLMemoryTest : MemoryBenchmarkTestSuite() {
  @get:Rule
  val memoryUsageBenchmarkRule = MemoryUsageBenchmarkRule(
    projectRule,
    projectName = "BenchmarkXL",
    memoryLimitMb = 60000,
    lightweightMode = true
  )

  init {
    setUpProject() // no diff
  }

  @Test
  fun testSyncMemory() {
    memoryUsageBenchmarkRule.openProjectAndMeasure()
  }
}
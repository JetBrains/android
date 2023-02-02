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

class Benchmark50MemoryTest : AbstractGradleSyncMemoryUsageTestCase() {
  override val projectName = "50Modules"
  override val memoryLimitMb = 400

  init {
    setUpProject("diff-50")
  }
}
class Benchmark100MemoryTest : AbstractGradleSyncMemoryUsageTestCase() {
  override val projectName = "100Modules"
  override val memoryLimitMb = 600

  init {
    setUpProject("diff-100")
  }
}

class Benchmark200MemoryTest : AbstractGradleSyncMemoryUsageTestCase() {
  override val projectName = "200Modules"
  override val memoryLimitMb = 1300

  init {
    setUpProject("diff-200")
  }
}

class Benchmark500MemoryTest : AbstractGradleSyncMemoryUsageTestCase() {
  override val projectName = "500Modules"
  override val memoryLimitMb = 4000

  init {
    setUpProject("diff-500")
  }
}

class Benchmark1000MemoryTest : AbstractGradleSyncMemoryUsageTestCase() {
  override val projectName = "1000Modules"
  override val memoryLimitMb = 9000

  init {
    setUpProject("diff-1000")
  }
}

class Benchmark2000MemoryTest : AbstractGradleSyncMemoryUsageTestCase() {
  override val projectName = "2000Modules"
  override val memoryLimitMb = 20000

  init {
    setUpProject("diff-app")
  }
}

class BenchmarkXLMemoryTest : AbstractGradleSyncMemoryUsageTestCase() {
  override val projectName = "BenchmarkXL"
  override val memoryLimitMb = 40000

  init {
    setUpProject() // no diff
  }
}
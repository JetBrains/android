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
package com.android.tools.idea.benchmarks

import com.android.tools.perflogger.Metric
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInInitMode
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

const val EDITOR_PERFGATE_PROJECT_NAME = "Android Studio Editor"

/** Enables all inspections which are on by default in production. */
fun enableAllDefaultInspections(fixture: CodeInsightTestFixture) {
  val profileManager = ProjectInspectionProfileManager.getInstance(fixture.project)
  val defaultProfile = profileManager.getProfile("Project Default", false)!!
  val tools = runInInitMode { defaultProfile.getAllEnabledInspectionTools(fixture.project) }
  val inspectionEntries = tools.map { it.tool.tool }
  fixture.enableInspections(*inspectionEntries.toTypedArray())
}

/**
 * Blocks until all pending async VFS refreshes have completed.
 * Must *not* be called from the EDT.
 *
 * This helps ensure that a VFS refresh does not happen *during* a benchmark, which
 * can trigger assertion failures in the platform and would anyway add noise to the
 * timing samples.
 */
fun waitForAsyncVfsRefreshes() {
  assert(!ApplicationManager.getApplication().isDispatchThread) {
    "Cannot block on an async VFS refresh from the EDT (otherwise deadlock is imminent)"
  }
  // Here we assume that the RefreshQueue uses FIFO ordering.
  val semaphore = Semaphore(0)
  RefreshQueue.getInstance().refresh(true, false, Runnable(semaphore::release))
  semaphore.acquire()
}

/** Runs [action] several times to warm up, and then several times again. */
fun repeatWithWarmups(
  warmupIterations: Int,
  mainIterations: Int,
  action: (isWarmup: Boolean) -> Unit
) {
  repeat(warmupIterations) {
    val time = measureElapsedMillis { action(true) }
    println("Warmup phase: $time ms")
  }
  repeat(mainIterations) {
    val time = measureElapsedMillis { action(false) }
    println("Main phase: $time ms")
  }
}

/**
 * Runs [action] several times to warm up, and then several times again to measure elapsed time.
 * Returns an array containing a [Metric.MetricSample] for each iteration.
 *
 * Note: if you need more control over time measurement, just use [repeatWithWarmups] directly.
 */
fun measureTimeMs(
  warmupIterations: Int,
  mainIterations: Int,
  setUp: () -> Unit = {},
  action: () -> Unit,
  tearDown: () -> Unit = {}
): List<Metric.MetricSample> {
  val samplesMs = ArrayList<Metric.MetricSample>(mainIterations)
  repeatWithWarmups(
    warmupIterations = warmupIterations,
    mainIterations = mainIterations,
    action = { isWarmup ->
      setUp()
      val timeMs = measureElapsedMillis { action() }
      if (!isWarmup) {
        samplesMs.add(Metric.MetricSample(System.currentTimeMillis(), timeMs))
      }
      tearDown()
    }
  )
  return samplesMs
}

/**
 * Like [measureTimeMillis], but uses System.nanoTime() under the hood.
 *
 * Justification: System.currentTimeMillis() is not guaranteed to be monotonic, so
 * measureTimeMillis() is not the most robust way to measure elapsed time.
 */
inline fun measureElapsedMillis(action: () -> Unit): Long = TimeUnit.NANOSECONDS.toMillis(measureNanoTime(action))

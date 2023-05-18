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
package com.android.tools.idea.gradle.project.sync.cpu

import com.android.tools.idea.gradle.project.sync.GRADLE_SYNC_TOPIC
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.annotations.SystemIndependent
import org.junit.rules.ExternalResource
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import kotlin.time.Duration
import kotlin.time.DurationUnit


val CPU_BENCHMARK = Benchmark.Builder("Cpu time")
  .setProject("Android Studio Sync Test")
  .build()
private data class ImportResult(
  var gradleDuration: Duration? = null,
  var totalDuration: Duration? = null
) {
  val ideDuration get() = totalDuration!! - gradleDuration!!
}
class MeasureSyncExecutionTimeRule(val syncCount: Int) : ExternalResource() {
  private var importResults = mutableListOf<ImportResult>()
  private lateinit var currentAttemptStart : Instant
  private lateinit var currentResult : ImportResult

  override fun before() {
    ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(object: ExternalSystemTaskNotificationListenerAdapter() {
      override fun onSuccess(id: ExternalSystemTaskId) {
        currentResult.gradleDuration = Clock.System.now() - currentAttemptStart
        println("Gradle build succeeded in ${currentResult.gradleDuration}")
      }
    })
  }

  val listeners = mapOf<Topic<GradleSyncListenerWithRoot>, GradleSyncListenerWithRoot>(
      GRADLE_SYNC_TOPIC to object : GradleSyncListenerWithRoot {
        override fun syncStarted(project: Project, rootProjectPath: @SystemIndependent String) {
          println("Project import started: attempt #${importResults.size + 1}")
          currentAttemptStart = Clock.System.now()
          currentResult = ImportResult()
        }

        override fun syncSucceeded(project: Project, rootProjectPath: @SystemIndependent String) {
          currentResult.totalDuration = Clock.System.now() - currentAttemptStart
          println("IDE import succeeded in ${currentResult.ideDuration}")
          println("Total:  ${currentResult.totalDuration}")
          importResults.add(currentResult)
        }
      }
  )
  fun recordMeasurements(projectName: String) {
    importResults.flatMapIndexed {  index, value ->
      val prefix = when (index) {
        0 -> "Initial_"
        1, 2 -> "Dropped_"
        else -> ""
      }
      listOf(
        "${prefix}Ide_Ms" to value.ideDuration,
        "${prefix}Total_Ms" to value.totalDuration!!,
        "${prefix}Gradle_Ms" to value.gradleDuration!!
    )}.groupBy { it.first }.entries.forEach {
      println("Recording ${projectName}_${it.key} -> ${it.value.map { it.second.toLong(DurationUnit.MILLISECONDS)}}")
      recordCpuMeasurement("${projectName}_${it.key}", it.value.map { it.second.toLong(DurationUnit.MILLISECONDS) })
    }
  }
}

internal fun recordCpuMeasurement(metricName: String, values: Iterable<Long>) {
  val currentTime = java.time.Instant.now().toEpochMilli()
  Metric(metricName).apply {
    values.forEach {
      addSamples(CPU_BENCHMARK, Metric.MetricSample(currentTime, it))
    }
    commit()
  }
}
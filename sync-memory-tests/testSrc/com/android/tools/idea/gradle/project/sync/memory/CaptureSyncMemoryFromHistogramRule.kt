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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.android.tools.idea.gradle.project.sync.memory

import com.android.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.gradle.MeasurementPluginConfig
import com.android.tools.idea.gradle.project.sync.mutateGradleProperties
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Files


class CaptureSyncMemoryFromHistogramRule(private val projectName: String) : ExternalResource() {
  override fun before() {
    StudioFlags.GRADLE_HEAP_ANALYSIS_OUTPUT_DIRECTORY.override(OUTPUT_DIRECTORY)
    StudioFlags.GRADLE_HEAP_ANALYSIS_LIGHTWEIGHT_MODE.override(true)

    mutateGradleProperties {
      setJvmArgs("$jvmArgs -XX:SoftRefLRUPolicyMSPerMB=0")
    }
    MeasurementPluginConfig.configureAndApply(OUTPUT_DIRECTORY)
  }

  override fun after() {
    recordHistogramValues(projectName)
    StudioFlags.GRADLE_HEAP_ANALYSIS_OUTPUT_DIRECTORY.clearOverride()
    StudioFlags.GRADLE_HEAP_ANALYSIS_LIGHTWEIGHT_MODE.clearOverride()
    File(OUTPUT_DIRECTORY).delete()
  }

  private fun recordHistogramValues(projectName: String) {
    for (metricFilePath in File(OUTPUT_DIRECTORY).walk().filter { !it.isDirectory }.asIterable()) {
        val bytes = metricFilePath.readLines()
          .last()
          .trim()
          .split("\\s+".toRegex())[2]
          .toLong()
        recordMemoryMeasurement("${projectName}_${metricFilePath.toMetricName()}", TimestampedMeasurement(
          metricFilePath.toTimestamp(),
          bytes
        ))
        Files.move(metricFilePath.toPath(), TestUtils.getTestOutputDir().resolve(metricFilePath.name))
    }
  }
}
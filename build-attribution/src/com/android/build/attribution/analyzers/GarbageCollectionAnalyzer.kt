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
package com.android.build.attribution.analyzers

import com.android.build.attribution.data.GarbageCollectionData
import com.android.buildanalyzer.common.AndroidGradlePluginAttributionData
import com.intellij.util.lang.JavaVersion
import org.jetbrains.kotlin.utils.addToStdlib.sumByLong

class GarbageCollectionAnalyzer :
  BaseAnalyzer<GarbageCollectionAnalyzer.Result>(), BuildAttributionReportAnalyzer {
  private var garbageCollectionData: List<GarbageCollectionData> = emptyList()
  private var javaVersion: Int? = null
  private var isSettingSet: Boolean? = null

  override fun cleanupTempState() {
    garbageCollectionData = emptyList()
    javaVersion = null
    isSettingSet = null
  }

  override fun receiveBuildAttributionReport(androidGradlePluginAttributionData: AndroidGradlePluginAttributionData) {
    garbageCollectionData = androidGradlePluginAttributionData.garbageCollectionData.map { GarbageCollectionData(it.key, it.value) }
    javaVersion = JavaVersion.tryParse(androidGradlePluginAttributionData.javaInfo.version)?.feature
    isSettingSet = androidGradlePluginAttributionData.javaInfo.vmArguments.any { it.isGcVmArgument() }
    ensureResultCalculated()
  }

  private fun String.isGcVmArgument(): Boolean = startsWith("-XX:+Use") && endsWith("GC")

  override fun calculateResult(): Result = Result(garbageCollectionData, javaVersion, isSettingSet)

  data class Result(
    val garbageCollectionData: List<GarbageCollectionData>,
    val javaVersion: Int?,
    val isSettingSet: Boolean?
  ) : AnalyzerResult {
    val totalGarbageCollectionTimeMs: Long
      get() = garbageCollectionData.sumByLong { it.collectionTimeMs }

  }
}

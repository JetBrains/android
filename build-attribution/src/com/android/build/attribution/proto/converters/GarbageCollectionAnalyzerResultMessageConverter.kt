/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build.attribution.proto.converters

import com.android.build.attribution.BuildAnalysisResultsMessage
import com.android.build.attribution.analyzers.GarbageCollectionAnalyzer
import com.android.build.attribution.data.GarbageCollectionData

class GarbageCollectionAnalyzerResultMessageConverter {
  companion object {
    fun transform(garbageCollectionAnalyzerResult: GarbageCollectionAnalyzer.Result)
      : BuildAnalysisResultsMessage.GarbageCollectionAnalyzerResult {
      val garbageCollectionAnalyzerResultBuilder = BuildAnalysisResultsMessage.GarbageCollectionAnalyzerResult.newBuilder()
      garbageCollectionAnalyzerResultBuilder.addAllGarbageCollectionData(
        (garbageCollectionAnalyzerResult.garbageCollectionData).map(Companion::transformGarbageCollectionDatum))
      if (garbageCollectionAnalyzerResult.javaVersion != null) {
        garbageCollectionAnalyzerResultBuilder.javaVersion = garbageCollectionAnalyzerResult.javaVersion
      }
      when (garbageCollectionAnalyzerResult.isSettingSet) {
        true -> garbageCollectionAnalyzerResultBuilder.isSettingSet = BuildAnalysisResultsMessage.GarbageCollectionAnalyzerResult.TrueFalseUnknown.TRUE
        false -> garbageCollectionAnalyzerResultBuilder.isSettingSet = BuildAnalysisResultsMessage.GarbageCollectionAnalyzerResult.TrueFalseUnknown.FALSE
        null -> garbageCollectionAnalyzerResultBuilder.isSettingSet = BuildAnalysisResultsMessage.GarbageCollectionAnalyzerResult.TrueFalseUnknown.UNKNOWN
      }
      return garbageCollectionAnalyzerResultBuilder.build()
    }

    fun construct(
      garbageCollectionAnalyzerResult: BuildAnalysisResultsMessage.GarbageCollectionAnalyzerResult
    ): GarbageCollectionAnalyzer.Result {
      val garbageCollectionData: MutableList<GarbageCollectionData> = mutableListOf()
      val isSettingSet = when (garbageCollectionAnalyzerResult.isSettingSet) {
        BuildAnalysisResultsMessage.GarbageCollectionAnalyzerResult.TrueFalseUnknown.TRUE -> true
        BuildAnalysisResultsMessage.GarbageCollectionAnalyzerResult.TrueFalseUnknown.FALSE -> false
        BuildAnalysisResultsMessage.GarbageCollectionAnalyzerResult.TrueFalseUnknown.UNKNOWN -> null
        BuildAnalysisResultsMessage.GarbageCollectionAnalyzerResult.TrueFalseUnknown.UNRECOGNIZED -> throw IllegalStateException(
          "Unrecognized setting state")
        null -> throw IllegalStateException("Unrecognized setting state")

      }
      val javaVersion = when (garbageCollectionAnalyzerResult.javaVersion) {
        0 -> null
        else -> garbageCollectionAnalyzerResult.javaVersion
      }
      garbageCollectionAnalyzerResult.garbageCollectionDataList.forEach {
        garbageCollectionData.add(GarbageCollectionData(it.name, it.collectionTimeMs))
      }
      return GarbageCollectionAnalyzer.Result(garbageCollectionData, javaVersion, isSettingSet)
    }

    private fun transformGarbageCollectionDatum(garbageCollectionDatum: GarbageCollectionData) = BuildAnalysisResultsMessage.GarbageCollectionAnalyzerResult.GarbageCollectionData.newBuilder()
      .setCollectionTimeMs(garbageCollectionDatum.collectionTimeMs)
      .setName(garbageCollectionDatum.name)
      .build()
  }
}
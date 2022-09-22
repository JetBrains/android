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
package com.android.build.attribution.proto

import com.android.build.attribution.BuildAnalysisResultsMessage.GarbageCollectionAnalyzerResult
import com.android.build.attribution.analyzers.GarbageCollectionAnalyzer
import com.android.build.attribution.data.GarbageCollectionData

class GarbageCollectionAnalyzerResultMessageConverter {
  companion object {
    fun transform(garbageCollectionAnalyzerResult: GarbageCollectionAnalyzer.Result)
      : GarbageCollectionAnalyzerResult {
      val garbageCollectionAnalyzerResultBuilder = GarbageCollectionAnalyzerResult.newBuilder()
      garbageCollectionAnalyzerResultBuilder.addAllGarbageCollectionData(
        (garbageCollectionAnalyzerResult.garbageCollectionData).map(::transformGarbageCollectionDatum))
      if (garbageCollectionAnalyzerResult.javaVersion != null) {
        garbageCollectionAnalyzerResultBuilder.javaVersion = garbageCollectionAnalyzerResult.javaVersion
      }
      when (garbageCollectionAnalyzerResult.isSettingSet) {
        true -> garbageCollectionAnalyzerResultBuilder.isSettingSet = GarbageCollectionAnalyzerResult.TrueFalseUnknown.TRUE
        false -> garbageCollectionAnalyzerResultBuilder.isSettingSet = GarbageCollectionAnalyzerResult.TrueFalseUnknown.FALSE
        null -> garbageCollectionAnalyzerResultBuilder.isSettingSet = GarbageCollectionAnalyzerResult.TrueFalseUnknown.UNKNOWN
      }
      return garbageCollectionAnalyzerResultBuilder.build()
    }

    fun construct(
      garbageCollectionAnalyzerResult: GarbageCollectionAnalyzerResult
    ): GarbageCollectionAnalyzer.Result {
      val garbageCollectionData: MutableList<GarbageCollectionData> = mutableListOf()
      val isSettingSet = when (garbageCollectionAnalyzerResult.isSettingSet) {
        GarbageCollectionAnalyzerResult.TrueFalseUnknown.TRUE -> true
        GarbageCollectionAnalyzerResult.TrueFalseUnknown.FALSE -> false
        GarbageCollectionAnalyzerResult.TrueFalseUnknown.UNKNOWN -> null
        GarbageCollectionAnalyzerResult.TrueFalseUnknown.UNRECOGNIZED -> throw IllegalStateException(
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

    private fun transformGarbageCollectionDatum(garbageCollectionDatum: GarbageCollectionData) = GarbageCollectionAnalyzerResult.GarbageCollectionData.newBuilder()
      .setCollectionTimeMs(garbageCollectionDatum.collectionTimeMs)
      .setName(garbageCollectionDatum.name)
      .build()
  }
}
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

import com.android.build.attribution.analyzers.GarbageCollectionAnalyzer
import com.android.build.attribution.data.GarbageCollectionData
import com.google.common.truth.Truth
import org.junit.Test

class GarbageCollectionAnalyzerResultMessageConverterTest {
  @Test
  fun testGarbageCollectionAnalyzerResult() {
    val result = GarbageCollectionAnalyzer.Result(listOf(GarbageCollectionData("name", 12345)), 12345, true)
    val resultMessage = GarbageCollectionAnalyzerResultMessageConverter.transform(result)
    val resultConverted = GarbageCollectionAnalyzerResultMessageConverter.construct(resultMessage)
    Truth.assertThat(result).isEqualTo(resultConverted)
  }

  @Test
  fun testGarbageCollectionAnalyzerResultNullValues() {
    val result = GarbageCollectionAnalyzer.Result(listOf(GarbageCollectionData("name", 12345)), null, null)
    val resultMessage = GarbageCollectionAnalyzerResultMessageConverter.transform(result)
    val resultConverted = GarbageCollectionAnalyzerResultMessageConverter.construct(resultMessage)
    Truth.assertThat(result).isEqualTo(resultConverted)
  }
}
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
package com.android.gmdcodecompletion.completions.lookupelementprovider

import com.android.gmdcodecompletion.ConfigurationParameterName
import com.android.gmdcodecompletion.ConfigurationParameterName.FAIL_FAST
import com.android.gmdcodecompletion.ConfigurationParameterName.GRANTED_PERMISSIONS
import com.android.gmdcodecompletion.ConfigurationParameterName.PERFORMANCE_METRICS
import com.android.gmdcodecompletion.ConfigurationParameterName.RECORD_VIDEO
import com.android.gmdcodecompletion.completions.GmdCodeCompletionLookupElement
import com.android.gmdcodecompletion.completions.GmdDevicePropertyInsertHandler
import com.android.gmdcodecompletion.verifyConfigurationLookupElementProviderResult
import org.junit.Test

class FtlTestOptionsLookupElementProviderTest {
  private fun ftlTestOptionsTestHelper(configurationParameterName: ConfigurationParameterName,
                                       currentDeviceProperties: CurrentDeviceProperties,
                                       expectedResult: List<GmdCodeCompletionLookupElement>) {
    val result = FtlTestOptionsLookupElementProvider.generateSimpleValueSuggestionList(configurationParameterName, currentDeviceProperties)
    verifyConfigurationLookupElementProviderResult(result, expectedResult)
  }

  @Test
  fun testGenerateGrantedPermissions() {
    val expectedResult = listOf(
      GmdCodeCompletionLookupElement(myValue = "all",
                                     myScore = 1u,
                                     myInsertHandler = GmdDevicePropertyInsertHandler()),
      GmdCodeCompletionLookupElement(myValue = "none",
                                     myScore = 0u,
                                     myInsertHandler = GmdDevicePropertyInsertHandler())
    )
    ftlTestOptionsTestHelper(GRANTED_PERMISSIONS, hashMapOf(), expectedResult)
  }

  @Test
  fun testFailFast() {
    simpleBooleanSuggestionHelper(FAIL_FAST, false)
  }

  @Test
  fun testRecordVideo() {
    simpleBooleanSuggestionHelper(RECORD_VIDEO, false)
  }

  @Test
  fun testPerformanceMetrics() {
    simpleBooleanSuggestionHelper(PERFORMANCE_METRICS, false)
  }

  private fun simpleBooleanSuggestionHelper(configurationParameterName: ConfigurationParameterName, defaultValue: Boolean) {
    val expectedResult = listOf(
      GmdCodeCompletionLookupElement(myValue = defaultValue.toString(),
                                     myScore = 1u),
      GmdCodeCompletionLookupElement(myValue = (!defaultValue).toString(),
                                     myScore = 0u)
    )
    ftlTestOptionsTestHelper(configurationParameterName, hashMapOf(), expectedResult)
  }
}
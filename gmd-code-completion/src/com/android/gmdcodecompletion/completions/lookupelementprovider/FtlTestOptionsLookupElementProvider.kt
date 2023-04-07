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
import com.intellij.codeInsight.lookup.LookupElement

/**
 * Generates lookup suggestion list for fields under FTL testOptions
 */
object FtlTestOptionsLookupElementProvider : BaseLookupElementProvider() {

  override fun generateSimpleValueSuggestionList(propertyName: ConfigurationParameterName,
                                                 deviceProperties: CurrentDeviceProperties): Collection<LookupElement> {
    return when (propertyName) {
      // default value is "all"
      GRANTED_PERMISSIONS -> generateSimpleEnumSuggestion(listOf("all", "none"))
      // default value is false for all the following fields
      FAIL_FAST, RECORD_VIDEO, PERFORMANCE_METRICS -> generateSimpleBooleanSuggestion(defaultValue = false)
      // We cannot give suggestions for other values
      else -> emptyList()
    }
  }
}
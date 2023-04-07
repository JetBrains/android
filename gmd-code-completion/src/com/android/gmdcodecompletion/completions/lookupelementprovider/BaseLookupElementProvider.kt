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

import com.android.gmdcodecompletion.AndroidDeviceInfo
import com.android.gmdcodecompletion.ConfigurationParameterName
import com.android.gmdcodecompletion.GmdDeviceCatalog
import com.android.gmdcodecompletion.MinAndTargetApiLevel
import com.android.gmdcodecompletion.completions.GmdCodeCompletionLookupElement
import com.android.gmdcodecompletion.completions.GmdDevicePropertyInsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toUpperCaseAsciiOnly

internal typealias CurrentDeviceProperties = HashMap<ConfigurationParameterName, String>

// Hardcoded order for ranking device ID suggestions
private val BRAND_PRIORITY: Array<String> = arrayOf("sony", "vivo", "xiaomi", "samsung", "google")
private val FORM_FACTOR_PRIORITY: Array<String> = arrayOf("TV", "WEARABLE", "TABLET", "PHONE")

/**
 * Base class for classes that generates lookup elements for GMDs
 */
abstract class BaseLookupElementProvider {

  /**
   * Stores and computes the score of a given device property.
   * Score that is added earlier in the sequence is prioritized compared with the latter ones.
   * Use UInt instead of Int since Int has a leading digit that is used to determine sign of the value.
   */
  private class ScoreBuilder {
    // Since current implementation of scoring system is relatively simple, 32 bits should be able to hold all information
    var myScore: UInt = 0u

    fun addScore(score: UInt, scoreMax: UInt) {
      // Prioritize myScore by left shifting it while leaving enough space for the new score
      myScore = myScore.shl(UInt.SIZE_BITS - scoreMax.countLeadingZeroBits()) + score
    }

    fun getScore(): UInt {
      return myScore
    }
  }

  protected fun generateGmdDeviceIdSuggestionHelper(minAndTargetApiLevel: MinAndTargetApiLevel,
                                                    specifiedApiLevel: Int,
                                                    deviceMap: HashMap<String, AndroidDeviceInfo>): Collection<LookupElement> {

    return deviceMap.mapNotNull { (deviceId, deviceInfo) ->
      if ((specifiedApiLevel != -1 && deviceInfo.supportedApis.contains(specifiedApiLevel)) ||
          (specifiedApiLevel == -1 && (deviceInfo.supportedApis.maxOrNull() ?: -1) >= minAndTargetApiLevel.minSdk)) {
        val scoreBuilder = ScoreBuilder()
        if (specifiedApiLevel == -1) {
          // 1 bit
          scoreBuilder.addScore(if (deviceInfo.supportedApis.contains(minAndTargetApiLevel.targetSdk)) 1u else 0u, 1u)
        }
        // Always add 1 to avoid -1 returned by indexOf() when casting to UInt. Keep # bits under 32 to avoid overflow
        // 3 bits
        scoreBuilder.addScore((BRAND_PRIORITY.indexOf(deviceInfo.brand.toLowerCaseAsciiOnly()) + 1).toUInt(),
                              BRAND_PRIORITY.size.toUInt())
        // 2 bits
        scoreBuilder.addScore((FORM_FACTOR_PRIORITY.indexOf(deviceInfo.formFactor.toUpperCaseAsciiOnly()) + 1).toUInt(),
                              FORM_FACTOR_PRIORITY.size.toUInt())
        val presentation = LookupElementPresentation()
        presentation.itemText = deviceId
        presentation.tailText = "  ${deviceInfo.deviceName}  ${deviceInfo.deviceForm}"
        GmdCodeCompletionLookupElement(myValue = deviceId, myScore = scoreBuilder.getScore(),
                                       myInsertHandler = GmdDevicePropertyInsertHandler(), myPresentation = presentation)
      }
      else null
    }
  }

  // Set defaultValue to prioritize desired value in suggestion list
  protected fun generateSimpleBooleanSuggestion(defaultValue: Boolean): Collection<LookupElement> {
    return listOf(GmdCodeCompletionLookupElement(myValue = defaultValue.toString(), myScore = 1u),
                  GmdCodeCompletionLookupElement(myValue = (!defaultValue).toString(), myScore = 0u))
  }

  /**
   * If hasDefault is set to true, first element in enumValues is the default value.
   * Default value will be prioritized in the suggestion list
   * Set insertDoubleQuotation to true to insert double quotation marks around the value after insertion
   */
  protected fun generateSimpleEnumSuggestion(enumValues: List<String>,
                                             hasDefault: Boolean = true,
                                             insertDoubleQuotation: Boolean = true): Collection<LookupElement> {
    return enumValues.mapIndexed { index, value ->
      GmdCodeCompletionLookupElement(myValue = value,
                                     myScore = if (index == 0 && hasDefault) 1u else 0u,
                                     myInsertHandler = if (insertDoubleQuotation) GmdDevicePropertyInsertHandler() else null)
    }
  }

  // Used to obtain GMD device property suggestion list. Do not put any PSI related fields
  open fun generateDevicePropertyValueSuggestionList(configurationParameterName: ConfigurationParameterName,
                                                     deviceProperties: CurrentDeviceProperties,
                                                     minAndTargetApiLevel: MinAndTargetApiLevel,
                                                     deviceCatalog: GmdDeviceCatalog): Collection<LookupElement> = emptyList()

  // Used to obtain simple device property suggestion list that does not require GmdDeviceCatalog. Do not put any PSI related fields
  open fun generateSimpleValueSuggestionList(configurationParameterName: ConfigurationParameterName,
                                             deviceProperties: CurrentDeviceProperties): Collection<LookupElement> = emptyList()
}
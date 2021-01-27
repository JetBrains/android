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
package com.android.tools.idea.run.editor

import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import org.jetbrains.android.facet.AndroidFacet

/**
 * Encapsulates an extra param of Android instrumentation test.
 *
 * The extra param is a key-value pair. [NAME] is the key and [VALUE] is the value.
 * If a param is originally defined outside of IDE's run configuration such as Gradle's build file,
 * the original value is stored in [ORIGINAL_VALUE] along with the source type [ORIGINAL_VALUE_SOURCE].
 */
data class AndroidTestExtraParam @JvmOverloads constructor(
  /**
   * The name of this extra param.
   */
  var NAME: String = "",

  /**
   * The current value of this extra param.
   */
  var VALUE: String = "",

  /**
   * The original value of this param which is defined by [ORIGINAL_VALUE_SOURCE].
   * If [ORIGINAL_VALUE_SOURCE] is [AndroidTestExtraParamSource.NONE], this value is always empty (or
   * you should ignore this value in case it's not empty).
   */
  var ORIGINAL_VALUE: String = "",

  /**
   * A source of the original value of this extra param.
   */
  var ORIGINAL_VALUE_SOURCE: AndroidTestExtraParamSource = AndroidTestExtraParamSource.NONE
) {
  companion object {
    /**
     * Parses a [String] to a [Sequence] of [AndroidTestExtraParam].
     *
     * e.g. "-e key1 value1 -e key2 value2" will be parsed as [(key1, value1), (key2, value2)].
     */
    @JvmStatic
    fun parseFromString(extraParams: String): Sequence<AndroidTestExtraParam> {
      return extraParams.splitToSequence("-e")
        .drop(1)  // We split string by "-e", so we need to discard the first element which is a substring before the first "-e".
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { it.split(' ', limit = 2) + "" }  // Pad with empty string for key-only param.
        .map { (key, value) -> AndroidTestExtraParam(key, value.trim()) }
    }
  }
}

/**
 * Represents where the param is originally defined.
 */
enum class AndroidTestExtraParamSource {
  /**
   * No original source. It means this param is defined in the IDE by user.
   */
  NONE,

  /**
   * The param is defined in Gradle build file.
   */
  GRADLE
}

/**
 * Merges two [Sequence] of [AndroidTestExtraParam]s.
 *
 * If there are more than one value with the same param name, one with no original source will be prioritized.
 * If both params have the same source, the last one will be used.
 */
fun Sequence<AndroidTestExtraParam>.merge(params: Sequence<AndroidTestExtraParam>): Collection<AndroidTestExtraParam> {
  return (this + params)
    .groupingBy { it.NAME }
    .reduce { _, param1, param2 ->
      when {
        param1.ORIGINAL_VALUE_SOURCE == param2.ORIGINAL_VALUE_SOURCE -> param2
        param1.ORIGINAL_VALUE_SOURCE == AndroidTestExtraParamSource.GRADLE -> param1.copy(VALUE = param2.VALUE)
        param2.ORIGINAL_VALUE_SOURCE == AndroidTestExtraParamSource.GRADLE -> param2.copy(VALUE = param1.VALUE)
        else -> param2
      }
    }
    .values
}

/**
 * Retrieves [AndroidTestExtraParam]s from a given [AndroidModuleModel].
 */
fun AndroidModuleModel?.getAndroidTestExtraParams(): Sequence<AndroidTestExtraParam> {
  return this?.selectedVariant?.testInstrumentationRunnerArguments?.asSequence()?.map { (key, value) ->
    AndroidTestExtraParam(key, value, value, AndroidTestExtraParamSource.GRADLE)
  } ?: emptySequence()
}

/**
 * Retrieves [AndroidTestExtraParam]s from a given [AndroidFacet].
 */
fun AndroidFacet?.getAndroidTestExtraParams(): Sequence<AndroidTestExtraParam> {
  return this?.let { AndroidModuleModel.get(it).getAndroidTestExtraParams() } ?: emptySequence()
}
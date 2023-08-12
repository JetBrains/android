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
package com.android.tools.idea.bleak

abstract class BleakCheck<OptionsType, ResultType>(val options: OptionsType, private val ignoreList: IgnoreList<ResultType>, private val knownIssues: IgnoreList<ResultType>) {
  // callbacks from BLeak
  abstract fun firstIterationFinished()
  abstract fun middleIterationFinished()
  abstract fun lastIterationFinished()

  abstract fun getResults(): List<ResultType>
  val success: Boolean
    get() = getResults().all { ignoreList.matches(it) || knownIssues.matches(it) }
  val report: String
    get() {
      val numKnownIssues = getResults().filter { knownIssues.matches(it) }.size
      return getResults().filterNot { ignoreList.matches(it) || knownIssues.matches(it) }.joinToString(separator = "\n----------------------\n") +
             if (numKnownIssues > 0) "\n----------------------\nFound $numKnownIssues known issues" else ""
    }
}
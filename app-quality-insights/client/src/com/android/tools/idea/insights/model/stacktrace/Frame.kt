/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.insights.model.stacktrace

/** Represents a Frame of the stack, as well as metadata & analysis results. */
data class Frame(
  // Line number of the code
  val line: Long = 0,

  // Filename of the code
  val file: String = "",

  // The raw symbol no matter it's unhydrateable or not
  val rawSymbol: String = "",

  // The hydrated symbol, or the raw symbol if it's unhydrateable
  val symbol: String = "",

  // Byte offset into the binary image which contains the code
  val offset: Long = 0,

  // Address in the binary image which contains the code
  val address: Long = 0,

  // Display name of the library
  val library: String = "",

  // Indicates whether analysis blames this frame as the cause of the crash
  // or error
  val blame: Blames = Blames.UNKNOWN_BLAMED,
) {
  fun matches(qualifiedClassName: String, methodName: String): Boolean {
    return symbol.startsWith(qualifiedClassName) && symbol.contains(methodName)
  }

  fun matches(regex: Regex): Boolean {
    return symbol.matches(regex)
  }
}

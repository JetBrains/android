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
package com.android.tools.idea.diagnostics

class TruncatingStringBuilder(private val maxSize: Int, private val truncationMessage: String) {
  private val stringBuilder = StringBuilder()
  private var hasHitLimit = false
  private var size = 0

  fun append(s: String) {
    if (hasHitLimit) return
    if (s.length + size > maxSize) {
      hasHitLimit = true
      stringBuilder.append(s.substring(0, maxSize - size).substringBeforeLast("\n"))
      stringBuilder.append(truncationMessage)
    } else {
      stringBuilder.append(s)
      size += s.length
    }
  }

  override fun toString() = stringBuilder.toString()
}
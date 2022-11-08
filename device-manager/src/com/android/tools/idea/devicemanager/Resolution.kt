/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager

import com.intellij.openapi.diagnostic.Logger
import java.util.Optional
import java.util.regex.Pattern

class Resolution(val width: Int, val height: Int) {

  override fun hashCode(): Int {
    return 31 * width + height
  }

  override fun equals(`object`: Any?): Boolean {
    if (`object` !is Resolution) {
      return false
    }
    val resolution = `object`
    return width == resolution.width && height == resolution.height
  }

  override fun toString(): String {
    return width.toString() + " × " + height
  }

  companion object {
    private val PATTERN = Pattern.compile("Physical size: (\\d+)x(\\d+)")
    fun newResolution(output: List<String?>): Optional<Resolution> {
      val string = output[0]
      val matcher = PATTERN.matcher(string)
      if (!matcher.matches()) {
        Logger.getInstance(Resolution::class.java).warn(string)
        return Optional.empty()
      }
      return Optional.of(Resolution(matcher.group(1).toInt(), matcher.group(2).toInt()))
    }
  }
}

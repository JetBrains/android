/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.scene.draw

import com.google.common.base.Joiner
import java.awt.Color

fun parse(s: String, expected: Int): Array<String> {
  val sp = s.split(',').dropLastWhile { it.isEmpty() }.toTypedArray()
  return if (sp.size == expected) {
    sp
  }
  else {
    throw IllegalArgumentException()
  }
}

fun buildString(simpleName: String, vararg properties: Any): String {
  return simpleName + if (properties.isNotEmpty()) {
    ',' + Joiner.on(',').join(properties)
  }
  else {
  }
}

fun stringToColorOrNull(s: String): Color? {
  return if (s == "null") null else stringToColor(s)
}

fun colorOrNullToString(c: Color?): String {
  return if (c == null) "null" else colorToString(c)
}

fun stringToColor(s: String): Color {
  return Color(s.toLong(16).toInt())
}

fun colorToString(c: Color): String {
  return Integer.toHexString(c.rgb)
}
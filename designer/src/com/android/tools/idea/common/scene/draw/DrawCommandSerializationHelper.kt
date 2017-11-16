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
import java.awt.Font
import java.awt.Rectangle
import java.lang.Long

fun parse(s: String, expected: Int): Array<String> {
  val sp = s.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
  return if (sp.size == expected) {
    sp
  }
  else {
    throw IllegalArgumentException()
  }
}

fun stringToRect(s: String): Rectangle {
  val sp = s.split("x".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
  val r = Rectangle()
  r.x = Integer.parseInt(sp[0])
  r.y = Integer.parseInt(sp[1])
  r.width = Integer.parseInt(sp[2])
  r.height = Integer.parseInt(sp[3])
  return r
}

fun rectToString(r: Rectangle): String {
  return Joiner.on('x').join(r.x, r.y, r.width, r.height)
}

fun stringToColor(s: String): Color {
  return Color(Long.parseLong(s, 16).toInt())
}

fun colorToString(c: Color): String {
  return Integer.toHexString(c.rgb)
}

fun stringToFont(s: String): Font {
  val sp = s.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
  val style = Integer.parseInt(sp[1])
  val size = Integer.parseInt(sp[2])
  return Font(sp[0], style, size)
}

fun fontToString(f: Font): String {
  return Joiner.on(':').join(f.name, f.style, f.size)
}

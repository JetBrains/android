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

import com.android.tools.idea.common.scene.LerpFloat
import com.google.common.base.Joiner
import java.awt.*
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D

fun parse(s: String, expected: Int): Array<String> {
  val sp = splitString(s, ',').toTypedArray()
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

fun stringToRect(s: String): Rectangle {
  val sp = splitString(s, 'x')
  val r = Rectangle()
  r.x = sp[0].toInt()
  r.y = sp[1].toInt()
  r.width = sp[2].toInt()
  r.height = sp[3].toInt()
  return r
}

fun rectToString(r: Rectangle): String {
  return Joiner.on('x').join(r.x, r.y, r.width, r.height)
}

fun stringToRoundRect2D(s: String): RoundRectangle2D.Float {
  val sp = splitString(s, 'x')
  val r = RoundRectangle2D.Float()
  r.x = sp[0].toFloat()
  r.y = sp[1].toFloat()
  r.width = sp[2].toFloat()
  r.height = sp[3].toFloat()
  r.arcwidth = sp[4].toFloat()
  r.archeight = sp[5].toFloat()
  return r
}

fun roundRect2DToString(r: RoundRectangle2D.Float): String {
  return Joiner.on('x').join(r.x, r.y, r.width, r.height, r.arcwidth, r.arcHeight)
}

fun stringToColor(s: String): Color {
  return Color(s.toLong(16).toInt())
}

fun colorToString(c: Color): String {
  return Integer.toHexString(c.rgb)
}

fun stringToFont(s: String): Font {
  val sp = splitString(s, ':')
  val style = sp[1].toInt()
  val size = sp[2].toInt()
  return Font(sp[0], style, size)
}

fun fontToString(f: Font): String {
  return Joiner.on(':').join(f.name, f.style, f.size)
}

fun stringToPoint(s: String): Point {
  val sp = splitString(s, 'x')
  val p = Point()
  p.x = sp[0].toInt()
  p.y = sp[1].toInt()
  return p
}

fun pointToString(p: Point): String {
  return Joiner.on('x').join(p.x, p.y)
}

fun stringToPoint2D(s: String): Point2D.Float {
  val sp = splitString(s, 'x')
  val p = Point2D.Float()
  p.x = sp[0].toFloat()
  p.y = sp[1].toFloat()
  return p
}

fun point2DToString(p: Point2D.Float): String {
  return Joiner.on('x').join(p.x, p.y)
}

fun stringToLerp(s: String): LerpFloat {
  val sp = splitString(s, ':')
  val start = sp[0].toFloat()
  val end = sp[1].toFloat()
  val duration = sp[2].toInt()

  return LerpFloat(start, end, duration)
}

fun lerpToString(l: LerpFloat): String {
  return Joiner.on(':').join(l.start, l.end, l.duration)
}

fun stringToStroke(s: String): BasicStroke {
  val sp = splitString(s, ':')
  val width = sp[0].toInt()
  val cap = sp[1].toInt()
  val join = sp[2].toInt()

  return BasicStroke(width.toFloat(), cap, join)
}

fun strokeToString(s: BasicStroke): String {
  return Joiner.on(':').join(s.lineWidth.toInt(), s.endCap, s.lineJoin)
}

private fun splitString(s: String, delimiter: Char): List<String> {
  return s.split(delimiter).dropLastWhile { it.isEmpty() }
}



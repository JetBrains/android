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
package com.android.tools.idea.adb.wireless

import com.google.common.truth.Truth
import com.google.zxing.common.BitMatrix
import org.junit.Test

class QrCodeGeneratorTest {
  /**
   * Note: Change the return value to `true` to generate source code for the `expectedRows`
   * variable of the tests. This should only required if, for some reason, the zxing library
   * behavior changes. (One would hope this never happens).
   */
  private fun generateExpectedRows() = false

  @Test
  fun qrEncodeShouldWork() {
    // Prepare
    val expectedRows = arrayOf(
      "                             ",
      "                             ",
      "                             ",
      "                             ",
      "    XXXXXXX   X X XXXXXXX    ",
      "    X     X X X X X     X    ",
      "    X XXX X X XX  X XXX X    ",
      "    X XXX X     X X XXX X    ",
      "    X XXX X XXXXX X XXX X    ",
      "    X     X XXX   X     X    ",
      "    XXXXXXX X X X XXXXXXX    ",
      "            X                ",
      "    XX X  XX  XXX XXX XX     ",
      "    XXX  X XX  X X X  XXX    ",
      "      X X X XXXX  XXXX  X    ",
      "    XXXXXX XXX  XXXXXX X     ",
      "      XX  X  XXX XXX   XX    ",
      "            XX   XXXX X X    ",
      "    XXXXXXX X XXX X X  X     ",
      "    X     X  X    XXX  XX    ",
      "    X XXX X     XXX     X    ",
      "    X XXX X X X  XX X  XX    ",
      "    X XXX X  XXX XXXX   X    ",
      "    X     X X  XX   X        ",
      "    XXXXXXX X X  X  XX X     ",
      "                             ",
      "                             ",
      "                             ",
      "                             ")

    // Act
    val bits = encodeQrCode("foobar", 0)
    outputExpectedRowsCode(bits)

    // Assert
    Truth.assertThat(bits.height).isEqualTo(expectedRows.count())
    Truth.assertThat(bits.width).isEqualTo(expectedRows[0].length)
    for (y in 0 until bits.height) {
      for (x in 0 until bits.width) {
        Truth.assertThat(expectedRows[y][x]).isEqualTo(if (bits[x, y]) 'X' else ' ')
      }
    }
  }

  @Test
  fun qrEncodeShouldWorkWithCustomSize() {
    // Prepare
    val expectedRows = arrayOf(
      "                                        ",
      "                                        ",
      "                                        ",
      "                                        ",
      "                                        ",
      "                                        ",
      "                                        ",
      "                                        ",
      "                                        ",
      "         XXXXXXX   X X XXXXXXX          ",
      "         X     X X X X X     X          ",
      "         X XXX X X XX  X XXX X          ",
      "         X XXX X     X X XXX X          ",
      "         X XXX X XXXXX X XXX X          ",
      "         X     X XXX   X     X          ",
      "         XXXXXXX X X X XXXXXXX          ",
      "                 X                      ",
      "         XX X  XX  XXX XXX XX           ",
      "         XXX  X XX  X X X  XXX          ",
      "           X X X XXXX  XXXX  X          ",
      "         XXXXXX XXX  XXXXXX X           ",
      "           XX  X  XXX XXX   XX          ",
      "                 XX   XXXX X X          ",
      "         XXXXXXX X XXX X X  X           ",
      "         X     X  X    XXX  XX          ",
      "         X XXX X     XXX     X          ",
      "         X XXX X X X  XX X  XX          ",
      "         X XXX X  XXX XXXX   X          ",
      "         X     X X  XX   X              ",
      "         XXXXXXX X X  X  XX X           ",
      "                                        ",
      "                                        ",
      "                                        ",
      "                                        ",
      "                                        ",
      "                                        ",
      "                                        ",
      "                                        ",
      "                                        ",
      "                                        ")

    // Act
    val bits = encodeQrCode("foobar", 40)
    outputExpectedRowsCode(bits)

    // Assert
    Truth.assertThat(bits.height).isEqualTo(expectedRows.count())
    Truth.assertThat(bits.width).isEqualTo(expectedRows[0].length)
    for (y in 0 until bits.height) {
      for (x in 0 until bits.width) {
        Truth.assertThat(expectedRows[y][x]).isEqualTo(if (bits[x, y]) 'X' else ' ')
      }
    }
  }

  @Test
  fun qrEncodeShouldWorkForUnicodeCharacters() {
    // Prepare
    val expectedRows = arrayOf(
      "                             ",
      "                             ",
      "                             ",
      "                             ",
      "    XXXXXXX   X X XXXXXXX    ",
      "    X     X X X X X     X    ",
      "    X XXX X X XX  X XXX X    ",
      "    X XXX X     X X XXX X    ",
      "    X XXX X XXX X X XXX X    ",
      "    X     X XXX   X     X    ",
      "    XXXXXXX X X X XXXXXXX    ",
      "            X  X             ",
      "    XX X  XX  XXX XXX XX     ",
      "       X    XX XX X X XX     ",
      "    X  X XX XXXXXX X         ",
      "    XX X   XXX  X XX X XX    ",
      "      XXX X X         X X    ",
      "            X     XX XX      ",
      "    XXXXXXX X  X XXXX  X     ",
      "    X     X  X XXXX  XXXX    ",
      "    X XXX X      XX X XX     ",
      "    X XXX X XXX  X XX X X    ",
      "    X XXX X  XX    XX   X    ",
      "    X     X X XXXXXX  XXX    ",
      "    XXXXXXX XX     X  XX     ",
      "                             ",
      "                             ",
      "                             ",
      "                             ")

    // Act
    val bits = encodeQrCode("中文測試", 0)
    outputExpectedRowsCode(bits)

    // Assert
    Truth.assertThat(bits.height).isEqualTo(expectedRows.count())
    Truth.assertThat(bits.width).isEqualTo(expectedRows[0].length)
    for (y in 0 until bits.height) {
      for (x in 0 until bits.width) {
        Truth.assertThat(expectedRows[y][x]).isEqualTo(if (bits[x, y]) 'X' else ' ')
      }
    }
  }

  private fun encodeQrCode(contents: String, size: Int): BitMatrix {
    return QrCodeGenerator.encodeQrCode(contents, size)
  }

  /**
   * Keep this method because we may need to generate source code for the
   * expected QR Code format (or if we add tests in the future)
   */
  @Suppress("unused")
  private fun outputExpectedRowsCode(bits: BitMatrix) {
    if (!generateExpectedRows()) {
      return
    }
    println("val expectedRows = arrayOf(")
    for (y in 0 until bits.height) {
      val sb = StringBuilder()
      sb.append("  \"")
      for (x in 0 until bits.width) {
        sb.append(if (bits[x, y]) "X" else " ")
      }
      sb.append(if (y == bits.height - 1) "\")" else "\",")
      println(sb)
    }
  }
}
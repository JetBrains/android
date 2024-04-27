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
      "    XXXXXXX XXXXX XXXXXXX    ",
      "    X     X  X X  X     X    ",
      "    X XXX X  XX   X XXX X    ",
      "    X XXX X  XXX  X XXX X    ",
      "    X XXX X   X X X XXX X    ",
      "    X     X    XX X     X    ",
      "    XXXXXXX X X X XXXXXXX    ",
      "            XXXXX            ",
      "    XX XX X  XX X X     X    ",
      "       XX    XX X X XX       ",
      "     XXXXXXXX X  XX X  XX    ",
      "              XX      X X    ",
      "     XX  XXX  X   X  X  X    ",
      "            X XXX    X X     ",
      "    XXXXXXX  XX XXXXXX       ",
      "    X     X   XXXX   XX      ",
      "    X XXX X XX XX XX X XX    ",
      "    X XXX X XX XX  X XX      ",
      "    X XXX X   X   X XX XX    ",
      "    X     X XXX  XXX XXXX    ",
      "    XXXXXXX XXXX   XX        ",
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
      "         XXXXXXX XXXXX XXXXXXX          ",
      "         X     X  X X  X     X          ",
      "         X XXX X  XX   X XXX X          ",
      "         X XXX X  XXX  X XXX X          ",
      "         X XXX X   X X X XXX X          ",
      "         X     X    XX X     X          ",
      "         XXXXXXX X X X XXXXXXX          ",
      "                 XXXXX                  ",
      "         XX XX X  XX X X     X          ",
      "            XX    XX X X XX             ",
      "          XXXXXXXX X  XX X  XX          ",
      "                   XX      X X          ",
      "          XX  XXX  X   X  X  X          ",
      "                 X XXX    X X           ",
      "         XXXXXXX  XX XXXXXX             ",
      "         X     X   XXXX   XX            ",
      "         X XXX X XX XX XX X XX          ",
      "         X XXX X XX XX  X XX            ",
      "         X XXX X   X   X XX XX          ",
      "         X     X XXX  XXX XXXX          ",
      "         XXXXXXX XXXX   XX              ",
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
      "    XXXXXXX XXXXX XXXXXXX    ",
      "    X     X XX XX X     X    ",
      "    X XXX X  XXX  X XXX X    ",
      "    X XXX X  X XX X XXX X    ",
      "    X XXX X X  XX X XXX X    ",
      "    X     X X X   X     X    ",
      "    XXXXXXX X X X XXXXXXX    ",
      "            XXX              ",
      "    XXX  XX XXXXXXXXX  XX    ",
      "     X   X XX   XXXXXXX      ",
      "    X   X X X   XX  XX       ",
      "    X X         XX   X       ",
      "     XX XXXXXX X X X XXXX    ",
      "            XXXX  X X X      ",
      "    XXXXXXX  X X    X   X    ",
      "    X     X X   X XX  X X    ",
      "    X XXX X  XXX XXX XXX     ",
      "    X XXX X   X   X X XX     ",
      "    X XXX X X XX X  XX XX    ",
      "    X     X XX  XXX XXXXX    ",
      "    XXXXXXX X    XX   X X    ",
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
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

import com.android.annotations.concurrency.Slow
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.util.EnumMap

object QrCodeGenerator {
  /**
   * Encode the [String] [contents] into a QR code represented as a [BitMatrix].
   * Each bit entry of the [BitMatrix] represents either a transparent or
   * black dot in the corresponding QR Code image.
   *
   * If the [size] is not provided (or has the default value of [0]), the
   * size of the returned [BitMatrix] is computed so that it is big enough
   * to represent [contents].
   *
   * If the [size] is provided, the returned [BitMatrix] will be of the provided
   * size, with the possibility of an incomplete QR Code is the size is too
   * small.
   */
  @JvmStatic
  @Throws(WriterException::class)
  @Slow
  fun encodeQrCode(contents: String, size: Int = 0): BitMatrix {
    val hints = EnumMap<EncodeHintType, Any?>(EncodeHintType::class.java)
    if (!isIso88591(contents)) {
      // Generate QR code of UTF-8 encoding (See b/131851854)
      //
      // ZXing generate QR code with default encoding ISO-8859-1, the generated
      // QR code is wrong encoded if there is a character outside of the charset.
      hints[EncodeHintType.CHARACTER_SET] = StandardCharsets.UTF_8.name()
    }
    return MultiFormatWriter().encode(contents, BarcodeFormat.QR_CODE, size, size, hints)
  }

  /**
   * Encode the [String] [contents] into a QR code represented as a [BufferedImage].
   * The returned [BufferedImage] contains 1 pixel per QR Code "bit". If the QR Code
   * bit is set, the corresponding [BufferedImage] pixel is set to the RGB color
   * black (0x000000), otherwise it is set to the RGB color white (0xffffff)
   */
  @JvmStatic
  @Throws(WriterException::class)
  @Slow
  fun encodeQrCodeToImage(contents: String, backgroundColor: Color, foregroundColor: Color): BufferedImage {
    val bits = encodeQrCode(contents)

    // Note: Since we set individual pixels, we use a non-hidpi aware image
    //       (i.e. we use BufferedImage directly). It is up to the caller
    //       to scale the image to the desired dpi scale.
    @Suppress("UndesirableClassUsage")
    val img = BufferedImage(bits.width, bits.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until bits.height) {
      for (x in 0 until bits.width) {
        img.setRGB(x, y, if (bits.get(x, y)) foregroundColor.rgb else backgroundColor.rgb)
      }
    }
    return img
  }

  private fun isIso88591(contents: String): Boolean {
    val encoder = StandardCharsets.ISO_8859_1.newEncoder()
    return encoder.canEncode(contents)
  }
}
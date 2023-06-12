package com.android.tools.screensharing.benchmark

import android.graphics.Color
import androidx.annotation.ColorInt
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Encodes a positive integer into a list of colors using at most [bitsPerChannel] bits per channel.
 *
 * A value of 0 [bitsPerChannel] means that the integer will be encoded as a sequence of black and white,
 * i.e. in binary.
 *
 * Will throw an [IllegalArgumentException] if the integer is negative or if [bitsPerChannel] is
 * more than 8.
 */
fun Int.toColors(maxBits: Int, bitsPerChannel: Int): List<Int> {
  require(this >= 0) { "Cannot encode a negative integer" }
  require(bitsPerChannel in 0..8) { "Can only use 0 to 8 bits per color channel." }
  if (bitsPerChannel == 0) return bits(maxBits).map { if (it == '1') Color.WHITE else Color.BLACK }
  return bits(maxBits).chunked(bitsPerChannel).chunked(3) { it.toColor() }
}

/** Returns a contrasting color for the [ColorInt]. */
@ColorInt
fun @receiver:ColorInt Int.contrastingColor(): Int {
  val r = Color.red(this)
  val g = Color.green(this)
  val b = Color.blue(this)
  return if (r * 0.299 + g * 0.587 + b * 0.114 > 128) Color.BLACK else Color.WHITE
}

/** Pretty-prints a [ColorInt] as a hex string (e.g. #123ABC) */
fun @receiver:ColorInt Int.toHexColorString(): String {
  return String.format("#%06X", (0xFFFFFF and this))
}

/** Creates a random color. */
@ColorInt
fun randomColor(): Int =
  Color.rgb(Random.nextInt(255), Random.nextInt(255), Random.nextInt(255))

private fun Int.bits(minBits: Int = Int.SIZE_BITS) : String = toUInt().toString(2).padStart(minBits, '0')

@ColorInt
private fun List<String>.toColor() : Int {
  require(!isEmpty()) { "Must provide at least one value." }
  return when(size) {
    1 -> Color.rgb(get(0).toColorChannel(), 0, 0)
    2 -> Color.rgb(get(0).toColorChannel(), get(1).toColorChannel(), 0)
    else ->  Color.rgb(get(0).toColorChannel(), get(1).toColorChannel(), get(2).toColorChannel())
  }
}

private fun String.toColorChannel() : Int {
  require(length in 1..8) { "Cannot fit $length bits in a color channel." }
  return (toInt(2) * (255 / ((1 shl length) - 1).toDouble())).roundToInt().coerceIn(0, 255)
}

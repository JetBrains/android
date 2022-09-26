package com.android.tools.screensharing.benchmark

import android.content.Context
import android.util.AttributeSet

/**
 * A version of [EncodedIntegerView] that precomputes its color values.
 *
 * This should only be used when the total number of possible values is small.
 */
class CachingEncodedIntegerView(context: Context, attrs: AttributeSet) : EncodedIntegerView(context, attrs) {
  private val possibleValues: Int
    get() = 1 shl maxBits
  private var colorCache: Map<Int, List<Int>> = generateColorCache()

  override fun onConfigurationReloaded() {
    colorCache = generateColorCache()
  }

  override fun computeColors(n: Int): List<Int>? = colorCache[n].also { if(it == null) displayError(">= $possibleValues") }

  private fun generateColorCache() = (0 until possibleValues).associateWith { it.toColors(maxBits, bitsPerChannel) }
}

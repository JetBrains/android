/*
 * Copyright (C) 2014 The Android Open Source Project
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

/**
 * Functions to style controls for a consistent user UI
 */
@file:JvmName("WelcomeUiUtils")

package com.android.tools.idea.welcome.wizard

import com.android.sdklib.devices.Storage
import java.math.RoundingMode
import java.text.NumberFormat

/**
 * Returns string describing the [size].
 */
fun getSizeLabel(size: Long): String {
  val unit = Storage.Unit.values().last { it.numberOfBytes <= size.coerceAtLeast(1) }

  val space = size * 1.0 / unit.numberOfBytes
  val formatted = roundToNumberOfDigits(space, 3)
  return "$formatted ${unit.displayValue}"
}

/**
 * Returns a string that rounds the number so number of integer places + decimal places is less or equal to [maxDigits].
 *
 * Number will not be truncated if it has more integer digits then [maxDigits].
 */
private fun roundToNumberOfDigits(number: Double, maxDigits: Int): String {
  var multiplier = 1
  var digits = maxDigits
  while (digits > 0 && number > multiplier) {
    multiplier *= 10
    digits--
  }
  return NumberFormat.getNumberInstance().apply {
    isGroupingUsed = false
    roundingMode = RoundingMode.HALF_UP
    maximumFractionDigits = digits
  }.format(number)
}

/**
 * Appends [details] to the [message] if they are not empty.
 */
fun getMessageWithDetails(message: String, details: String?): String =
  if (details.isNullOrBlank()) {
    "$message."
  }
  else {
    val dotIfNeeded = if (details.trim().endsWith(".")) "" else "."
    "$message: $details$dotIfNeeded"
  }

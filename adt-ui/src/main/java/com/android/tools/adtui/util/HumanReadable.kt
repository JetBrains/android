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
@file:JvmName("HumanReadableUtil")
package com.android.tools.adtui.util

import java.text.DecimalFormat
import kotlin.math.abs

/**
 * Converts size in bytes to a compact human-readable string.
 */
fun getHumanizedSize(sizeInBytes: Long): String {
  val formatter = DecimalFormat("#.#")
  val range = abs(sizeInBytes)
  return when {
    range > GIGA -> formatter.format(sizeInBytes / GIGA.toDouble()) + " GB"
    range > MEGA -> formatter.format(sizeInBytes / MEGA.toDouble()) + " MB"
    range > KILO -> formatter.format(sizeInBytes / KILO.toDouble()) + " KB"
    else -> "${sizeInBytes} B"
  }
}

private const val KILO = 1024L
private const val MEGA = KILO * KILO
private const val GIGA = MEGA * KILO



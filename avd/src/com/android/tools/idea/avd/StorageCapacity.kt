/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.avd

/** @throws ArithmeticException If this cannot be expressed in bytes without overflow */
internal data class StorageCapacity(val value: Long, val unit: Unit) : Comparable<StorageCapacity> {

  init {
    Math.multiplyExact(value, unit.byteCount)
  }

  /**
   * Returns an equivalent StorageCapacity with the largest unit with no loss of precision. Returns
   * 2M for 2048K, for example.
   */
  internal fun withMaxUnit(): StorageCapacity {
    val maxUnit = maxUnit()
    return StorageCapacity(valueIn(maxUnit), maxUnit)
  }

  private fun maxUnit(): Unit {
    val array = Unit.values()
    val subList = array.toList().subList(unit.ordinal + 1, array.size)
    val byteCount = value * unit.byteCount

    return subList.filter { byteCount % it.byteCount == 0L }.maxOrNull() ?: unit
  }

  fun valueIn(unit: Unit) = value * this.unit.byteCount / unit.byteCount

  enum class Unit(val byteCount: Long) {
    B(1),
    KB(1_024),
    MB(1_024 * 1_024),
    GB(1_024 * 1_024 * 1_024),
    TB(1_024L * 1_024 * 1_024 * 1_024),
  }

  override fun toString() = value.toString() + unit.toString().first()

  override fun compareTo(other: StorageCapacity) = valueIn(Unit.B).compareTo(other.valueIn(Unit.B))

  companion object {
    val MIN = StorageCapacity(0, Unit.B)
  }
}

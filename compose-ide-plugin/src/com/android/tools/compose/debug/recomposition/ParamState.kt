/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.compose.debug.recomposition

import com.android.tools.compose.ComposeBundle

private const val SLOTS_PER_INT = 10

/**
 * Based on [androidx.compose.compiler.plugins.kotlin.lower.ParamState]
 */
internal enum class ParamState(private val nameResource: String, private val bits: Int) {
  /**
   * Indicates that nothing is certain about the current state of the parameter. It could be
   * different from it was during the last execution, or it could be the same, but it is not
   * known so the current function looking at it must call equals on it in order to find out.
   * This is the only state that can cause the function to spend slot table space in order to
   * look at it.
   */
  Uncertain("recomposition.state.uncertain", 0b000),

  /**
   * This indicates that the value is known to be the same since the last time the function was
   * executed. There is no need to store the value in the slot table in this case because the
   * calling function will *always* know whether the value was the same or different as it was
   * in the previous execution.
   */
  Same("recomposition.state.same", 0b001),

  /**
   * This indicates that the value is known to be different since the last time the function
   * was executed. There is no need to store the value in the slot table in this case because
   * the calling function will *always* know whether the value was the same or different as it
   * was in the previous execution.
   */
  Different("recomposition.state.different", 0b010),

  /**
   * This indicates that the value is known to *never change* for the duration of the running
   * program.
   */
  Static("recomposition.state.static", 0b011),

  /**
   * If the msb is set, it is unstable. Lower bits are ignored.
   */
  Unstable100("recomposition.state.unstable", 0b100),
  Unstable101("recomposition.state.unstable", 0b101),
  Unstable110("recomposition.state.unstable", 0b110),
  Unstable111("recomposition.state.unstable", 0b111),
  ;

  fun getDisplayName() = ComposeBundle.message(nameResource)

  companion object {
    private val STATES = ParamState.values().sortedBy { it.bits }

    /**
     * Returns the decoded param states of [values].
     */
    fun decode(values: List<Int>): List<ParamState> {
      val states = mutableListOf<ParamState>()
      values.forEach {
        var bits = it and 0x7fff_ffff // ignore msb

        // drop lsb
        bits = bits shr 1

        var cnt = SLOTS_PER_INT
        while (cnt != 0) {
          val decoded = STATES[bits and 0b111]
          bits = bits shr 3
          states.add(decoded)
          cnt--
        }
      }

      return states
    }
  }
}

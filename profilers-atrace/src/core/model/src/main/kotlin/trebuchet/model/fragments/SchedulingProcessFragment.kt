/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package trebuchet.model.fragments

import trebuchet.model.CpuProcessSlice

class SchedulingProcessFragment(val thread: ThreadModelFragment, override val startTime: Double) : CpuProcessSlice {
  override var endTime: Double = Double.MAX_VALUE

  class Builder {
    private val _slices = mutableListOf<SchedulingProcessFragment>()
    val slices: List<SchedulingProcessFragment> get() = _slices
    fun switchProcess(thread: ThreadModelFragment, timestamp: Double) {
      if (_slices.isNotEmpty()) {
        if (_slices.last().endTime == Double.MAX_VALUE) {
          _slices.last().endTime = timestamp
        }
      }
      if (thread.id != 0) {
        _slices.add(SchedulingProcessFragment(thread, timestamp))
      }
    }
  }

  override val name: String get() {
    if (thread.name == null) {
      return thread.id.toString()
    } else {
      return thread.name!!
    }
  }

  override val id: Int get() {
    return thread.id
  }

  override val didNotFinish: Boolean get() = endTime == Double.MAX_VALUE
}
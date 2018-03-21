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

import trebuchet.model.SchedSlice
import trebuchet.model.SchedulingState
import trebuchet.model.base.Slice

class SchedulingSliceFragment(override val state: SchedulingState, override val startTime: Double)
        : SchedSlice {
    override var endTime: Double = Double.MAX_VALUE
    var blockedReason: String? = null

    class Builder {
        private val _slices = mutableListOf<SchedulingSliceFragment>()
        val slices: List<SchedulingSliceFragment> get() = _slices

        fun switchState(newState: SchedulingState, timestamp: Double) {
            if (_slices.isNotEmpty()) {
                if (_slices.last().state == newState) {
                    // Nothing to do
                    return
                }
                _slices.last().endTime = timestamp
            }
            _slices.add(SchedulingSliceFragment(newState, timestamp))
        }
    }

    override val name: String get() = state.friendlyName
    override val didNotFinish: Boolean get() = false
}
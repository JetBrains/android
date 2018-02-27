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
import trebuchet.model.base.SliceGroup

class SliceGroupBuilder {
    val slices: MutableList<MutableSliceGroup> = mutableListOf()
    val openSlices: MutableList<MutableSliceGroup> = mutableListOf()

    fun hasOpenSlices() = openSlices.isNotEmpty()

    inline fun beginSlice(action: (MutableSliceGroup) -> Unit) {
        val builder = MutableSliceGroup()
        action(builder)
        openSlices.add(builder)
    }

    inline fun endSlice(action: (MutableSliceGroup) -> Unit): SliceGroup? {
        if (!hasOpenSlices()) return null // silently ignore unmatched endSlice calls

        val builder = openSlices.removeAt(openSlices.lastIndex)
        action(builder)
        builder.validate()
        if (openSlices.isNotEmpty()) {
            openSlices.last().add(builder)
        } else {
            slices.add(builder)
        }
        return builder
    }

    fun autoCloseOpenSlices(maxTimestamp: Double) {
        while (hasOpenSlices()) {
            endSlice {
                it.endTime = maxTimestamp
                it.didNotFinish = true
            }
        }
    }

    companion object {
        val EmptyChildren = mutableListOf<MutableSliceGroup>()
        val EmptySchedules = mutableListOf<SchedulingSliceFragment>()
    }

    class MutableSliceGroup(override var startTime: Double = Double.NaN,
                            override var endTime: Double = Double.NaN,
                            override var didNotFinish: Boolean = false,
                            override var cpuTime: Double = 0.0,
                            private var _name: String? = null,
                            private var _children: MutableList<MutableSliceGroup>? = null,
                            private var _scheduledSlices: MutableList<SchedulingSliceFragment>? = null) : SliceGroup {
        override var name: String
            get() = _name!!
            set(value) { _name = value }

        override val runningSlices: List<SchedSlice>
            get() = _scheduledSlices!!

        override val children: List<SliceGroup>
            get() = _children!!

        fun validate() {
            if (!startTime.isFinite() || startTime < 0) {
                throw IllegalStateException("Invalid startTime $startTime")
            }
            if (!endTime.isFinite() || endTime < 0) {
                throw IllegalStateException("Invalid endTime $endTime")
            }
            if (endTime < startTime) {
                throw IllegalStateException("endTime $endTime cannot be before startTime $startTime")
            }
            if (_name == null) {
                throw IllegalStateException("name cannot be null")
            }
            if (_children == null) {
                _children = EmptyChildren
            }
            if (_scheduledSlices == null) {
                _scheduledSlices = EmptySchedules
            }
        }

        /**
         * Loops the set of scheduling slices summing the cpu time, and adding a reference
         * to each slice that has an end time > this slices start time.
         * The scheduling slice fragments list is assumed to contain a sorted set of fragments.
         */
        fun populateScheduledSlices(slices: List<SchedulingSliceFragment>) {
            for(i in slices.size-1 downTo 0) {
                if (slices[i].endTime < startTime) {
                    break
                }
                if (slices[i].state == SchedulingState.RUNNING && slices[i].startTime < endTime) {
                    cpuTime += minOf(slices[i].endTime, endTime) - maxOf(slices[i].startTime, startTime)
                    if (_scheduledSlices == null) {
                        _scheduledSlices = mutableListOf()
                    }
                    _scheduledSlices!!.add(slices[i])
                }

            }
        }

        fun add(child: MutableSliceGroup) {
            if (_children == null) _children = mutableListOf()
            _children!!.add(child)
        }
    }
}
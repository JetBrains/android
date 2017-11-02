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

import trebuchet.model.base.SliceGroup

class SliceGroupBuilder {
    val slices: MutableList<MutableSliceGroup> = mutableListOf()
    val openSlices: MutableList<MutableSliceGroup> = mutableListOf()

    fun hasOpenSlices() = openSlices.isNotEmpty()

    inline fun beginSlice(action: (MutableSliceGroup) -> Unit): Unit {
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
    }

    class MutableSliceGroup(override var startTime: Double = Double.NaN,
                            override var endTime: Double = Double.NaN,
                            override var didNotFinish: Boolean = false,
                            var _name: String? = null,
                            var _children: MutableList<MutableSliceGroup>? = null) : SliceGroup {
        override var name: String
            get() = _name!!
            set(value) { _name = value }

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
        }

        fun add(child: MutableSliceGroup) {
            if (_children == null) _children = mutableListOf()
            _children!!.add(child)
        }
    }
}
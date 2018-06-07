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

import trebuchet.model.InvalidId
import trebuchet.model.SchedSlice
import trebuchet.model.base.SliceGroup

class ThreadModelFragment(var id: Int, var process: ProcessModelFragment, var name: String? = null) {
    val slicesBuilder = SliceGroupBuilder()
    val schedulingStateBuilder = SchedulingSliceFragment.Builder()

    fun hint(pid: Int = InvalidId, name: String? = null, tgid: Int = InvalidId, processName: String? = null) {
        if (this.id == InvalidId) this.id = pid
        if (this.name == null) this.name = name
        if (this.process.id == InvalidId) this.process.id = tgid
        if (this.process.name == null) this.process.name = processName
    }

    val slices: List<SliceGroup> get() {
        if (slicesBuilder.hasOpenSlices()) {
            throw IllegalStateException("SliceBuilder has open slices, not finished")
        }
        return slicesBuilder.slices
    }

    val schedSlices: List<SchedSlice> get() {
        // TODO: Close open slices
        return schedulingStateBuilder.slices
    }
}
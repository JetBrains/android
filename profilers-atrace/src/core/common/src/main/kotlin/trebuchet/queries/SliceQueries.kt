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

package trebuchet.queries

import trebuchet.model.Model
import trebuchet.model.ProcessModel
import trebuchet.model.ThreadModel
import trebuchet.model.base.Slice
import trebuchet.model.base.SliceGroup

object SliceQueries {
    fun selectAll(model: Model, cb: (Slice) -> Boolean): List<Slice> {
        val ret = mutableListOf<Slice>()
        iterSlices(model) {
            if (cb(it)) {
                ret.add(it)
            }
        }
        return ret
    }

    fun selectAll(thread: ThreadModel, cb: (Slice) -> Boolean): List<Slice> {
        val ret = mutableListOf<Slice>()
        iterSlices(thread) {
            if (cb(it)) {
                ret.add(it)
            }
        }
        return ret
    }

    private fun iterSlices(model: Model, cb: (Slice) -> Unit) {
        model.processes.values.forEach { iterSlices(it, cb) }
    }

    private fun iterSlices(process: ProcessModel, cb: (Slice) -> Unit) {
        process.threads.forEach { iterSlices(it, cb) }
    }

    private fun iterSlices(thread: ThreadModel, cb: (Slice) -> Unit) {
        iterSlices(thread.slices, cb)
    }

    private fun iterSlices(slices: List<SliceGroup>, cb: (Slice) -> Unit) {
        slices.forEach {
            cb(it)
            iterSlices(it.children, cb)
        }
    }

    fun any(slices: List<SliceGroup>, cb: (Slice) -> Boolean): Boolean {
        slices.forEach {
            if (cb(it)) return true
            if (any(it.children, cb)) return true
        }
        return false
    }
}
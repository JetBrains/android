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

package trebuchet.model

import trebuchet.model.fragments.ModelFragment

class Model constructor(fragments: Iterable<ModelFragment>) {
    val processes: Map<Int, ProcessModel>
    val cpus: List<CpuModel>
    val beginTimestamp: Double
    val endTimestamp: Double
    val parentTimestamp: Double
    val realtimeTimestamp: Long
    val duration get() = endTimestamp - beginTimestamp

    init {
        val processBuilder = mutableMapOf<Int, ProcessModel>()
        val cpuBuilder = mutableListOf<CpuModel>()
        var beginTimestamp = Double.MAX_VALUE
        var endTimestamp = 0.0
        var parentTimestamp = 0.0
        var realtimeTimestamp = 0L
        fragments.forEach {
            it.autoCloseOpenSlices()
            beginTimestamp = minOf(beginTimestamp, it.globalStartTime)
            endTimestamp = maxOf(endTimestamp, it.globalEndTime)
            parentTimestamp = maxOf(parentTimestamp, it.parentTimestamp)
            realtimeTimestamp = maxOf(realtimeTimestamp, it.realtimeTimestamp)
            it.processes.forEach {
                if (it.id != InvalidId) {
                    // TODO: Merge
                    processBuilder.put(it.id, ProcessModel(this, it))
                }
            }
            it.cpus.forEach {
                cpuBuilder.add(CpuModel(this, it))
            }
        }
        cpuBuilder.sortBy { it.id }
        processes = processBuilder
        cpus = cpuBuilder
        this.beginTimestamp = minOf(beginTimestamp, endTimestamp)
        this.endTimestamp = endTimestamp
        this.parentTimestamp = parentTimestamp
        this.realtimeTimestamp = realtimeTimestamp
    }

    constructor(fragment: ModelFragment) : this(listOf(fragment))

    fun isEmpty(): Boolean = processes.isEmpty()
}
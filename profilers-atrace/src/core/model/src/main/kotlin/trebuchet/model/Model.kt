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
    /**
     * For each process found in the atrace file we keep a map of process id to process model.
     */
    val processes: Map<Int, ProcessModel>
    /**
     * For each core we create a new cpu model. Each cpu model contains all the slices scheduled on that core.
     */
    val cpus: List<CpuModel>
    /**
     * The timestamp of the first event found. This timestamp by default is device boot time in seconds.
     */
    val beginTimestamp: Double
    /**
     * The last timestamp found in our list of entries. This timestamp by default is the device boot time in seconds.
     */
    val endTimestamp: Double
    /**
     * The last parent timestamp found in our trace file. This timestamp is clock monotonic time in seconds.
     */
    val parentTimestamp: Double
    /**
     * The boot time in seconds associated with the {@link #parentTimestamp} attribute.
     */
    val parentTimestampBootTime: Double
    /**
     * If the "realtime" marker is found in the trace file this field is set to that time in seconds.
     */
    val realtimeTimestamp: Long
    /**
     * The duration of this trace in seconds.
     */
    val duration get() = endTimestamp - beginTimestamp

    init {
        val processBuilder = mutableMapOf<Int, ProcessModel>()
        val cpuBuilder = mutableListOf<CpuModel>()
        var beginTimestamp = Double.MAX_VALUE
        var endTimestamp = 0.0
        var parentTimestamp = 0.0
        var parentTimestampBootTime = 0.0
        var realtimeTimestamp = 0L
        fragments.forEach {
            it.autoCloseOpenSlices()
            beginTimestamp = minOf(beginTimestamp, it.globalStartTime)
            endTimestamp = maxOf(endTimestamp, it.globalEndTime)
            parentTimestamp = maxOf(parentTimestamp, it.parentTimestamp)
            parentTimestampBootTime = maxOf(parentTimestampBootTime, it.parentTimestampBootTime)
            realtimeTimestamp = maxOf(realtimeTimestamp, it.realtimeTimestamp)
            it.processes.forEach {
                if (it.id != InvalidId) {
                    // TODO: Merge
                    processBuilder[it.id] = ProcessModel(this, it)
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
        this.parentTimestampBootTime = parentTimestampBootTime
        this.realtimeTimestamp = realtimeTimestamp
    }

    constructor(fragment: ModelFragment) : this(listOf(fragment))

    fun isEmpty(): Boolean = processes.isEmpty()
}
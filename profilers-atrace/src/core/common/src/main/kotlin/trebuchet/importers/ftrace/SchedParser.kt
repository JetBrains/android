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

package trebuchet.importers.ftrace

import trebuchet.model.SchedulingState
import trebuchet.model.fragments.ThreadModelFragment
import trebuchet.util.PreviewReader
import java.util.regex.Pattern

object SchedParser : FunctionHandlerRegistry() {
    init {
        "sched_switch" handleWith this::sched_switch
        "sched_waking" handleWith this::sched_wakeup
        "sched_wakeup" handleWith this::sched_wakeup
        "sched_blocked_reason" handleWith this::sched_blocked_reason
        "sched_cpu_hotplug" handleWith this::sched_cpu_hotplug
    }

    private val schedSwitchMatcher = matcher(
            "prev_comm=(.*) prev_pid=(\\d+) prev_prio=(\\d+) prev_state=([^\\s]+) ==> next_comm=(.*) next_pid=(\\d+) next_prio=(\\d+)")

    private fun sched_switch(data: ImportData) = data.readDetails {
        // sched_switch: prev_comm=atrace prev_pid=7100 prev_prio=120 prev_state=S
        // ==> next_comm=swapper/1 next_pid=0 next_prio=120
        match(schedSwitchMatcher) {
            val prevPid = int(2)
            val prevPrio = int(3)
            val prevState = read(4) { readSchedulingState() }
            val nextPid = int(6)
            val nextPrio = int(7)

            val prevThread = data.importer.threadFor(prevPid)
            if (prevThread.name == null) {
                prevThread.hint(name = string(1))
            }
            val nextThread = data.importer.threadFor(nextPid)
            if (nextThread.name == null) {
                nextThread.hint(name = string(5))
            }
            val cpu = data.importer.cpuFor(data.line.cpu)

            prevThread.schedulingStateBuilder.switchState(prevState, data.line.timestamp)
            nextThread.schedulingStateBuilder.switchState(SchedulingState.RUNNING, data.line.timestamp)
            cpu.schedulingProcessBuilder.switchProcess(nextThread.process, nextThread, data.line.timestamp)

        }
    }

    private val schedWakeupMatcher = matcher(
            """comm=(.+) pid=(\d+) prio=(\d+)(?: success=\d+)? target_cpu=(\d+)""")

    private fun sched_wakeup(data: ImportData) = data.readDetails {
        match(schedWakeupMatcher) {
            val pid = int(2)
            val thread = data.importer.threadFor(pid)
            if (thread.name == null) {
                thread.hint(name = string(1))
            }
            thread.schedulingStateBuilder.switchState(SchedulingState.WAKING, data.line.timestamp)
        }
    }

    private fun sched_blocked_reason(data: ImportData) = data.readDetails {

    }

    private fun sched_cpu_hotplug(data: ImportData) = data.readDetails {

    }

    private fun PreviewReader.readSchedulingState(): SchedulingState {
        val byte = readByte()
        return when (byte) {
            'S'.toByte() -> SchedulingState.SLEEPING
            'R'.toByte() -> SchedulingState.RUNNABLE
            'D'.toByte() -> {
                if (peek() == '|'.toByte()) {
                    skip()
                    return when (readByte()) {
                        'K'.toByte() -> SchedulingState.UNINTR_SLEEP_WAKE_KILL
                        'W'.toByte() -> SchedulingState.UNINTR_SLEEP_WAKING
                        else -> SchedulingState.UNINTR_SLEEP
                    }
                }
                SchedulingState.UNINTR_SLEEP
            }
            'T'.toByte() -> SchedulingState.STOPPED
            't'.toByte() -> SchedulingState.DEBUG
            'Z'.toByte() -> SchedulingState.ZOMBIE
            'X'.toByte() -> SchedulingState.EXIT_DEAD
            'x'.toByte() -> SchedulingState.TASK_DEAD
            'K'.toByte() -> SchedulingState.WAKE_KILL
            'W'.toByte() -> SchedulingState.WAKING
            else -> SchedulingState.UNKNOWN
        }
    }
}
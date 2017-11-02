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

object SchedParser : FunctionHandlerRegistry() {
    init {
        "sched_switch" handleWith this::sched_switch
        "sched_wakeup" handleWith this::sched_wakeup
        "sched_blocked_reason" handleWith this::sched_blocked_reason
        "sched_cpu_hotplug" handleWith this::sched_cpu_hotplug
    }

    fun sched_switch(data: ImportData) = data.readDetails {

    }

    fun sched_wakeup(data: ImportData) = data.readDetails {

    }

    fun sched_blocked_reason(data: ImportData) = data.readDetails {

    }

    fun sched_cpu_hotplug(data: ImportData) = data.readDetails {

    }
}
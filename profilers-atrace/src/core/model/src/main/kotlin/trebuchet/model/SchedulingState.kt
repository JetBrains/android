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

enum class SchedulingState(val friendlyName: String) {
    DEBUG("Debug"),
    EXIT_DEAD("Exit Dead"),
    RUNNABLE("Runnable"),
    RUNNING("Running"),
    SLEEPING("Sleeping"),
    STOPPED("Stopped"),
    TASK_DEAD("Task Dead"),
    UNINTR_SLEEP("Uninterruptible Sleep"),
    UNINTR_SLEEP_WAKE_KILL("Uninterruptible Sleep | WakeKill"),
    UNINTR_SLEEP_WAKING("Uninterruptible Sleep | Waking"),
    UNINTR_SLEEP_IO("Uninterruptible Sleep - Block I/O"),
    UNINTR_SLEEP_WAKE_KILL_IO("Uninterruptible Sleep | WakeKill - Block I/O"),
    UNINTR_SLEEP_WAKING_IO("Uninterruptible Sleep | Waking - Block I/O"),
    UNKNOWN("UNKNOWN"),
    WAKE_KILL("Wakekill"),
    WAKING("Waking"),
    ZOMBIE("Zombie"),
}
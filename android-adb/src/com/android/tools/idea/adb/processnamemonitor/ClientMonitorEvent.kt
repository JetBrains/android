/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.adb.processnamemonitor

/**
 * Client tracking events
 *
 * @param addedProcesses A map (pid -> process name) of processes added on the devices
 * @param removedProcesses A collection of processes removed from the device.
 */
internal class ClientMonitorEvent(private val addedProcesses: Map<Int, ProcessNames>, private val removedProcesses: Collection<Int>) {
  override fun toString(): String {
    val added = addedProcesses.entries.joinToString(prefix = "Added: [", postfix = "]") { "${it.key}->${it.value}" }
    val removed = removedProcesses.joinToString(prefix = "Removed: [", postfix = "]") { it.toString() }
    return "$added $removed"
  }

  operator fun component1() = addedProcesses
  operator fun component2() = removedProcesses
}
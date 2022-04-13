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
 * A convenience class representing a process.
 */
internal data class ProcessInfo(val pid: Int, val packageName: String, val processName: String) {
  val names = ProcessNames(packageName, processName)
  val asMapEntry = pid to names
}

/**
 * Convenience ClientMonitorEvent creation with just added processes
 */
internal fun clientsAddedEvent(vararg added: ProcessInfo): ClientMonitorEvent {
  return ClientMonitorEvent(added.associate { it.asMapEntry }, emptyList())
}

/**
 * Convenience ClientMonitorEvent creation with just removed processes
 */
internal fun clientsRemovedEvent(vararg removed: Int): ClientMonitorEvent {
  return ClientMonitorEvent(emptyMap(), removed.asList())
}

/**
 * Convenience ClientMonitorEvent creation
 */
internal fun clientMonitorEvent(added: List<ProcessInfo>, removed: List<Int>): ClientMonitorEvent {
  return ClientMonitorEvent(added.associate { it.asMapEntry }, removed)
}


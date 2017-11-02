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

import platform.collections.SparseArray
import trebuchet.importers.ImportFeedback
import trebuchet.model.InvalidId
import trebuchet.model.fragments.ModelFragment
import trebuchet.model.fragments.ProcessModelFragment
import trebuchet.model.fragments.ThreadModelFragment
import trebuchet.util.StringCache

class FtraceImporterState(feedback: ImportFeedback) {
    private val modelFragment = ModelFragment()
    private val pidMap = SparseArray<ThreadModelFragment>(50)
    private val handlers = FunctionRegistry.create()
    val stringCache = StringCache()
    private val importData = ImportData(this, feedback)

    fun finish(): ModelFragment {
        return modelFragment
    }

    fun importLine(line: FtraceLine) {
        if (modelFragment.globalStartTime == 0.0) {
            modelFragment.globalStartTime = line.timestamp
        }
        modelFragment.globalEndTime = line.timestamp

        if (line.hasTgid) threadFor(line)
        val handler = handlers[line.function] ?: return
        handler(importData.wrap(line))
    }

    private fun createProcess(tgid: Int, name: String? = null): ThreadModelFragment {
        val proc = ProcessModelFragment(tgid, name)
        modelFragment.processes.add(proc)
        val thread = proc.threadFor(tgid, name)
        if (pidMap[tgid] != null) {
            IllegalStateException("Unable to create process $tgid - already exists!")
        }
        pidMap.put(tgid, thread)
        return thread
    }

    fun processFor(tgid: Int, name: String? = null): ProcessModelFragment {
        val thread = pidMap[tgid] ?: createProcess(tgid, name)
        thread.process.hint(tgid, name)
        return thread.process
    }

    private fun createUnknownProcess(): ProcessModelFragment {
        return ProcessModelFragment(InvalidId, hasIdCb = { process ->
            val tgid = process.id
            val existing = pidMap[tgid]
            if (existing != null) {
                existing.process.merge(process)
            } else {
                pidMap.put(tgid, process.threadFor(tgid, process.name))
            }
            if (modelFragment.processes.none { it.id == process.id }) {
                modelFragment.processes.add(process)
            }
        })
    }

    fun threadFor(line: FtraceLine): ThreadModelFragment {
        var thread = pidMap[line.pid]
        val processName = if (line.tgid == line.pid) line.task else null
        if (thread != null) {
            thread.hint(name = line.task, tgid = line.tgid, processName = processName)
            return thread
        }
        val process =
                if (line.hasTgid) processFor(line.tgid)
                else createUnknownProcess()
        thread = process.threadFor(line.pid, line.task)
        thread.hint(processName = processName)
        pidMap.put(line.pid, thread)
        return thread
    }
}
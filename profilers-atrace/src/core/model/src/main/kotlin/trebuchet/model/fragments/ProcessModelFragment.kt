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
import trebuchet.model.hasCount

class ProcessModelFragment(id: Int, var name: String? = null,
                           private var hasIdCb: ((trebuchet.model.fragments.ProcessModelFragment) -> Unit)? = null) {
    private var _id: Int = id
    var id: Int
        get() = _id
        set(value) {
            _id = value
            if (_id != InvalidId) {
                hasIdCb?.invoke(this)
                hasIdCb = null
            }
        }

    private val _threads = mutableMapOf<Int, ThreadModelFragment>()
    private val _counters = mutableMapOf<String, CounterFragment>()

    val threads: Collection<ThreadModelFragment> get() = _threads.values
    val counters: Map<String, CounterFragment> get() = _counters

    fun threadFor(pid: Int, name: String? = null): ThreadModelFragment {
        var thread = _threads[pid]
        if (thread == null) {
            thread = ThreadModelFragment(pid, this, name)
            _threads.put(pid, thread)
        } else {
            thread.hint(name = name)
        }
        return thread
    }

    fun addCounterSample(name: String, timestamp: Double, value: Int) {
        _counters.getOrPut(name, { CounterFragment(name) }).events.add(timestamp hasCount value)
    }

    fun merge(other: trebuchet.model.fragments.ProcessModelFragment) {
        if (other === this) return
        if (id != -1 && id != other.id) {
            throw IllegalArgumentException("Process ID mismatch")
        }
        hint(name = other.name)
        other._threads.forEach { (key, value) ->
            if (_threads.put(key, value) != null) {
                throw IllegalStateException("Unable to merge threads of the same ID $key")
            }
            value.process = this
        }
        other._counters.forEach { (key, value) ->
            val existing = _counters.put(key, value)
            if (existing != null) {
                _counters[key]!!.events.addAll(existing.events)
            }
        }
    }

    fun hint(id: Int = InvalidId, name: String? = null) {
        if (this.id == InvalidId) this.id = id
        if (this.name == null) this.name = name
    }
}
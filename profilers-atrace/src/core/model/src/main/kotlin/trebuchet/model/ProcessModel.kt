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

import trebuchet.model.fragments.ProcessModelFragment

class ProcessModel constructor(val model: Model, fragment: ProcessModelFragment) {
    val id: Int = fragment.id
    val name: String = fragment.name ?: "<$id>"
    val threads: List<ThreadModel>
    val counters: List<Counter>
    val hasContent: Boolean

    init {
        if (id == InvalidId) throw IllegalArgumentException("Process has invalid id")
        val threadBuilder = mutableListOf<ThreadModel>()
        fragment.threads.forEach {
            threadBuilder.add(ThreadModel(this, it))
        }
        threadBuilder.sortBy { it.id }
        threads = threadBuilder
        counters = fragment.counters.values.filter { it.events.isNotEmpty() }.map { Counter(it) }.toList()
        hasContent = counters.isNotEmpty() || threads.any { it.hasContent }
    }
}
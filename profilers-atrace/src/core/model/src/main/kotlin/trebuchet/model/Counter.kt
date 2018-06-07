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

import trebuchet.model.fragments.CounterFragment

data class CounterValue(val timestamp: Double, val count: Int)

class Counter(val name: String, val events: List<CounterValue>) {
    constructor(fragment: CounterFragment) : this(fragment.name, fragment.events) {
        events.sortedBy { it.timestamp }
    }
}

infix fun Double.hasCount(value: Int): CounterValue = CounterValue(this, value)
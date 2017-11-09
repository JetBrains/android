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

import trebuchet.model.fragments.ThreadModelFragment

class ThreadModel constructor(val process: ProcessModel, fragment: ThreadModelFragment) {
    val id: Int = fragment.id
    val name: String = fragment.name ?: "<$id>"
    val slices = fragment.slices
    val schedSlices = fragment.schedSlices
    val hasContent = slices.isNotEmpty() && schedSlices.isNotEmpty()

    init {
        if (id == InvalidId) throw IllegalArgumentException("Thread has invalid id")
    }
}
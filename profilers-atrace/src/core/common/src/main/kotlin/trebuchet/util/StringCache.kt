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

package trebuchet.util

import trebuchet.io.DataSlice

class StringCache {
    // we're gonna have a lot of strings probably
    private val cache: MutableMap<DataSlice, String> = HashMap(1_000)
    private var cacheHits: Int = 0
        get private set

    fun stringFor(slice: DataSlice): String {
        var ret = cache[slice]
        if (ret == null) {
            ret = slice.toString()
            cache.put(slice.compact(), ret)
        } else {
            cacheHits++
        }
        return ret
    }
}
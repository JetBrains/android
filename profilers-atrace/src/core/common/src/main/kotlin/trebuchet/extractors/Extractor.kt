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

package trebuchet.extractors

import trebuchet.io.BufferProducer
import trebuchet.io.StreamingReader

/**
 * An extractor that operates on string data sources
 */
interface Extractor {
    /**
     * Starts extraction from the given data source.
     *
     * @param stream The data to extract from
     * @param callback A callback mechanism to begin extracting sub-streams as encountered. The callback can be invoked
     *                 as often as necessary. One extractor can produce multiple sub-streams.
     */
    fun extract(stream: StreamingReader, processSubStream: (BufferProducer) -> Unit)
}
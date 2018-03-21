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

package trebuchet.importers

import trebuchet.io.StreamingReader
import trebuchet.model.fragments.ModelFragment

interface Importer {
    /**
     * Produces a ModelFragment from the given DataStream. The importer may return null at any point if it
     * is unable to process the stream for whatever reason.
     *
     * @param stream The stream to read from
     * @return A ModelFragment built from the input, or null if the importer was unable to import
     */
    fun import(stream: StreamingReader): ModelFragment?
}
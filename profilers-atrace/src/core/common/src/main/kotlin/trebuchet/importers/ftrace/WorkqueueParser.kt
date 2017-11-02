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

import trebuchet.util.searchFor


object WorkqueueParser : FunctionHandlerRegistry() {
    init {
        "workqueue_execute_start" handleWith { data: ImportData ->
            data.readDetails {
                val thread = data.importer.threadFor(data.line)
                skipTo(StartFunction)
                skip(StartFunction.length + 1)
                val function = stringTo { skipUntil { it == ' '.toByte() } }
                thread.slicesBuilder.beginSlice {
                    it.name = function
                    it.startTime = data.line.timestamp
                }
            }
        }

        "workqueue_execute_end" handleWith { data: ImportData ->
            val thread = data.importer.threadFor(data.line)
            thread.slicesBuilder.endSlice {
                it.endTime = data.line.timestamp
            }
        }
    }

    private val StartFunction = searchFor("function")
}
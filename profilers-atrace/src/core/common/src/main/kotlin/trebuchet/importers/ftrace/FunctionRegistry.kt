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

import trebuchet.io.DataSlice
import trebuchet.io.asSlice


interface ParseFunction {
    operator fun invoke(data: ImportData): Unit
}

data class FunctionHandler(val name: String, val func: ParseFunction)

abstract class FunctionHandlerRegistry(val handlers: MutableList<FunctionHandler> = mutableListOf()) {
    protected infix fun String.handleWith(func: (ImportData) -> Unit) {
        handlers.add(FunctionHandler(this, object : ParseFunction {
            override fun invoke(data: ImportData) = func(data)
        }))
    }
}

object FunctionRegistry {
    fun create(): Map<DataSlice,  ParseFunction> {
        val lut = mutableMapOf<DataSlice, ParseFunction>()
        handlers.forEach { factory ->
            val instance = factory()
            instance.handlers.forEach {
                if (lut.put(it.name.asSlice(), it.func) != null) {
                    throw IllegalStateException("Found multiple handlers for ${it.name}")
                }
            }
        }
        return lut
    }

    private val handlers = arrayOf(
            { TracingMarkerWrite },
            { WorkqueueParser },
            { SchedParser }
    )
}
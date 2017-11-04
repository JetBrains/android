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

package trebuchet.extras

import trebuchet.model.Model
import trebuchet.task.ImportTask
import trebuchet.util.PrintlnImportFeedback
import java.io.File


fun parseTrace(file: File): Model {
    val before = System.nanoTime()
    val task = ImportTask(PrintlnImportFeedback())
    val model = task.import(InputStreamAdapter(file))
    val after = System.nanoTime()
    val duration = (after - before) / 1000000
    println("Parsing ${file.name} took ${duration}ms")
    return model
}

fun findSampleData(): String {
    var path = "sample_data"
    while (!File(path).exists()) {
        path = "../" + path
    }
    return path
}

fun openSample(name: String): Model {
    return parseTrace(File(findSampleData(), name))
}
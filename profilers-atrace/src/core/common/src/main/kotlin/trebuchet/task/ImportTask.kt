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

package trebuchet.task

import trebuchet.extractors.ExtractorRegistry
import trebuchet.importers.ImportFeedback
import trebuchet.importers.ImporterRegistry
import trebuchet.io.BufferProducer
import trebuchet.io.StreamingReader
import trebuchet.model.Model
import trebuchet.model.fragments.ModelFragment
import kotlin.system.measureTimeMillis

class ImportTask(private val importFeedback: ImportFeedback) {
    private val fragments = mutableListOf<ModelFragment>()

    fun importBuffer(source: BufferProducer): Model {
        return import(source)
    }

    fun import(source: BufferProducer): Model {
        var model: Model? = null
        val duration = measureTimeMillis {
            extractOrImport(source)
            model = finish()
        }
        println("Took ${duration}ms to import")
        return model!!
    }

    private fun extractOrImport(stream: BufferProducer) {
        try {
            val reader = StreamingReader(stream)
            reader.loadIndex(reader.keepLoadedSize)
            val extractor = ExtractorRegistry.extractorFor(reader, importFeedback)
            if (extractor != null) {
                extractor.extract(reader, this::extractOrImport)
            } else {
                addImporterSource(reader)
            }
        } catch (ex: Throwable) {
            importFeedback.reportImportException(ex)
        } finally {
            stream.close()
        }
    }

    private fun addImporterSource(reader: StreamingReader) {
        val importer = ImporterRegistry.importerFor(reader, importFeedback)
        if (importer != null) {
            val result = importer.import(reader)
            if (result != null) {
                fragments.add(result)
            }
        }
    }

    private fun finish(): Model {
        val model = Model(fragments)
        fragments.clear()
        return model
    }
}
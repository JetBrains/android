/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run.deployment.liveedit.desugaring

import com.android.tools.idea.run.deployment.liveedit.LiveEditLogger
import com.android.tools.r8.ClassFileResourceProvider
import java.nio.file.Path
import kotlin.io.path.absolute

// Manage a set of caches.
// There is one cache per jar file.
// Each cache contains all the entries in a jar
// Each cache is used to provide fast access to .class while R8 desugars.
internal class JarResourceCacheManager(private val logger: LiveEditLogger) : AutoCloseable {

  // Mapping path to a jar -> Cache of the the jar entries.
  private val cache : MutableMap<String, JarResourceCacheEntry> = HashMap()

  internal fun getResourceCache(file: Path) : ClassFileResourceProvider {
    val path = file.absolute().toString()

    if (!cache.containsKey(path)) {
      cache[path] = JarResourceCacheEntry(file, logger)
    }

    if (!cache[path]!!.isMapValid()) {
      cache[path]!!.finished(null)
      cache[path] = JarResourceCacheEntry(file, logger)
    }
    return cache[path]!!
  }

  // Release all resources
  override fun close() {
    cache.forEach{
      it.value.finished(null)
    }
    cache.clear()
  }

  // Called at the end of desugaring. This is when jar cache can be closed
  fun done() {
    val handler = R8DiagnosticHandler(logger)
    cache.forEach{
      it.value.finished(handler)
    }
  }
}
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
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.ProgramResource
import com.android.tools.r8.origin.ArchiveEntryOrigin
import com.android.tools.r8.origin.PathOrigin
import com.android.zipflinger.ZipMap
import com.android.zipflinger.ZipRepo
import com.intellij.util.io.lastModified
import java.nio.file.Path

// Ideally we would keep the ZipRepo open as long as the cache entry lives. However that would prevent jars to be
// updated on Windows since it also locks files and prevent deletion. To solve this issue, we close the zip
// repo (and re-open without parsing via ZipMap) once desugar ends (signaled by finished()).
internal class JarResourceCacheEntry(val path : Path, val logger: LiveEditLogger) : ClassFileResourceProvider{

  private val origin = PathOrigin(path)

  // Maps class descriptor names to jar entry name. e.g:
  //   Lcom.foo.bar.MyClass; -> com/foo/bar/Myclass.class
  // It is used to lookup the ZipMap when R8 requests a class descriptor (getProgramResource)
  private val descriptorNames = mutableMapOf<String, String>()
  private val zipMap = ZipMap.from(path)
  private var repo = ZipRepo(zipMap)
  private val lastModified = path.lastModified()

  init {
    zipMap.entries.forEach{
      val name = it.key!!
      if (!R8Utils.isClassFile(name)) {
        return@forEach // Skip this entry
      }
      descriptorNames[R8Utils.guessTypeDescriptor(name)] = name
    }
  }

  internal fun isMapValid() = (lastModified == path.lastModified())

  private fun ensureOpen() {
    if (repo.isOpen) {
      return
    }
    repo = ZipRepo(zipMap)
  }

  // We check for zipmap validity on here because it is a fairly expensive operation. This method is guaranteed to be called
  // before any call to getProgramResource().
  override fun getClassDescriptors(): Set<String> {
    ensureOpen()
    return descriptorNames.keys
  }

  // We don't check for ZipMap validity here because we already did it in getClassDescriptors.
  override fun getProgramResource(descriptor: String?): ProgramResource? {
    if (!descriptorNames.contains(descriptor)) {
      return null
    }

    ensureOpen()
    val name = descriptorNames[descriptor]!!
    val bytes = repo.getContent(name).array()
    return ProgramResource.fromBytes(ArchiveEntryOrigin(name, origin), ProgramResource.Kind.CF, bytes, setOf(descriptor))
  }

  override fun finished(handler: DiagnosticsHandler?) {
    super.finished(handler)
    repo.close()
  }
}
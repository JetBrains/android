/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run.classes

import com.android.tools.idea.projectsystem.ClassContent
import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.getPathFromFqcn
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import kotlin.streams.asSequence

class BazelClassFileFinder internal constructor(jars: Collection<Path>) : ClassFileFinder {
  private val classToJarMultimap = jars.asSequence().map { Jar(it) }.flatMap { it.entries }.groupBy({ it.toString() }, { it.jar })
  val jarCountForLoggingOnly = jars.size

  override fun findClassFile(fqcn: String): ClassContent {
    val path = getPathFromFqcn(fqcn)

    val jar = requireNotNull(classToJarMultimap[path]) { "$fqcn is expected to be in $classToJarMultimap" }
    return jar[0].getContent(path)
  }

  private class Jar(jar: Path) {
    private val jar: File = jar.toFile()
    val entries: Collection<Entry> = initEntries(jar, this)

    companion object {
      fun initEntries(jar: Path, container: Jar): Collection<Entry> {
        try {
          JarFile(jar.toFile()).use { jar ->
            return jar
              .stream()
              .asSequence()
              .map { Entry(it, container) }
              .filter { it.isNotDirectory }
              .filter { it.isNotInMetaInfDirectory }
              .toList()
          }
        } catch (exception: IOException) {
          Logger.getInstance(BazelClassFileFinder::class.java).warn(exception)
          return emptyList()
        }
      }
    }

    fun getContent(path: String): ClassContent {
      JarFile(this.jar).use { jar ->
        return ClassContent.fromJarEntryContent(this.jar, jar.getInputStream(jar.getEntry(path)).readAllBytes())
      }
    }
  }

  private class Entry(private val entry: ZipEntry, val jar: Jar) {
    val isNotDirectory: Boolean
      get() = !entry.isDirectory

    val isNotInMetaInfDirectory: Boolean
      get() = !entry.toString().startsWith("META-INF/")

    override fun toString(): String {
      return entry.toString()
    }
  }
}

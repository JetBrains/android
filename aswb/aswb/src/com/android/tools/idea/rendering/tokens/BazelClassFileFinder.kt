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
package com.android.tools.idea.rendering.tokens

import com.android.tools.idea.projectsystem.ClassContent
import com.android.tools.idea.projectsystem.ClassContent.Companion.fromJarEntryContent
import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.getPathFromFqcn
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.function.Function
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.zip.ZipEntry
import kotlin.streams.asSequence

internal class BazelClassFileFinder(jars: Collection<Path>) : ClassFileFinder {
  private val classToJarMultimap: Map<String, List<Jar>>

  init {
    classToJarMultimap = jars.asSequence()
      .map { Jar(it) }
      .flatMap { it.entries }
      .groupBy( { it.toString() }, {it.jar})
  }

  override fun findClassFile(c: String): ClassContent? {
    var c = c
    c = getPathFromFqcn(c)
    return classToJarMultimap[c]?.get(0)?.getContent(c)
  }

  private class Jar(jar: Path) {
    private val jar: File = jar.toFile()
    val entries: Collection<Entry> = initEntries(jar, this)

    companion object {
      fun initEntries(jar: Path, container: Jar): Collection<Entry> {
        try {
          JarFile(jar.toFile()).use { jar ->
            return jar.stream().asSequence()
              .map { Entry(it, container) }
              .filter { it.isNotDirectory }
              .filter { it.isNotInMetaInfDirectory }
              .toList()
          }
        }
        catch (exception: IOException) {
          Logger.getInstance(BazelClassFileFinder::class.java).warn(exception)
          return emptyList()
        }
      }
    }

    fun getContent(c: String?): ClassContent? {
      try {
        JarFile(this.jar).use { jar ->
          return fromJarEntryContent(this.jar, jar.getInputStream(jar.getEntry(c)).readAllBytes())
        }
      } catch (exception: IOException) {
        Logger.getInstance(BazelClassFileFinder::class.java).warn(exception)
        return null
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

/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.diagnostics

import com.google.common.collect.ImmutableList
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.util.io.createDirectories
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.Locale

/**
 * Stores list of hprof files and keeps the temporary directory where they are stored clean.
 */
class HProfDatabase(tmpDirectory: Path) {

  // $tmpDirectory/hprof-list.txt contains list of hprof files to process later.
  // All hprof files should be in $tmpDirectory/hprof-temp/
  private val hprofDatabase = tmpDirectory.resolve("hprof-list.txt").toAbsolutePath()
  private val hprofTempDirectory: Path = tmpDirectory.resolve("hprof-temp").toAbsolutePath()

  private val LOCK = Any()

  fun getPathsAndCleanupDatabase(): List<Path> {
    synchronized(LOCK) {
      try {
        val hprofPathsList: MutableList<Path> = mutableListOf()

        if (Files.isRegularFile(hprofDatabase)) {
          try {
            val allLines = Files.readAllLines(hprofDatabase)
            Files.deleteIfExists(hprofDatabase)

            val allPaths = allLines
              .map { Paths.get(it) }
              .map(Path::toAbsolutePath)

            // Delete all files that are not in hprof temp directory
            var exceptionThrown = false
            allPaths.filter { it.parent != hprofTempDirectory.toAbsolutePath() }.forEach {
              try {
                Files.deleteIfExists(it)
              } catch (_: IOException) {
                exceptionThrown = true
              }
            }

            //
            if (!exceptionThrown) {
              hprofPathsList.addAll(allPaths.filter { Files.isRegularFile(it) })
            }
          }
          catch (t: Throwable) {
            // If there was any problem with reading the database, ignore it and report
            // any list. This way, all files in temp directory will be deleted and we should be back in
            // good state.
          }
        }

        // Delete all files in temp directory, but ones that are to be returned
        val hprofPathsSet = hprofPathsList.toSet()
        Files.newDirectoryStream(hprofTempDirectory).use { stream ->
          stream.forEach { path ->
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                !hprofPathsSet.contains(path.toAbsolutePath())) {
              Files.delete(path)
            }
          }
        }
        return hprofPathsList
      }
      catch (_: IOException) {
        // Return empty list if there was any exception, so that any path
        // is never reported more than once.
        return ImmutableList.of()
      }
    }
  }

  fun appendHProfPath(path: Path) {
    appendLineToFile(hprofDatabase, path.toString())
  }

  private fun appendLineToFile(dbPath: Path, line: String) {
    synchronized(LOCK) {
      Files.createDirectories(dbPath.parent)
      Files.newBufferedWriter(dbPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND).use { writer ->
        writer.appendln(line)
      }
    }
  }

  fun createHprofTemporaryFilePath(): Path {
    val name = ApplicationNamesInfo.getInstance().productName.replace(' ', '-').toLowerCase(
      Locale.US)
    hprofTempDirectory.createDirectories()
    return hprofTempDirectory.resolve("heapDump-$name-${System.currentTimeMillis()}.hprof")
  }
}

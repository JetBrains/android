/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.rendering.classloading.loaders

import com.android.ide.common.util.PathString
import com.google.common.cache.AbstractCache
import com.jetbrains.rd.util.first
import org.jetbrains.kotlin.util.prefixIfNot
import org.junit.Assert
import org.junit.Test
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.util.concurrent.Callable

private class MapCache(val delegateMap: MutableMap<String, EntryCache>): AbstractCache<String, EntryCache>() {
  override fun get(key: String, valueLoader: Callable<out EntryCache>): EntryCache =
    delegateMap[key] ?: valueLoader.call().also {
      delegateMap[key] = it
    }
  override fun getIfPresent(key: Any): EntryCache? =
    delegateMap[key as String]

  override fun put(key: String, value: EntryCache) {
    delegateMap[key] = value
  }
}

fun createJarFile(outputJar: Path, contents: Map<String, ByteArray>): String {
  Assert.assertTrue(FileSystemProvider.installedProviders().any { it.scheme == "jar" })

  // Converts a Path to a jar into a portable path usable both in Linux/Mac and Windows. On Windows, paths do not start with / but this is
  // required to treat them as URI.
  val outputJarPath =  PathString(outputJar).portablePath.prefixIfNot("/")
  FileSystems.newFileSystem(URI.create("jar:file:$outputJarPath"), mapOf("create" to "true")).use {
    contents.forEach { (pathString, contents) ->
      val path = it.getPath(pathString)
      // Create parent directories if any
      path.parent?.let { Files.createDirectories(it) }
      Files.write(path, contents)
    }
  }


  return outputJarPath
}

class JarManagerUtilTest {
  private fun createSampleJarFile(): String {
    val outDirectory = Files.createTempDirectory("out")
    val jarFile = outDirectory.resolve("contents.jar")
    return createJarFile(jarFile, mapOf(
      "file1" to "contents1".encodeToByteArray(),
      "file2" to "contents2".encodeToByteArray(),
      "dir1/dir2/file1" to "dir1/dir2/contents1".encodeToByteArray(),
    ))
  }

  @Test
  fun `check single file loading with no cache`() {
    val jarFilePath = createSampleJarFile()

    val jarManagerNoPrefetch = JarManager()
    assertEquals(
      "contents1",
      String(jarManagerNoPrefetch.loadFileFromJar(URI("jar:file:$jarFilePath!/file1"))!!)
    )
    assertEquals(
      "contents2",
      String(jarManagerNoPrefetch.loadFileFromJar(URI("jar:file:/$jarFilePath!/file2"))!!)
    )
    assertEquals(
      "dir1/dir2/contents1",
      String(jarManagerNoPrefetch.loadFileFromJar(URI("jar:file:/$jarFilePath!/dir1/dir2/file1"))!!)
    )

    val jarManagerPrefetch = JarManager()
    assertEquals(
      "contents1",
      String(jarManagerPrefetch.loadFileFromJar(URI("jar:file:/$jarFilePath!/file1"))!!)
    )
    assertEquals(
      "contents2",
      String(jarManagerPrefetch.loadFileFromJar(URI("jar:file:/$jarFilePath!/file2"))!!)
    )
    assertEquals(
      "dir1/dir2/contents1",
      String(jarManagerPrefetch.loadFileFromJar(URI("jar:file:/$jarFilePath!/dir1/dir2/file1"))!!)
    )
  }

  @Test
  fun `check empty jar file no cache`() {
    val outDirectory = Files.createTempDirectory("out")
    val outDirectoryPath = PathString(outDirectory).portablePath
    val jarFilePath = PathString(outDirectory.resolve("contents.jar")).portablePath

    val jarManagerNoPrefetch = JarManager()
    assertNull(
      jarManagerNoPrefetch.loadFileFromJar(URI("jar:file:/$jarFilePath!/file1"))
    )
    assertNull(
      jarManagerNoPrefetch.loadFileFromJar(URI("jar:file:/$outDirectoryPath/notAFile.jar!/file1"))
    )

    val jarManagerPrefetch = JarManager()
    assertNull(
      jarManagerPrefetch.loadFileFromJar(URI("jar:file:/$jarFilePath!/file1"))
    )
    assertNull(
      jarManagerPrefetch.loadFileFromJar(URI("jar:file:/$outDirectoryPath/notAFile.jar!/file1"))
    )
  }

  @Test
  fun `check no-preload with cache`() {
    val jarFilePath = createSampleJarFile()
    val backingMap = mutableMapOf<String, EntryCache>()
    val cache = MapCache(backingMap)
    val jarManager = JarManager(jarFileCache = cache)

    assertEquals(
      "contents1",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/file1"))!!)
    )
    assertEquals(
      jarFilePath,
      backingMap.keys.single().prefixIfNot("/")
    )
    assertEquals(
      """
        file1
      """.trimIndent(),
      backingMap.first().value.keys.sorted().joinToString("\n")
    )

    assertEquals(
      "contents2",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/file2"))!!)
    )
    assertEquals(
      """
        file1
        file2
      """.trimIndent(),
      backingMap.first().value.keys.sorted().joinToString("\n")
    )
  }

  @Test
  fun `check preload with cache`() {
    val jarFilePath = createSampleJarFile()
    val backingMap = mutableMapOf<String, EntryCache>()
    val cache = MapCache(backingMap)
    val jarManager = JarManager(prefetchAllFiles = true, jarFileCache = cache)

    assertEquals(
      "contents1",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/file1"))!!)
    )
    assertEquals(
      jarFilePath,
      backingMap.keys.single().prefixIfNot("/")
    )
    assertEquals(
      """
        dir1/dir2/file1
        file1
        file2
      """.trimIndent(),
      backingMap.first().value.keys.sorted().joinToString("\n")
    )

    assertEquals(
      "contents2",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/file2"))!!)
    )
    assertNull(
      jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/notAFile"))
    )
    assertEquals(
      "contents1",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/file1"))!!)
    )
    assertEquals(
      """
        dir1/dir2/file1
        file1
        file2
        notAFile
      """.trimIndent(),
      backingMap.first().value.keys.sorted().joinToString("\n")
    )
  }
}
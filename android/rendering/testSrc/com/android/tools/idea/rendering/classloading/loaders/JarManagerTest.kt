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
import com.google.common.cache.Cache
import com.google.common.cache.ForwardingCache
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.util.prefixIfNot
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.Callable

private class MapCache(val delegateMap: MutableMap<String, EntryCache>) :
  AbstractCache<String, EntryCache>() {
  override fun get(key: String, valueLoader: Callable<out EntryCache>): EntryCache =
    delegateMap[key] ?: valueLoader.call().also { delegateMap[key] = it }
  override fun getIfPresent(key: Any): EntryCache? = delegateMap[key as String]

  override fun put(key: String, value: EntryCache) {
    delegateMap[key] = value
  }
}

fun createJarFile(outputJar: Path, contents: Map<String, ByteArray>): String {
  Assert.assertTrue(FileSystemProvider.installedProviders().any { it.scheme == "jar" })

  // Converts a Path to a jar into a portable path usable both in Linux/Mac and Windows. On Windows,
  // paths do not start with / but this is
  // required to treat them as URI.
  val outputJarPath = PathString(outputJar).portablePath.prefixIfNot("/")
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

private fun JarManager.getSortedPrefetchBannedJars(): String =
  getPrefetchBannedJars()
    .map { it.substringAfterLast("/") }
    .sorted()
    .joinToString(",")

class JarManagerUtilTest {
  private fun createSampleJarFile(name: String = "contents.jar"): String {
    val outDirectory = Files.createTempDirectory("out")
    val jarFile = outDirectory.resolve(name)
    return createJarFile(
      jarFile,
      mapOf(
        "file1" to "contents1".encodeToByteArray(),
        "file2" to "contents2".encodeToByteArray(),
        "dir1/dir2/file1" to "dir1/dir2/contents1".encodeToByteArray(),
      )
    )
  }

  @Test
  fun `check single file loading with no cache`() {
    val jarFilePath = createSampleJarFile()

    val jarManagerNoPrefetch = JarManager.withNoCache()
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
    assertTrue(jarManagerNoPrefetch.getPrefetchBannedJars().isEmpty())
  }

  @Test
  fun `check single file loading with cache`() {
    val jarFilePath = createSampleJarFile()

    run {
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
        String(
          jarManagerNoPrefetch.loadFileFromJar(URI("jar:file:/$jarFilePath!/dir1/dir2/file1"))!!
        )
      )
      assertTrue("No jars should have been banned from prefetch", jarManagerNoPrefetch.getPrefetchBannedJars().isEmpty())
    }

    run {
      val jarManagerPrefetch = JarManager()
      assertEquals(
        "contents1",
        String(jarManagerPrefetch.loadFileFromJar(URI("jar:file:$jarFilePath!/file1"))!!)
      )
      assertEquals(
        "contents2",
        String(jarManagerPrefetch.loadFileFromJar(URI("jar:file:/$jarFilePath!/file2"))!!)
      )
      assertEquals(
        "dir1/dir2/contents1",
        String(
          jarManagerPrefetch.loadFileFromJar(URI("jar:file:/$jarFilePath!/dir1/dir2/file1"))!!
        )
      )
      assertTrue("No jars should have been banned from prefetch", jarManagerPrefetch.getPrefetchBannedJars().isEmpty())
    }
  }

  @Test
  fun `check empty jar file no cache`() {
    val outDirectory = Files.createTempDirectory("out")
    val outDirectoryPath = PathString(outDirectory).portablePath
    val jarFilePath = PathString(outDirectory.resolve("contents.jar")).portablePath

    val jarManagerNoPrefetch = JarManager.withNoCache()
    assertNull(jarManagerNoPrefetch.loadFileFromJar(URI("jar:file:/$jarFilePath!/file1")))
    assertNull(
      jarManagerNoPrefetch.loadFileFromJar(URI("jar:file:/$outDirectoryPath/notAFile.jar!/file1"))
    )
  }

  @Test
  fun `check no-preload with cache`() {
    val jarFilePath = createSampleJarFile()
    val backingMap = mutableMapOf<String, EntryCache>()
    val cache = MapCache(backingMap)
    val jarManager = JarManager.forTesting(maxPrefetchFileSizeBytes = 0L, jarFileCache = cache)

    assertEquals(
      "contents1",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/file1"))!!)
    )
    assertEquals(jarFilePath, backingMap.keys.single().prefixIfNot("/"))
    assertEquals(
      """
        file1
      """
        .trimIndent(),
      backingMap.entries.first().value.keys.sorted().joinToString("\n")
    )

    assertEquals(
      "contents2",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/file2"))!!)
    )
    assertEquals(
      """
        file1
        file2
      """
        .trimIndent(),
      backingMap.entries.first().value.keys.sorted().joinToString("\n")
    )
  }

  @Test
  fun `check no-preload cache misses`() {
    val jarFilePath = createSampleJarFile()
    var cacheMisses = 0
    val jarManager = JarManager.forTesting(maxPrefetchFileSizeBytes = 0L) { cacheMisses++ }

    assertEquals(
      "contents1",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/file1"))!!)
    )

    assertEquals(
      "contents2",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/file2"))!!)
    )
    assertEquals(2, cacheMisses)
  }

  @Test
  fun `check preload with cache`() {
    val jarFilePath = createSampleJarFile()
    val backingMap = mutableMapOf<String, EntryCache>()
    val cache = MapCache(backingMap)
    val jarManager = JarManager.forTesting(jarFileCache = cache)

    assertEquals(
      "contents1",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/file1"))!!)
    )
    assertEquals(jarFilePath, backingMap.keys.single().prefixIfNot("/"))
    assertEquals(
      """
        dir1/dir2/file1
        file1
        file2
      """
        .trimIndent(),
      backingMap.entries.first().value.keys.sorted().joinToString("\n")
    )

    assertEquals(
      "contents2",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/file2"))!!)
    )
    assertNull(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/notAFile")))
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
      """
        .trimIndent(),
      backingMap.entries.first().value.keys.sorted().joinToString("\n")
    )
  }

  @Test
  fun `check jar banning when not able to cache them`() {
    val jar1 = createSampleJarFile("jar1.jar")
    val jar2 = createSampleJarFile("jar2.jar")
    val bannedJar1 = createSampleJarFile("bannedJar1.jar")
    val bannedJar2 = createSampleJarFile("bannedJar2.jar")
    val backingCache = MapCache(mutableMapOf())
    // This cache will only allow caching elements from the NOT banned jars, simulating that the
    // banned ones are maybe too large.
    val selectiveCache =
      object : ForwardingCache<String, EntryCache>() {
        override fun delegate(): Cache<String, EntryCache> = backingCache

        override fun get(key: String, valueLoader: Callable<out EntryCache>): EntryCache =
          if (key.substringAfterLast(File.separatorChar).contains("bannedJar")) {
            // Do not allow caching of this value, simply get it from the loader and return it.
            valueLoader.call()
          } else {
            super.get(key, valueLoader)
          }
      }
    val jarManager = JarManager.forTesting(jarFileCache = selectiveCache)

    assertEquals("contents1", String(jarManager.loadFileFromJar(URI("jar:file:/$jar1!/file1"))!!))
    assertEquals("", jarManager.getSortedPrefetchBannedJars())
    assertEquals(
      "contents1",
      String(jarManager.loadFileFromJar(URI("jar:file:/$bannedJar1!/file1"))!!)
    )
    assertEquals("bannedJar1.jar", jarManager.getSortedPrefetchBannedJars())
    assertEquals(
      "The contents of jar2 are expected to load even after being the prefetch banned jar list",
      "contents1",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jar1!/file1"))!!)
    )
    assertEquals("contents1", String(jarManager.loadFileFromJar(URI("jar:file:/$jar2!/file1"))!!))
    assertEquals(
      "contents1",
      String(jarManager.loadFileFromJar(URI("jar:file:/$bannedJar2!/file1"))!!)
    )
    assertEquals("bannedJar1.jar,bannedJar2.jar", jarManager.getSortedPrefetchBannedJars())
  }

  /**
   * Verifies that JAR replacement does not cause an exception when trying to update the cache.
   * Regression test for b/308140478.
   */
  @Test
  fun `check cache load after eviction`() {
    val backingMap = mutableMapOf<String, EntryCache>()
    val cache = MapCache(backingMap)
    val jarManager = JarManager.forTesting(jarFileCache = cache)

    val outDirectory = Files.createTempDirectory("out")
    val jarFile = outDirectory.resolve("content1.jar")
    // Single file jars in the past would trigger a specific code path
    // that would end up generating unexpectedly an immutable cache map
    // that would throw.
    // Create a sample jar file with 1 file that we will replace after it's
    // been loaded.
    val jarFilePath = createJarFile(
      jarFile,
      mapOf(
        "file1" to "contents1".encodeToByteArray(),
      )
    )

    assertEquals(
      "contents1",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/file1"))!!)
    )

    createJarFile(
      jarFile,
      mapOf(
        "file3" to "contents3".encodeToByteArray(),
      )
    )
    assertEquals(
      "contents3",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/file3"))!!)
    )
  }

  @Test
  fun `check cache invalidated`() {
    val outDirectory = Files.createTempDirectory("out")
    val jarFile = outDirectory.resolve("content1.jar")
    val jarFilePath = createJarFile(jarFile, mapOf("file1" to "contents1".encodeToByteArray()))
    val jarManager = JarManager()

    assertEquals(
      "contents1",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/file1"))!!)
    )

    jarFile.toFile().delete()
    createJarFile(jarFile, mapOf("file1" to "updatedcontents1".encodeToByteArray()))

    assertEquals(
      "contents1",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/file1"))!!)
    )

    jarManager.clearCache()

    assertEquals(
      "updatedcontents1",
      String(jarManager.loadFileFromJar(URI("jar:file:/$jarFilePath!/file1"))!!)
    )
  }
}

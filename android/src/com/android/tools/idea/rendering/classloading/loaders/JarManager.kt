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

import com.android.tools.idea.flags.StudioFlags
import com.google.common.cache.AbstractCache
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.io.ByteStreams
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.io.URLUtil
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.streams.asSequence
import org.jetbrains.annotations.TestOnly
import kotlin.io.path.invariantSeparatorsPathString

private fun defaultCache(maxWeight: Long) =
  CacheBuilder.newBuilder()
    .softValues()
    .weigher { _: String, value: EntryCache -> value.weight() }
    .maximumWeight(maxWeight)
    .build<String, EntryCache>()

typealias EntryCache = MutableMap<String, ByteArray?>

private fun EntryCache.weight() = values.sumOf { it?.size ?: 0 }

private val noCache =
  object : AbstractCache<String, EntryCache>() {
    override fun get(key: String, valueLoader: Callable<out EntryCache>): EntryCache =
      valueLoader.call()

    override fun getIfPresent(key: Any): EntryCache? = null

    override fun put(key: String, value: EntryCache) {}
  }

private fun loadBytes(file: ZipFile, entry: ZipEntry): ByteArray =
  ByteArray(entry.size.toInt()).also {
    @Suppress("UnstableApiUsage") ByteStreams.readFully(file.getInputStream(entry), it)
  }

/**
 * Loads all the entries in the given jar referred by the given [jarFilePath]. The path is expected
 * to have a "jar://path/to/jarfile.jar". If the [jarFilePath] contains any suffixes pointing to a
 * file after the "!/" separator, it will be ignored. This method will throw an exception if the
 * files can not be loaded.
 */
private fun loadAllFilesFromJarOnDisk(jarFilePath: Path): EntryCache =
  ZipFile(jarFilePath.toFile()).use {
    return it
      .stream()
      .asSequence()
      .filter { !it.isDirectory }
      .map { zipEntry -> zipEntry.name to loadBytes(it, zipEntry) }
      .toMap(mutableMapOf())
  }

/**
 * Loads a single file within a jar file located in [jarFilePath]. The file will be loaded from the
 * [filePath] within the jar. This method will throw an exception if the file can not be loaded.
 */
private fun loadFileFromJarOnDisk(jarFilePath: Path, filePath: String): ByteArray =
  ZipFile(jarFilePath.toFile()).use { zipFile ->
    val zipEntry = zipFile.getEntry(filePath) ?: throw FileNotFoundException(filePath)
    return loadBytes(zipFile, zipEntry)
  }

/** Same as [loadFileFromJarOnDisk] but it never throws and will return null instead. */
private fun loadFileFromJarOnDiskOrNull(jarFilePath: Path, filePath: String): ByteArray? =
  try {
    loadFileFromJarOnDisk(jarFilePath, filePath)
  } catch (_: IOException) {
    null
  } catch (_: IllegalArgumentException) {
    // The entry probably did not exist in the jar file
    null
  }

/** A class to assist with the loading of files from JAR files. */
@Service(Service.Level.PROJECT)
class JarManager {
  private val maxPrefetchFileSizeBytes: Long
  private val jarFileCache: Cache<String, EntryCache>
  /**
   * Callback to be used in tests when the [jarFileCache] can't find a value corresponding to the
   * given key in the cache.
   */
  private val cacheMissCallback: () -> Unit

  /** Creates a new [JarManager] with the default cache. */
  constructor(): this(
    StudioFlags.PROJECT_SYSTEM_CLASS_LOADER_CACHE_LIMIT.get(),
    defaultCache(StudioFlags.PROJECT_SYSTEM_CLASS_LOADER_CACHE_LIMIT.get()),
    {}
  )

  /**
   * If [maxPrefetchFileSizeBytes] is not 0, this cache will try to pre-fetch all the files in the same jar the
   * first time the jar is accessed file and add them to the given [jarFileCache] if any as long as the JAR size
   * is smaller than [maxPrefetchFileSizeBytes].
   *
   * The optional [cacheMissCallback] is used in tests when the [jarFileCache] can't find a value corresponding
   * to the given key in the cache.
   */
  private constructor(maxPrefetchFileSizeBytes: Long, jarFileCache: Cache<String, EntryCache>, cacheMissCallback: () -> Unit) {
    this.maxPrefetchFileSizeBytes = maxPrefetchFileSizeBytes
    this.jarFileCache = jarFileCache
    this.cacheMissCallback = cacheMissCallback
  }

  /**
   * [Cache] of files that are likely too large to be automatically pre-fetched. This will avoid
   * trying to load the full jar over and over when it is not possible to have it pre-fetched in
   * memory.
   */
  private val prefetchBannedJars =
    CacheBuilder.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build<String, Boolean>()

  @TestOnly fun getPrefetchBannedJars(): Collection<String> = prefetchBannedJars.asMap().keys

  /** Returns whether the given [jarPathString] should be prefetched. */
  private fun canPrefetchJar(jarPathString: String): Boolean =
    when {
      // No point prefetching if there is no cache
      jarFileCache == noCache -> false
      // We do not want to prefetch large jars that had been evicted in a previous load because of
      // their size
      prefetchBannedJars.getIfPresent(jarPathString) == true -> false
      // This ensures that we do not load the whole file to find out that it does not fit in memory.
      // If the file is larger than maxPreloadFileSizeBytes, then we add it to the banned list and
      // skip the load.
      // This is just a shortcut since even if the file is smaller than maxPrefetchFileSizeBytes, it might
      // not fit in memory once uncompressed. If this is the case, this is handled in the loadFileFromJar method
      // by checking if the entry is immediately evicted.
      Files.size(Paths.get(jarPathString)) > maxPrefetchFileSizeBytes -> {
        prefetchBannedJars.put(jarPathString, true)
        false
      }
      else -> true
    }

  /**
   * Loads a file from the given [uri] and returns its contents or null if the file does not exist.
   */
  fun loadFileFromJar(uri: URI): ByteArray? {
    val splitJarPaths =
      URLUtil.splitJarUrl(URLDecoder.decode(uri.toString(), Charsets.UTF_8)) ?: return null

    // On Windows, the url will start with //C:/.... we remove the prefix if it's there since it's not needed
    val jarFilePath = Paths.get(splitJarPaths.first.removePrefix("//"))
    return loadFileFromJar(jarFilePath, splitJarPaths.second)
  }

  /**
   * Loads a file from the given [jarFilePath] and [filePath] and returns its contents or null if the file does not exist.
   */
  fun loadFileFromJar(jarFilePath: Path, filePath: String): ByteArray? {

    val jarPathString = jarFilePath.invariantSeparatorsPathString
    val entryCache =
      jarFileCache.get(jarPathString) {
        cacheMissCallback()
        if (canPrefetchJar(jarPathString)) {
          loadAllFilesFromJarOnDisk(jarFilePath)
        } else mutableMapOf()
      }

    if (jarFileCache != noCache && jarFileCache.getIfPresent(jarPathString) == null) {
      // The key was likely evicted immediately because it's size. Mark it as non-cacheable.
      prefetchBannedJars.put(jarPathString, true)
    }

    var cachedEntry = entryCache.get(filePath)
    if (cachedEntry == null) {
      // The entry was not in the EntryCache, add it
      cachedEntry = loadFileFromJarOnDiskOrNull(jarFilePath, filePath)
      entryCache[filePath] = cachedEntry

      // Ensure that the weights are re-calculated by updating the jarFileCache entry. If the
      // cachedEntry is null, then, do not update as
      // it is size 0.
      if (cachedEntry != null) jarFileCache.put(jarPathString, entryCache)
    }

    return cachedEntry
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): JarManager = project.getService(JarManager::class.java)

    /** Creates a new [JarManager] only for testing that allows a custom cache to be used. */
    @TestOnly
    @JvmOverloads
    fun forTesting(
      maxPrefetchFileSizeBytes: Long = StudioFlags.PROJECT_SYSTEM_CLASS_LOADER_CACHE_LIMIT.get(),
      jarFileCache: Cache<String, EntryCache> =
        defaultCache(maxPrefetchFileSizeBytes),
      cacheMissCallback: () -> Unit = {}
    ) = JarManager(maxPrefetchFileSizeBytes, jarFileCache, cacheMissCallback)

    /** Creates a new [JarManager] with no cache. */
    @TestOnly
    fun withNoCache(): JarManager = JarManager(0L, noCache) {}
  }
}

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

import com.google.common.cache.AbstractCache
import com.google.common.cache.Cache
import com.google.common.io.ByteStreams
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.TestOnly
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.streams.asSequence

typealias EntryCache = Map<String, ByteArray?>

fun EntryCache.weight() =
  values.sumOf { it?.size ?: 0 }

private val noCache = object : AbstractCache<String, EntryCache>() {
  override fun get(key: String, valueLoader: Callable<out EntryCache>): EntryCache =
    valueLoader.call()

  override fun getIfPresent(key: Any): EntryCache? = null
}


private fun loadBytes(file: ZipFile, entry: ZipEntry): ByteArray =
  ByteArray(entry.size.toInt()).also {
    @Suppress("UnstableApiUsage")
    ByteStreams.readFully(file.getInputStream(entry), it)
  }

/**
 * Loads all the entries in the given jar referred by the given [jarPath]. The path is expected to
 * have a "jar://path/to/jarfile.jar". If the [jarPath] contains any suffixes pointing to a file after
 * the "!/" separator, it will be ignored.
 * This method will throw an exception if the files can not be loaded.
 */
private fun loadAllFilesFromJarOnDisk(jarPath: String): Map<String, ByteArray> {
  ZipFile(File(jarPath)).use {
    return it.stream()
      .asSequence()
      .filter { !it.isDirectory }
      .map { zipEntry ->
        zipEntry.name to loadBytes(it, zipEntry)
      }.toMap()
  }
}

/**
 * Loads a single file within a jar file located in [jarPath]. The file will be loaded from the [filePath]
 * within the jar.
 * This method will throw an exception if the file can not be loaded.
 */
private fun loadFileFromJarOnDisk(jarPath: String, filePath: String): ByteArray {
  ZipFile(File(jarPath)).use { zipFile ->
    val zipEntry = zipFile.getEntry(filePath) ?: throw FileNotFoundException(filePath)
    return loadBytes(zipFile, zipEntry)
  }
}

/**
 * A class to assist with the loading of files from JAR files.
 * If [prefetchAllFiles] is true, this method will try to pre-fetch all the files in the same jar the first time the jar is accessed
 * file and add them to the given [jarFileCache] if any.
 */
class JarManager(private val prefetchAllFiles: Boolean = false, private val jarFileCache: Cache<String, EntryCache> = noCache) {

  /**
   * Callback to be used in tests when the [jarFileCache] can't find a value corresponding to the given key in the cache.
   */
  @TestOnly var cacheMissCallback = {}

  /**
   * Loads a file from the given [path] and returns its contents or null if the file does not exist.
   */
  fun loadFileFromJar(uri: URI) : ByteArray? {
    val splitJarPaths = URLUtil.splitJarUrl(
      URLDecoder.decode(uri.toString(), Charsets.UTF_8))?: return null
    val jarPath = splitJarPaths.first
    val filePath = splitJarPaths.second

    val entryCache = jarFileCache.get(jarPath) {
      cacheMissCallback()
      if (prefetchAllFiles && jarFileCache != noCache) {
        loadAllFilesFromJarOnDisk(jarPath)
      }
      else mutableMapOf()
    }

    return (entryCache as? MutableMap)?.getOrPut(filePath) {
      try {
        loadFileFromJarOnDisk(jarPath, filePath)
      }
      catch (_: IOException) {
        null
      }
    }
  }
}
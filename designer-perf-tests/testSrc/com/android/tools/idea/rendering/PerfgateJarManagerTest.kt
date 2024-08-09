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
package com.android.tools.idea.rendering

import com.android.ide.common.util.PathString
import com.android.tools.idea.rendering.classloading.loaders.JarManager
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.android.tools.perflogger.Metric.MetricSample
import com.google.common.base.Stopwatch
import com.intellij.util.io.outputStream
import java.io.Closeable
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import kotlin.io.path.deleteIfExists
import kotlin.random.Random
import org.junit.Assert
import org.junit.Test

private const val NUMBER_OF_SAMPLES = 40

private val benchmark =
  Benchmark.Builder("DesignTools Jar Manager Benchmark")
    .setProject("Design Tools")
    .setDescription("Base line for Jar file access (mean) after $NUMBER_OF_SAMPLES samples.")
    .build()

/**
 * Holder for a jar file with utilities used in the tests. The path will be removed after this
 * [Closeable] is closed.
 */
@JvmInline
private value class Jar(val path: Path) : Closeable {
  /** Returns a URI to a file contained in the jar in the given [filePath]. */
  fun asURIToFile(filePath: String): URI {
    // Converts a Path to a jar into a portable path usable both in Linux/Mac and Windows. On
    // Windows,
    // paths do not start with / but this is
    // required to treat them as URI.
    val outputJarPath = "/${PathString(path).portablePath.removePrefix("/")}"
    return URI.create("jar:file:$outputJarPath!/file$filePath")
  }

  override fun close() {
    path.deleteIfExists()
  }
}

private fun createJarFile(outputJar: Jar, fileCount: Int, fileSizeBytes: Int) {
  Assert.assertTrue(FileSystemProvider.installedProviders().any { it.scheme == "jar" })

  val page = ByteArray(1_000_000.coerceAtMost(fileSizeBytes))
  val pageSize = page.size

  val random = Random(20230509L)

  FileSystems.newFileSystem(outputJar.path, mapOf("create" to "true")).use { fileSystem ->
    repeat(fileCount) {
      val fileName = "file$it"
      val path = fileSystem.getPath(fileName)

      path.outputStream().use { outputStream ->
        random.nextBytes(page)
        var remainingFileSize = fileSizeBytes
        while (remainingFileSize > 0) {
          outputStream.write(page, 0, pageSize.coerceAtMost(remainingFileSize))
          remainingFileSize -= pageSize
        }
      }
    }
  }
}

class PerfgateJarManagerTest {
  private val numberOfFiles = 40

  private fun createSampleJar(): Jar {
    val outDirectory = Files.createTempDirectory("out")
    val jarPath = Jar(outDirectory.resolve("contents.jar"))
    // Create a {numberOfFiles}MB jar file with {numberOfFiles} files of 5MB each
    createJarFile(jarPath, numberOfFiles, 5_000_000)

    return jarPath
  }

  @Test
  @Throws(Exception::class)
  fun testLargeJarWithNoCache() {
    createSampleJar().use { jar ->
      val samples: MutableList<MetricSample> = ArrayList(NUMBER_OF_SAMPLES)

      repeat(NUMBER_OF_SAMPLES) {
        val jarManager = JarManager.withNoCache()
        // val jarManager = JarManager.withCache(true)
        val stopWatch = Stopwatch.createStarted()
        (0 until numberOfFiles).shuffled().forEach {
          jarManager.loadFileFromJar(jar.asURIToFile(it.toString()))!!
        }
        samples.add(MetricSample(System.currentTimeMillis(), stopWatch.elapsed().toMillis()))
      }

      Metric("large_jar_no_cache_access_time").apply {
        addSamples(benchmark, *samples.toTypedArray())
        commit()
      }
    }
  }

  @Test
  @Throws(Exception::class)
  fun testLargeJarWithDefaultCache() {
    createSampleJar().use { jar ->
      val samples: MutableList<MetricSample> = ArrayList(NUMBER_OF_SAMPLES)

      repeat(NUMBER_OF_SAMPLES) {
        val jarManager = JarManager()
        val stopWatch = Stopwatch.createStarted()
        (0 until numberOfFiles).shuffled().forEach {
          jarManager.loadFileFromJar(jar.asURIToFile(it.toString()))!!
        }
        samples.add(MetricSample(System.currentTimeMillis(), stopWatch.elapsed().toMillis()))
      }

      Metric("large_jar_access_default_cache_time").apply {
        addSamples(benchmark, *samples.toTypedArray())
        commit()
      }
    }
  }

  @Test
  @Throws(Exception::class)
  fun testCacheableJar() {
    val outDirectory = Files.createTempDirectory("out")
    Jar(outDirectory.resolve("contents.jar")).use { jar ->
      // Create a {numberOfFiles}MB jar file with {numberOfFiles} files of 5MB each
      createJarFile(jar, 2500, 250)
      val samples: MutableList<MetricSample> = ArrayList(NUMBER_OF_SAMPLES)

      repeat(NUMBER_OF_SAMPLES) {
        val jarManager = JarManager()
        val stopWatch = Stopwatch.createStarted()
        (0 until numberOfFiles).shuffled().forEach {
          jarManager.loadFileFromJar(jar.asURIToFile(it.toString()))!!
        }
        samples.add(MetricSample(System.currentTimeMillis(), stopWatch.elapsed().toMillis()))
      }

      Metric("jar_access_default_cache_time").apply {
        addSamples(benchmark, *samples.toTypedArray())
        commit()
      }
    }
  }
}

/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.profilers.perfetto.traceconv

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TraceconvBundlerTest {

  @get:Rule val temporaryFolder = TemporaryFolder()

  @Test
  fun testBundleWhenBothSymbolsAndProguardMapsEmpty() {
    val traceFile = temporaryFolder.newFile("sample.heapprofd")

    val result = TraceconvBundler.bundle(traceFile)

    assertThat(result).isNull()
  }

  @Test
  fun testBundleWhenProguardMapFilesDoNotExist() {
    val traceFile = temporaryFolder.newFile("sample_missing_map.heapprofd")
    val nonExistentMap = File(temporaryFolder.root, "non_existent_map.txt")

    val result =
      TraceconvBundler.bundle(
        traceFile = traceFile,
        symbolDirs = emptyList(),
        proguardMaps = mapOf("com.example.app" to nonExistentMap.absolutePath),
      )

    assertThat(result).isNull()
  }

  @Test
  fun testBundleWhenFileDoesNotExist() {
    val traceFile = File(temporaryFolder.root, "non_existent.heapprofd")

    val result = TraceconvBundler.bundle(traceFile, listOf("/fake/path"))

    assertThat(result).isNull()
  }

  @Test
  fun testBundleConcurrentCallsAreDeduplicated() {
    val traceFile = temporaryFolder.newFile("sample_race.heapprofd")
    val numThreads = 3
    val executor = Executors.newFixedThreadPool(numThreads)
    val startLatch = CountDownLatch(1)
    val futures = mutableListOf<Future<File?>>()

    for (i in 0 until numThreads) {
      futures.add(
        executor.submit<File?> {
          startLatch.await()
          TraceconvBundler.bundle(traceFile, listOf("/fake/path"))
        }
      )
    }

    startLatch.countDown() // Release all threads simultaneously

    val results = futures.map { it.get(15, TimeUnit.SECONDS) }
    executor.shutdown()

    assertThat(results).hasSize(numThreads)
    assertThat(results.distinct()).hasSize(1)
  }

  @Test
  fun testBuildBundleCommandWithSymbolsOnly() {
    val traceFile = temporaryFolder.newFile("input.heapprofd")
    val outputFile = temporaryFolder.newFile("output.heapprofd")
    val symbolDirs = listOf("/path/to/symbols1", "/path/to/symbols2")

    val command = TraceconvBundler.buildBundleCommand(traceFile, outputFile, symbolDirs, verbose = false)

    assertThat(command)
      .containsExactly(
        TraceconvManager.getExecutablePath(),
        "bundle",
        "--symbol-paths",
        "/path/to/symbols1,/path/to/symbols2",
        traceFile.absolutePath,
        outputFile.absolutePath,
      )
      .inOrder()
  }

  @Test
  fun testBuildBundleCommandWithOnlyProguardMap() {
    val traceFile = temporaryFolder.newFile("input_pg.heapprofd")
    val outputFile = temporaryFolder.newFile("output_pg.heapprofd")
    val mappingFile = temporaryFolder.newFile("mapping.txt")

    val command =
      TraceconvBundler.buildBundleCommand(
        traceFile = traceFile,
        outputFile = outputFile,
        proguardMaps = mapOf("com.example.app" to mappingFile.absolutePath),
        verbose = false,
      )

    assertThat(command)
      .containsExactly(
        TraceconvManager.getExecutablePath(),
        "bundle",
        "--proguard-map",
        "com.example.app=${mappingFile.absolutePath}",
        traceFile.absolutePath,
        outputFile.absolutePath,
      )
      .inOrder()
  }

  @Test
  fun testBuildBundleCommandWithEmptyPackageNameFormatsWithoutPrefix() {
    val traceFile = temporaryFolder.newFile("input_no_pkg.heapprofd")
    val outputFile = temporaryFolder.newFile("output_no_pkg.heapprofd")
    val mappingFile = temporaryFolder.newFile("mapping_no_pkg.txt")

    val command =
      TraceconvBundler.buildBundleCommand(
        traceFile = traceFile,
        outputFile = outputFile,
        proguardMaps = mapOf("" to mappingFile.absolutePath),
        verbose = false,
      )

    assertThat(command)
      .containsExactly(
        TraceconvManager.getExecutablePath(),
        "bundle",
        "--proguard-map",
        mappingFile.absolutePath,
        traceFile.absolutePath,
        outputFile.absolutePath,
      )
      .inOrder()
  }

  @Test
  fun testBuildBundleCommandWithSymbolsAndProguardMap() {
    val traceFile = temporaryFolder.newFile("input2.heapprofd")
    val outputFile = temporaryFolder.newFile("output2.heapprofd")
    val mappingFile = temporaryFolder.newFile("mapping.txt")
    val symbolDirs = listOf("/path/to/symbols")

    val command =
      TraceconvBundler.buildBundleCommand(
        traceFile,
        outputFile,
        symbolDirs,
        mapOf("com.example.app" to mappingFile.absolutePath),
        verbose = false,
      )

    assertThat(command)
      .containsExactly(
        TraceconvManager.getExecutablePath(),
        "bundle",
        "--symbol-paths",
        "/path/to/symbols",
        "--proguard-map",
        "com.example.app=${mappingFile.absolutePath}",
        traceFile.absolutePath,
        outputFile.absolutePath,
      )
      .inOrder()
  }

  @Test
  fun testBuildBundleCommandWithVerbose() {
    val traceFile = temporaryFolder.newFile("input3.heapprofd")
    val outputFile = temporaryFolder.newFile("output3.heapprofd")
    val mappingFile = temporaryFolder.newFile("mapping3.txt")
    val symbolDirs = listOf("/path/to/symbols")

    val command =
      TraceconvBundler.buildBundleCommand(
        traceFile,
        outputFile,
        symbolDirs,
        mapOf("com.example.app" to mappingFile.absolutePath),
        verbose = true,
      )

    assertThat(command)
      .containsExactly(
        TraceconvManager.getExecutablePath(),
        "bundle",
        "--symbol-paths",
        "/path/to/symbols",
        "--proguard-map",
        "com.example.app=${mappingFile.absolutePath}",
        "--verbose",
        traceFile.absolutePath,
        outputFile.absolutePath,
      )
      .inOrder()
  }

  @Test
  fun testConfigureEnvironmentWhenSymbolizerExists() {
    val symbolizerFile = temporaryFolder.newFile("llvm-symbolizer")
    val env = mutableMapOf("PATH" to "/usr/bin")

    TraceconvBundler.configureEnvironment(env) { symbolizerFile.absolutePath }

    assertThat(env["PERFETTO_LLVM_SYMBOLIZER_PATH"]).isEqualTo(symbolizerFile.absolutePath)
    val symbolizerDir = symbolizerFile.parentFile.absolutePath
    assertThat(env["PATH"]).isEqualTo("$symbolizerDir${File.pathSeparator}/usr/bin")
  }

  @Test
  fun testConfigureEnvironmentWhenSymbolizerDoesNotExist() {
    val env = mutableMapOf<String, String>()

    TraceconvBundler.configureEnvironment(env) { "/non_existent_dir/llvm-symbolizer" }

    assertThat(env.containsKey("PERFETTO_LLVM_SYMBOLIZER_PATH")).isFalse()
  }
}

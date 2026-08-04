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
  fun testBundleWhenSymbolDirsEmpty() {
    val traceFile = temporaryFolder.newFile("sample.heapprofd")

    val result = TraceconvBundler.bundle(traceFile, emptyList())

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
  fun testBuildBundleCommand() {
    val traceFile = temporaryFolder.newFile("input.heapprofd")
    val outputFile = temporaryFolder.newFile("output.heapprofd")
    val symbolDirs = listOf("/path/to/symbols1", "/path/to/symbols2")

    val command = TraceconvBundler.buildBundleCommand(traceFile, outputFile, symbolDirs)

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

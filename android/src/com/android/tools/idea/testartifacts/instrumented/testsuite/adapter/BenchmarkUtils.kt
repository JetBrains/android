/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.adapter

import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.google.testing.platform.proto.api.core.TestResultProto
import com.intellij.openapi.util.io.FileUtil
import java.io.File

/**
 * A label used for the benchmark message output artifact from the AdditionalTestOutputPlugin in UTP.
 */
const val ADDITIONAL_TEST_OUTPUT_PLUGIN_BENCHMARK_MESSAGE_LABEL = "additionaltestoutput.benchmark.message"

/**
 * A label used for the benchmark trace file output artifact from the AdditionalTestOutputPlugin in UTP.
 */
const val ADDITIONAL_TEST_OUTPUT_PLUGIN_BENCHMARK_TRACE_LABEL = "additionaltestoutput.benchmark.trace"

/**
 * Finds benchmark output artifacts from [utpTestResultProto],
 * retrieves files and messages, and set them to [testCase].
 */
fun setBenchmarkContextAndPrepareFiles(utpTestResultProto: TestResultProto.TestResult,
                                       testCase: AndroidTestCase,
                                       resolveOutputArtifactFunc: (String) -> File = ::File) {
  utpTestResultProto.outputArtifactList.asSequence().filter { it.label.namespace == "android" }.forEach {
    when (it.label.label) {
      ADDITIONAL_TEST_OUTPUT_PLUGIN_BENCHMARK_MESSAGE_LABEL -> {
        val benchmarkMessageFile = resolveOutputArtifactFunc(it.sourcePath.path)
        if (benchmarkMessageFile.exists()) {
          testCase.benchmark = benchmarkMessageFile.readText()
        }
      }
      ADDITIONAL_TEST_OUTPUT_PLUGIN_BENCHMARK_TRACE_LABEL -> {
        val benchmarkTraceFile = resolveOutputArtifactFunc(it.sourcePath.path)
        if (benchmarkTraceFile.exists()) {
          // Copy trace files into Android Studio's temporary directory because
          // BenchmarkLinkListener assumes files are available in FileUtil.getTempDirectory().
          // TODO(b/194527508): Don't create a copy as a trace file can be large.
          val tempCopyFilePath = FileUtil.getTempDirectory() + File.separator + benchmarkTraceFile.name
          val tempCopyFile = File(tempCopyFilePath)
          benchmarkTraceFile.copyTo(tempCopyFile, overwrite = true)
          tempCopyFile.deleteOnExit()
        }
      }
    }
  }
}
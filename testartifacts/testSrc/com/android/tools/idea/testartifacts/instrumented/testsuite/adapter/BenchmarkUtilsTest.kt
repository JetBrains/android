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
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.proto.api.core.LabelProto
import com.google.testing.platform.proto.api.core.PathProto
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.intellij.openapi.util.io.FileUtil
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

/**
 * Unit tests for functions in BenchmarkUtils.kt.
 */
@RunWith(JUnit4::class)
class BenchmarkUtilsTest {
  @get:Rule
  val tempFolder: TemporaryFolder = TemporaryFolder()

  @Test
  fun testSetBenchmarkContextAndPrepareFiles() {
    val benchmarkMessageFile = tempFolder.newFile("benchmarkMessage.txt").apply {
      writeText("benchmarkMessage")
    }
    val benchmarkTraceFile = tempFolder.newFile("benchmarkTraceFile").apply {
      writeText("benchmarkTrace")
    }

    val testResultProto = TestResultProto.TestResult.newBuilder().apply {
      testCaseBuilder.apply {
        testPackage = "com.example.test"
        testClass = "ExampleTest"
        testMethod = "testExample"
      }
      addOutputArtifact(TestArtifactProto.Artifact.newBuilder().apply {
        label = LabelProto.Label.newBuilder().apply {
          namespace = "android"
          label = ADDITIONAL_TEST_OUTPUT_PLUGIN_BENCHMARK_MESSAGE_LABEL
        }.build()
        sourcePath = PathProto.Path.newBuilder().apply {
          path = benchmarkMessageFile.absolutePath
        }.build()
      })
      addOutputArtifact(TestArtifactProto.Artifact.newBuilder().apply {
        label = LabelProto.Label.newBuilder().apply {
          namespace = "android"
          label = ADDITIONAL_TEST_OUTPUT_PLUGIN_BENCHMARK_TRACE_LABEL
        }.build()
        sourcePath = PathProto.Path.newBuilder().apply {
          path = benchmarkTraceFile.absolutePath
        }.build()
      })
    }.build()

    val testCase = AndroidTestCase("id", "methodName", "className", "packageName")

    setBenchmarkContextAndPrepareFiles(testResultProto, testCase)

    assertThat(testCase.benchmark).isEqualTo("benchmarkMessage")
    assertThat(File(FileUtil.getTempDirectory() + File.separator + "benchmarkTraceFile").exists()).isTrue()
  }
}
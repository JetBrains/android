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
package com.android.tools.utp

import com.android.testutils.MockitoKt.eq
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto
import com.google.common.truth.Truth.assertThat
import com.android.tools.idea.protobuf.Any
import com.android.tools.idea.protobuf.GeneratedMessageV3
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.util.Base64

/**
 * Unit tests for [TaskOutputProcessor].
 */
@RunWith(JUnit4::class)
class TaskOutputProcessorTest {

  @get:Rule
  val rule: MockitoRule = MockitoJUnit.rule()

  @Mock
  lateinit var mockListener: TaskOutputProcessorListener

  @Test
  fun processNoUtpTag() {
    val input = """
      There are no UTP test result tag in this test input.
      So the input text should be returned as-is.
      """.trimIndent()
    val processor = TaskOutputProcessor(mapOf("" to mockListener))

    val processed = processor.process(input)

    assertThat(processed).isEqualTo(input)
    verifyNoInteractions(mockListener)
  }

  @Test
  fun processWithUtpTag() {
    val input = """
      Connected to process 6763 on device 'Pixel_3a_XL_API_28 [emulator-5554]'.
      ${testSuiteStartedEvent()}
      ${testCaseStartedEvent()}
      ${testCaseFinishedEvent()}
      ${testSuiteFinishedEvent()}
      > Task :app:connectedDebugAndroidTest
      """.trimIndent()
    val processor = TaskOutputProcessor(mapOf("" to mockListener))

    val processed = processor.process(input)

    assertThat(processed).isEqualTo("""
      Connected to process 6763 on device 'Pixel_3a_XL_API_28 [emulator-5554]'.
      > Task :app:connectedDebugAndroidTest
    """.trimIndent())

    inOrder(mockListener).apply {
      verify(mockListener).onTestSuiteStarted(eq(TestSuiteResultProto.TestSuiteMetaData.newBuilder().apply {
        scheduledTestCaseCount = 1
      }.build()))
      verify(mockListener).onTestCaseStarted(eq(TestCaseProto.TestCase.getDefaultInstance()))
      verify(mockListener).onTestCaseFinished(eq(TestResultProto.TestResult.getDefaultInstance()))
      verify(mockListener).onTestSuiteFinished(eq(TestSuiteResultProto.TestSuiteResult.getDefaultInstance()))
      verifyNoMoreInteractions()
    }
  }

  private fun testSuiteStartedEvent(): String {
    return GradleAndroidTestResultListenerProto.TestResultEvent.newBuilder().apply {
      testSuiteStartedBuilder.apply {
        testSuiteMetadata = Any.pack(TestSuiteResultProto.TestSuiteMetaData.newBuilder().apply {
          scheduledTestCaseCount = 1
        }.build())
      }
    }.build().toXml()
  }

  private fun testCaseStartedEvent(): String {
    return GradleAndroidTestResultListenerProto.TestResultEvent.newBuilder().apply {
      testCaseStartedBuilder.apply {
        testCase = Any.pack(TestCaseProto.TestCase.newBuilder().apply {
        }.build())
      }
    }.build().toXml()
  }

  private fun testCaseFinishedEvent(): String {
    return GradleAndroidTestResultListenerProto.TestResultEvent.newBuilder().apply {
      testCaseFinishedBuilder.apply {
        testCaseResult = Any.pack(TestResultProto.TestResult.newBuilder().apply {
        }.build())
      }
    }.build().toXml()
  }

  private fun testSuiteFinishedEvent(): String {
    return GradleAndroidTestResultListenerProto.TestResultEvent.newBuilder().apply {
      testSuiteFinishedBuilder.apply {
        testSuiteResult = Any.pack(TestSuiteResultProto.TestSuiteResult.newBuilder().apply {
        }.build())
      }
    }.build().toXml()
  }

  private fun GradleAndroidTestResultListenerProto.TestResultEvent.toXml(): String {
    return "<UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>${toBase64String()}</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>"
  }

  private fun GeneratedMessageV3.toBase64String(): String {
    return Base64.getEncoder().encodeToString(toByteArray())
  }
}
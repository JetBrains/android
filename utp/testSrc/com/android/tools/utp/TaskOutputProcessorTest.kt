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

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

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
    val processor = TaskOutputProcessor(listOf(mockListener))

    val processed = processor.process(input)

    assertThat(processed).isEqualTo(input)
    verifyNoInteractions(mockListener)
  }

  @Test
  fun processWithUtpTag() {
    val input = """
      Connected to process 6763 on device 'Pixel_3a_XL_API_28 [emulator-5554]'.
      <UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>CgAqDWVtdWxhdG9yLTU1NTQ=</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>
      <UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>EgAqDWVtdWxhdG9yLTU1NTQ=</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>
      <UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>GgAqDWVtdWxhdG9yLTU1NTQ=</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>
      <UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>IgAqDWVtdWxhdG9yLTU1NTQ=</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>
      <UTP_TEST_RESULT_ON_COMPLETED />
      > Task :app:connectedDebugAndroidTest
      """.trimIndent()
    val processor = TaskOutputProcessor(listOf(mockListener))

    val processed = processor.process(input)

    assertThat(processed).isEqualTo("""
      Connected to process 6763 on device 'Pixel_3a_XL_API_28 [emulator-5554]'.
      > Task :app:connectedDebugAndroidTest
    """.trimIndent())

    inOrder(mockListener).apply {
      verify(mockListener).onTestSuiteStarted()
      verify(mockListener).onTestCaseStarted()
      verify(mockListener).onTestCaseFinished()
      verify(mockListener).onTestSuiteFinished()
      verify(mockListener).onComplete()
      verifyNoMoreInteractions()
    }
  }
}
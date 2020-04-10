/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.testutils.MockitoKt.any
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.google.protobuf.InvalidProtocolBufferException
import com.google.test.platform.core.proto.TestCaseProto
import com.google.test.platform.core.proto.TestResultProto
import com.google.test.platform.core.proto.TestStatusProto
import com.google.test.platform.core.proto.TestSuiteResultProto
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.argThat
import org.mockito.Mockito.inOrder
import org.mockito.MockitoAnnotations
import kotlin.test.assertFailsWith

@RunWith(JUnit4::class)
class UtpTestResultAdapterTest {
  @Mock
  lateinit var mockListener: AndroidTestResultListener
  @Mock
  lateinit var mockAndroidTestCase: AndroidTestCase
  @Mock
  lateinit var mockAndroidTestSuite: AndroidTestSuite
  lateinit var utpTestResultAdapter: UtpTestResultAdapter

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
    utpTestResultAdapter = UtpTestResultAdapter(mockListener)
  }

  @Test
  fun invalidProtobuf() {
    val protoString = "invalid"
    val inputStream = protoString.byteInputStream()
    assertFailsWith<InvalidProtocolBufferException>() { utpTestResultAdapter.importResult(inputStream) }
  }

  @Test
  fun importSuccessTestResults() {
    val protobuf = TestSuiteResultProto.TestSuiteResult.newBuilder()
      .addTestResult(
        TestResultProto.TestResult.newBuilder()
          .setTestCase(TestCaseProto.TestCase.newBuilder()
                         .setTestClass("ExampleInstrumentedTest")
                         .setTestPackage("com.example.application")
                         .setTestMethod("useAppContext"))
          .setTestStatus(TestStatusProto.TestStatus.PASSED)
      ).addTestResult(
        TestResultProto.TestResult.newBuilder()
          .setTestCase(TestCaseProto.TestCase.newBuilder()
                         .setTestClass("ExampleInstrumentedTest")
                         .setTestPackage("com.example.application")
                         .setTestMethod("useAppContext2"))
          .setTestStatus(TestStatusProto.TestStatus.PASSED)
      ).build()
    utpTestResultAdapter.importResult(protobuf.toByteArray().inputStream())
    val verifyInOrder = inOrder(mockListener)
    verifyInOrder.verify(mockListener).onTestSuiteScheduled(any())
    verifyInOrder.verify(mockListener).onTestSuiteStarted(any(), any())
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    // Use ?: mockAndroidTestCase to workaround kotlin mockito non-nullable compatibility issue
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      it!!.result == AndroidTestCaseResult.PASSED
    } ?: mockAndroidTestCase)
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      it!!.result == AndroidTestCaseResult.PASSED
    } ?: mockAndroidTestCase)
    verifyInOrder.verify(mockListener).onTestSuiteFinished(any(), argThat {
      it!!.result == AndroidTestSuiteResult.PASSED
    } ?: mockAndroidTestSuite)
  }

  @Test
  fun importPartialFailedTestResults() {
    val protobuf = TestSuiteResultProto.TestSuiteResult.newBuilder()
      .addTestResult(
        TestResultProto.TestResult.newBuilder()
          .setTestCase(TestCaseProto.TestCase.newBuilder()
                         .setTestClass("ExampleInstrumentedTest")
                         .setTestPackage
                         ("com.example.application")
                         .setTestMethod("useAppContext"))
          .setTestStatus(TestStatusProto.TestStatus.PASSED)
      ).addTestResult(
        TestResultProto.TestResult.newBuilder()
          .setTestCase(TestCaseProto.TestCase.newBuilder()
                         .setTestClass("ExampleInstrumentedTest")
                         .setTestPackage
                         ("com.example.application")
                         .setTestMethod("useAppContext2"))
          .setTestStatus(TestStatusProto.TestStatus.FAILED)
      ).build()
    utpTestResultAdapter.importResult(protobuf.toByteArray().inputStream())
    val verifyInOrder = inOrder(mockListener)
    verifyInOrder.verify(mockListener).onTestSuiteScheduled(any())
    verifyInOrder.verify(mockListener).onTestSuiteStarted(any(), any())
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    // Use ?: mockAndroidTestCase to workaround kotlin mockito non-nullable compatibility issue
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      it!!.result == AndroidTestCaseResult.PASSED
    } ?: mockAndroidTestCase)
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      it!!.result == AndroidTestCaseResult.FAILED
    } ?: mockAndroidTestCase)
    verifyInOrder.verify(mockListener).onTestSuiteFinished(any(), argThat {
      it!!.result == AndroidTestSuiteResult.FAILED
    } ?: mockAndroidTestSuite)
  }

  @Test
  fun importSkippedTestResults() {
    val protobuf = TestSuiteResultProto.TestSuiteResult.newBuilder()
      .addTestResult(
        TestResultProto.TestResult.newBuilder()
          .setTestCase(TestCaseProto.TestCase.newBuilder()
                         .setTestClass("ExampleInstrumentedTest")
                         .setTestPackage("com.example.application")
                         .setTestMethod("useAppContext"))
          .setTestStatus(TestStatusProto.TestStatus.ERROR)
      ).build()
    utpTestResultAdapter.importResult(protobuf.toByteArray().inputStream())
    val verifyInOrder = inOrder(mockListener)
    verifyInOrder.verify(mockListener).onTestSuiteScheduled(any())
    verifyInOrder.verify(mockListener).onTestSuiteStarted(any(), any())
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    // Use ?: mockAndroidTestCase to workaround kotlin mockito non-nullable compatibility issue
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      it!!.result == AndroidTestCaseResult.SKIPPED
    } ?: mockAndroidTestCase)
    verifyInOrder.verify(mockListener).onTestSuiteFinished(any(), argThat {
      it!!.result == AndroidTestSuiteResult.PASSED
    } ?: mockAndroidTestSuite)
  }
}
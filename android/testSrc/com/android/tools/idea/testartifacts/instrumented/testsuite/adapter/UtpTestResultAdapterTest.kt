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
import com.google.testing.platform.proto.api.core.PathProto
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.argThat
import org.mockito.Mockito.inOrder
import org.mockito.MockitoAnnotations
import java.io.File
import java.nio.charset.Charset
import kotlin.test.assertFailsWith

private const val TEST_PACKAGE_NAME = "com.example.application"

@RunWith(JUnit4::class)
class UtpTestResultAdapterTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Mock
  lateinit var mockListener: AndroidTestResultListener
  @Mock
  lateinit var mockAndroidTestCase: AndroidTestCase
  @Mock
  lateinit var mockAndroidTestSuite: AndroidTestSuite
  lateinit var utpTestResultAdapter: UtpTestResultAdapter
  private lateinit var utpProtoFile: File

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
    utpTestResultAdapter = UtpTestResultAdapter(mockListener)
    utpProtoFile = temporaryFolder.newFile()
  }

  @Test
  fun invalidProtobuf() {
    utpProtoFile.outputStream().write("Invalid".toByteArray(Charset.defaultCharset()))
    assertFailsWith<InvalidProtocolBufferException>() { utpTestResultAdapter.importResult(utpProtoFile) }
  }

  @Test
  fun importSuccessTestResults() {
    val protobuf = TestSuiteResultProto.TestSuiteResult.newBuilder()
      .addTestResult(
        TestResultProto.TestResult.newBuilder()
          .setTestCase(TestCaseProto.TestCase.newBuilder()
                         .setTestClass("ExampleInstrumentedTest")
                         .setTestPackage(TEST_PACKAGE_NAME)
                         .setTestMethod("useAppContext"))
          .setTestStatus(TestStatusProto.TestStatus.PASSED)
      ).addTestResult(
        TestResultProto.TestResult.newBuilder()
          .setTestCase(TestCaseProto.TestCase.newBuilder()
                         .setTestClass("ExampleInstrumentedTest")
                         .setTestPackage(TEST_PACKAGE_NAME)
                         .setTestMethod("useAppContext2"))
          .setTestStatus(TestStatusProto.TestStatus.PASSED)
      ).build()
    protobuf.writeTo(utpProtoFile.outputStream())
    utpTestResultAdapter.importResult(utpProtoFile)
    val verifyInOrder = inOrder(mockListener)
    verifyInOrder.verify(mockListener).onTestSuiteScheduled(any())
    verifyInOrder.verify(mockListener).onTestSuiteStarted(any(), any())
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      it!!.result == AndroidTestCaseResult.PASSED && it!!.retentionSnapshot == null && it!!.packageName == TEST_PACKAGE_NAME
    } ?: mockAndroidTestCase)
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      it!!.result == AndroidTestCaseResult.PASSED && it!!.retentionSnapshot == null && it!!.packageName == TEST_PACKAGE_NAME
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
                         .setTestPackage(TEST_PACKAGE_NAME)
                         .setTestMethod("useAppContext"))
          .setTestStatus(TestStatusProto.TestStatus.PASSED)
      ).addTestResult(
        TestResultProto.TestResult.newBuilder()
          .setTestCase(TestCaseProto.TestCase.newBuilder()
                         .setTestClass("ExampleInstrumentedTest")
                         .setTestPackage(TEST_PACKAGE_NAME)
                         .setTestMethod("useAppContext2"))
          .setTestStatus(TestStatusProto.TestStatus.FAILED)
      ).build()
    protobuf.writeTo(utpProtoFile.outputStream())
    utpTestResultAdapter.importResult(utpProtoFile)
    val verifyInOrder = inOrder(mockListener)
    verifyInOrder.verify(mockListener).onTestSuiteScheduled(any())
    verifyInOrder.verify(mockListener).onTestSuiteStarted(any(), any())
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      it!!.result == AndroidTestCaseResult.PASSED
    } ?: mockAndroidTestCase)
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      it!!.result == AndroidTestCaseResult.FAILED && it!!.retentionSnapshot == null && it!!.packageName == TEST_PACKAGE_NAME
    } ?: mockAndroidTestCase)
    verifyInOrder.verify(mockListener).onTestSuiteFinished(any(), argThat {
      it!!.result == AndroidTestSuiteResult.FAILED
    } ?: mockAndroidTestSuite)
  }

  @Test
  fun importFailedTestResultWithoutSnapshot() {
    val unrelatedArtifact = "foo"
    val protobuf = TestSuiteResultProto.TestSuiteResult.newBuilder()
      .addTestResult(
        TestResultProto.TestResult.newBuilder()
          .setTestCase(TestCaseProto.TestCase.newBuilder()
                         .setTestClass("ExampleInstrumentedTest")
                         .setTestPackage
                         ("com.example.application")
                         .setTestMethod("useAppContext"))
          .setTestStatus(TestStatusProto.TestStatus.FAILED)
          .addOutputArtifact(TestArtifactProto.Artifact.newBuilder()
                               .setSourcePath(PathProto.Path.newBuilder()
                                                .setPath(unrelatedArtifact)))
      ).build()
    protobuf.writeTo(utpProtoFile.outputStream())
    utpTestResultAdapter.importResult(utpProtoFile)
    val verifyInOrder = inOrder(mockListener)
    verifyInOrder.verify(mockListener).onTestSuiteScheduled(any())
    verifyInOrder.verify(mockListener).onTestSuiteStarted(any(), any())
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      it!!.result == AndroidTestCaseResult.FAILED && it!!.retentionSnapshot == null
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
                         .setTestPackage(TEST_PACKAGE_NAME)
                         .setTestMethod("useAppContext"))
          .setTestStatus(TestStatusProto.TestStatus.ERROR)
      ).build()
    protobuf.writeTo(utpProtoFile.outputStream())
    utpTestResultAdapter.importResult(utpProtoFile)
    val verifyInOrder = inOrder(mockListener)
    verifyInOrder.verify(mockListener).onTestSuiteScheduled(any())
    verifyInOrder.verify(mockListener).onTestSuiteStarted(any(), any())
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      it!!.result == AndroidTestCaseResult.SKIPPED && it!!.retentionSnapshot == null && it!!.packageName == TEST_PACKAGE_NAME
    } ?: mockAndroidTestCase)
    verifyInOrder.verify(mockListener).onTestSuiteFinished(any(), argThat {
      it!!.result == AndroidTestSuiteResult.PASSED
    } ?: mockAndroidTestSuite)
  }
}
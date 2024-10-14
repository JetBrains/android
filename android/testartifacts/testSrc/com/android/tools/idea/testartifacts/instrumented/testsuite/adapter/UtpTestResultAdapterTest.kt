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

import com.android.tools.idea.protobuf.InvalidProtocolBufferException
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.android.tools.utp.plugins.host.device.info.proto.AndroidTestDeviceInfoProto
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.proto.api.core.LabelProto
import com.google.testing.platform.proto.api.core.PathProto
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.intellij.openapi.util.io.FileUtil
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatcher
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import java.io.File
import java.nio.charset.Charset
import kotlin.test.assertFailsWith

private const val TEST_PACKAGE_NAME = "com.example.application"

@RunWith(JUnit4::class)
class UtpTestResultAdapterTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @get:Rule
  var mockitoRule: MockitoRule = MockitoJUnit.rule()

  @Mock
  lateinit var mockListener: AndroidTestResultListener

  private val utpProtoFile: File by lazy {
    temporaryFolder.newFile()
  }

  @Test
  fun invalidProtobuf() {
    utpProtoFile.outputStream().write("Invalid".toByteArray(Charset.defaultCharset()))
    assertFailsWith<InvalidProtocolBufferException> {
      UtpTestResultAdapter(utpProtoFile).packageName
    }
  }

  @Test
  fun getPackageName() {
    val protobuf = TestSuiteResultProto.TestSuiteResult.newBuilder()
      .addTestResult(
        TestResultProto.TestResult.newBuilder()
          .setTestCase(TestCaseProto.TestCase.newBuilder()
                         .setTestClass("ExampleInstrumentedTest")
                         .setTestPackage(TEST_PACKAGE_NAME)
                         .setTestMethod("useAppContext"))
          .setTestStatus(TestStatusProto.TestStatus.PASSED)
      ).build()
    protobuf.writeTo(utpProtoFile.outputStream())
    val utpTestResultAdapter = UtpTestResultAdapter(utpProtoFile)
    assertThat(utpTestResultAdapter.packageName).isEqualTo(TEST_PACKAGE_NAME)
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
    val utpTestResultAdapter = UtpTestResultAdapter(utpProtoFile)
    utpTestResultAdapter.forwardResults(mockListener)
    val verifyInOrder = inOrder(mockListener)
    verifyInOrder.verify(mockListener).onTestSuiteScheduled(any())
    verifyInOrder.verify(mockListener).onTestSuiteStarted(any(), any())
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      result == AndroidTestCaseResult.PASSED && retentionSnapshot == null && packageName == TEST_PACKAGE_NAME
    })
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      result == AndroidTestCaseResult.PASSED && retentionSnapshot == null && packageName == TEST_PACKAGE_NAME
    })
    verifyInOrder.verify(mockListener).onTestSuiteFinished(any(), argThat {
      result == AndroidTestSuiteResult.PASSED
    })
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
    val utpTestResultAdapter = UtpTestResultAdapter(utpProtoFile)
    utpTestResultAdapter.forwardResults(mockListener)
    val verifyInOrder = inOrder(mockListener)
    verifyInOrder.verify(mockListener).onTestSuiteScheduled(any())
    verifyInOrder.verify(mockListener).onTestSuiteStarted(any(), any())
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      result == AndroidTestCaseResult.PASSED
    })
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      result == AndroidTestCaseResult.FAILED && retentionSnapshot == null && packageName == TEST_PACKAGE_NAME
    })
    verifyInOrder.verify(mockListener).onTestSuiteFinished(any(), argThat {
      result == AndroidTestSuiteResult.FAILED
    })
  }

  @Test
  fun importFailedTestResultsWithSnapshots() {
    val testClass = "ExampleInstrumentedTest"
    val testMethod1 = "useAppContext"
    val testMethod2 = "useAppContext2"
    val snapshotName1 = "snapshot-${testClass}-${testMethod1}-failure0.tar"
    val snapshotName2 = "snapshot-${testClass}-${testMethod2}-failure1.tar.gz"
    val failedSnapshot1 = temporaryFolder.newFile(snapshotName1)
    val failedSnapshot2 = temporaryFolder.newFile(snapshotName2)
    val protobuf = TestSuiteResultProto.TestSuiteResult.newBuilder()
      .addTestResult(
        TestResultProto.TestResult.newBuilder()
          .setTestCase(TestCaseProto.TestCase.newBuilder()
                         .setTestClass(testClass)
                         .setTestPackage(TEST_PACKAGE_NAME)
                         .setTestMethod(testMethod1))
          .setTestStatus(TestStatusProto.TestStatus.FAILED)
          .addOutputArtifact(
            TestArtifactProto.Artifact.newBuilder().setSourcePath(PathProto.Path.newBuilder().setPath(snapshotName1))
          )
      ).addTestResult(
        TestResultProto.TestResult.newBuilder()
          .setTestCase(TestCaseProto.TestCase.newBuilder()
                         .setTestClass(testClass)
                         .setTestPackage(TEST_PACKAGE_NAME)
                         .setTestMethod(testMethod2))
          .setTestStatus(TestStatusProto.TestStatus.FAILED)
          .addOutputArtifact(
            TestArtifactProto.Artifact.newBuilder().setSourcePath(PathProto.Path.newBuilder().setPath(snapshotName2))
          )
      ).build()
    protobuf.writeTo(utpProtoFile.outputStream())
    val utpTestResultAdapter = UtpTestResultAdapter(utpProtoFile)
    utpTestResultAdapter.forwardResults(mockListener)
    val verifyInOrder = inOrder(mockListener)
    verifyInOrder.verify(mockListener).onTestSuiteScheduled(any())
    verifyInOrder.verify(mockListener).onTestSuiteStarted(any(), any())
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      result == AndroidTestCaseResult.FAILED && retentionSnapshot?.canonicalPath == failedSnapshot1.canonicalPath
    })
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      result == AndroidTestCaseResult.FAILED && retentionSnapshot?.canonicalPath == failedSnapshot2.canonicalPath
    })
    verifyInOrder.verify(mockListener).onTestSuiteFinished(any(), argThat {
      result == AndroidTestSuiteResult.FAILED
    })
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
    val utpTestResultAdapter = UtpTestResultAdapter(utpProtoFile)
    utpTestResultAdapter.forwardResults(mockListener)
    val verifyInOrder = inOrder(mockListener)
    verifyInOrder.verify(mockListener).onTestSuiteScheduled(any())
    verifyInOrder.verify(mockListener).onTestSuiteStarted(any(), any())
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      result == AndroidTestCaseResult.FAILED && retentionSnapshot == null
    })
    verifyInOrder.verify(mockListener).onTestSuiteFinished(any(), argThat {
      result == AndroidTestSuiteResult.FAILED
    })
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
    val utpTestResultAdapter = UtpTestResultAdapter(utpProtoFile)
    utpTestResultAdapter.forwardResults(mockListener)
    val verifyInOrder = inOrder(mockListener)
    verifyInOrder.verify(mockListener).onTestSuiteScheduled(any())
    verifyInOrder.verify(mockListener).onTestSuiteStarted(any(), any())
    verifyInOrder.verify(mockListener).onTestCaseStarted(any(), any(), any())
    verifyInOrder.verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      result == AndroidTestCaseResult.SKIPPED && retentionSnapshot == null && packageName == TEST_PACKAGE_NAME
    })
    verifyInOrder.verify(mockListener).onTestSuiteFinished(any(), argThat {
      result == AndroidTestSuiteResult.PASSED
    })
  }

  @Test
  fun importTestResultsWithLogcat() {
    val logcatFileName = "logcat-ExampleInstrumentedTest-useAppContext.txt"
    val logcatFile = temporaryFolder.newFile(logcatFileName)
    logcatFile.writeText("test logs")
    val protobuf = TestSuiteResultProto.TestSuiteResult.newBuilder()
      .addTestResult(
        TestResultProto.TestResult.newBuilder().apply {
          testCaseBuilder.apply {
            testPackage = TEST_PACKAGE_NAME
            testClass = "ExampleInstrumentedTest"
            testMethod = "useAppContext"
          }
        }.addOutputArtifact(TestArtifactProto.Artifact.newBuilder().apply {
            label = LabelProto.Label.newBuilder().apply {
              namespace = "android"
              label = "logcat"
            }.build()
            sourcePath = PathProto.Path.newBuilder().apply {
              path = logcatFile.canonicalPath
            }.build()
          })
      ).build()
    protobuf.writeTo(utpProtoFile.outputStream())

    val utpTestResultAdapter = UtpTestResultAdapter(utpProtoFile)
    utpTestResultAdapter.forwardResults(mockListener)

    verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      logcat == "test logs"
    })
  }

  @Test
  fun importMultipleDevices() {
    val deviceName1 = "device 1"
    val deviceName2 = "device 2"
    val deviceApi1 = 27
    val deviceApi2 = 28
    val deviceInfoProtoFile1 = temporaryFolder.newFile()
    val deviceInfoProtoFile2 = temporaryFolder.newFile()
    val manufacturer1 = "manufacturer 1"
    val manufacturer2 = "manufacturer 2"
    val model1 = "model 1"
    val model2 = "model 2"
    AndroidTestDeviceInfoProto.AndroidTestDeviceInfo.newBuilder().apply {
      apiLevel = deviceApi1.toString()
      name = deviceName1
      manufacturer = manufacturer1
      model = model1
    }.build().writeTo(deviceInfoProtoFile1.outputStream())
    AndroidTestDeviceInfoProto.AndroidTestDeviceInfo.newBuilder().apply {
      apiLevel = deviceApi2.toString()
      name = deviceName2
      avdName = deviceName2
      manufacturer = manufacturer2
      model = model2
    }.build().writeTo(deviceInfoProtoFile2.outputStream())
    val testClass1 = "ExampleInstrumentedTest1"
    val testClass2 = "ExampleInstrumentedTest2"
    val testMethod1 = "useAppContext1"
    val testMethod2 = "useAppContext2"
    TestSuiteResultProto.TestSuiteResult.newBuilder()
      .addTestResult(
        TestResultProto.TestResult.newBuilder()
          .setTestCase(TestCaseProto.TestCase.newBuilder()
                         .setTestClass(testClass1)
                         .setTestPackage(TEST_PACKAGE_NAME)
                         .setTestMethod(testMethod1))
          .setTestStatus(TestStatusProto.TestStatus.PASSED)
          .addOutputArtifact(TestArtifactProto.Artifact.newBuilder().apply {
            label = labelBuilder
              .setLabel("device-info")
              .setNamespace("android")
              .build()
            sourcePath = sourcePathBuilder
              .setPath(deviceInfoProtoFile1.absolutePath)
              .build()
          })
      ).addTestResult(
        TestResultProto.TestResult.newBuilder()
          .setTestCase(TestCaseProto.TestCase.newBuilder()
                         .setTestClass(testClass2)
                         .setTestPackage(TEST_PACKAGE_NAME)
                         .setTestMethod(testMethod2))
          .setTestStatus(TestStatusProto.TestStatus.FAILED)
          .addOutputArtifact(TestArtifactProto.Artifact.newBuilder().apply {
            label = labelBuilder
              .setLabel("device-info")
              .setNamespace("android")
              .build()
            sourcePath = sourcePathBuilder
              .setPath(deviceInfoProtoFile2.absolutePath)
              .build()
          })
      ).build().writeTo(utpProtoFile.outputStream())
    val utpTestResultAdapter = UtpTestResultAdapter(utpProtoFile)
    utpTestResultAdapter.forwardResults(mockListener)
    val deviceMatcher1 = ArgumentMatcher<AndroidDevice> { device ->
      device?.deviceName == deviceName1 && device.deviceType == AndroidDeviceType.LOCAL_PHYSICAL_DEVICE
      && device.additionalInfo["Manufacturer"] == manufacturer1 && device.additionalInfo["Model"] == model1
    }
    val deviceMatcher2 = ArgumentMatcher<AndroidDevice> { device ->
      device?.deviceName == deviceName2 && device.deviceType == AndroidDeviceType.LOCAL_EMULATOR
      && device.additionalInfo["Manufacturer"] == manufacturer2 && device.additionalInfo["Model"] == model2
    }
    val testCaseMatcher1= ArgumentMatcher<AndroidTestCase> { testCase ->
      testCase?.methodName == testMethod1 && testCase.className == testClass1 && testCase.packageName == TEST_PACKAGE_NAME
    }
    val testCaseMatcher2 = ArgumentMatcher<AndroidTestCase> { testCase ->
      testCase?.methodName == testMethod2 && testCase.className == testClass2 && testCase.packageName == TEST_PACKAGE_NAME
    }
    verify(mockListener).onTestSuiteScheduled(argThat(deviceMatcher1))
    verify(mockListener).onTestSuiteScheduled(argThat(deviceMatcher2))
    verify(mockListener).onTestSuiteStarted(argThat(deviceMatcher1), argThat {
      testCaseCount == 1
    })
    verify(mockListener).onTestSuiteStarted(argThat(deviceMatcher2), argThat {
      testCaseCount == 1
    })
    verify(mockListener).onTestCaseStarted(argThat(deviceMatcher1), any(), argThat(testCaseMatcher1))
    verify(mockListener).onTestCaseFinished(argThat(deviceMatcher1), any(), argThat(testCaseMatcher1))
    verify(mockListener).onTestCaseStarted(argThat(deviceMatcher2), any(), argThat(testCaseMatcher2))
    verify(mockListener).onTestCaseFinished(argThat(deviceMatcher2), any(), argThat(testCaseMatcher2))
    verify(mockListener).onTestSuiteFinished(argThat(deviceMatcher1),
                                             argThat { result == AndroidTestSuiteResult.PASSED })
    verify(mockListener).onTestSuiteFinished(argThat(deviceMatcher2),
                                             argThat { result == AndroidTestSuiteResult.FAILED })
  }

  @Test
  fun importManagedDevice() {
    val deviceName = "really_long_avd_name"
    val dslName = "myDevice"
    val deviceApi = 29
    val deviceInfoProtoFile = temporaryFolder.newFile()
    AndroidTestDeviceInfoProto.AndroidTestDeviceInfo.newBuilder().apply {
      apiLevel = deviceApi.toString()
      name = deviceName
      avdName = deviceName
      gradleDslDeviceName = dslName
    }.build().writeTo(deviceInfoProtoFile.outputStream())

    val testClass = "ExampleInstrumentedTest1"
    val testMethod = "useAppContext1"

    TestSuiteResultProto.TestSuiteResult.newBuilder()
      .addTestResult(
        TestResultProto.TestResult.newBuilder()
          .setTestCase(TestCaseProto.TestCase.newBuilder()
                         .setTestClass(testClass)
                         .setTestPackage(TEST_PACKAGE_NAME)
                         .setTestMethod(testMethod))
          .setTestStatus(TestStatusProto.TestStatus.PASSED)
          .addOutputArtifact(TestArtifactProto.Artifact.newBuilder().apply {
            label = labelBuilder
              .setLabel("device-info")
              .setNamespace("android")
              .build()
            sourcePath = sourcePathBuilder
              .setPath(deviceInfoProtoFile.absolutePath)
              .build()
          })
      ).build().writeTo(utpProtoFile.outputStream())

    val utpTestResultAdapter = UtpTestResultAdapter(utpProtoFile)
    utpTestResultAdapter.forwardResults(mockListener)
    val deviceMatcher = ArgumentMatcher<AndroidDevice> { device ->
      device?.deviceName == "Gradle:$dslName" &&
      device.avdName == deviceName &&
      device.deviceType == AndroidDeviceType.LOCAL_GRADLE_MANAGED_EMULATOR
    }
    val testCaseMatcher = ArgumentMatcher<AndroidTestCase> { testCase ->
      testCase?.methodName == testMethod && testCase.className == testClass && testCase.packageName == TEST_PACKAGE_NAME
    }
    verify(mockListener).onTestSuiteScheduled(argThat(deviceMatcher))
    verify(mockListener).onTestSuiteStarted(argThat(deviceMatcher), any())
    verify(mockListener).onTestCaseStarted(argThat(deviceMatcher), any(), argThat(testCaseMatcher))
    verify(mockListener).onTestCaseFinished(argThat(deviceMatcher), any(), argThat(testCaseMatcher))
    verify(mockListener).onTestSuiteFinished(argThat(deviceMatcher),
                                             argThat { result == AndroidTestSuiteResult.PASSED })
  }

  @Test
  fun importBenchmarkTest() {
    val benchmarkMessageFile = temporaryFolder.newFile("benchmarkMessage.txt").apply {
      writeText("benchmarkMessage")
    }
    val benchmarkTraceFile = temporaryFolder.newFile("benchmarkTraceFile.trace").apply {
      writeText("benchmarkTrace")
    }

    TestSuiteResultProto.TestSuiteResult.newBuilder().apply {
      addTestResultBuilder().apply {
        testCaseBuilder.apply {
          testPackage = "com.example.test"
          testClass = "ExampleTest"
          testMethod = "testExample"
        }
        testStatus = TestStatusProto.TestStatus.PASSED
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
      }
    }.build().writeTo(utpProtoFile.outputStream())

    val utpTestResultAdapter = UtpTestResultAdapter(utpProtoFile)
    utpTestResultAdapter.forwardResults(mockListener)

    verify(mockListener).onTestCaseFinished(any(), any(), argThat {
      packageName == "com.example.test" && className == "ExampleTest" &&
      methodName == "testExample" && result == AndroidTestCaseResult.PASSED
      && benchmark == "benchmarkMessage"
    })

    assertThat(File(FileUtil.getTempDirectory() + File.separator + "benchmarkTraceFile.trace").exists()).isTrue()
  }
}
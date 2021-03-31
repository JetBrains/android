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

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argThat
import com.android.testutils.MockitoKt.eq
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.google.common.truth.Truth.assertThat
import com.google.testing.platform.proto.api.core.LabelProto
import com.google.testing.platform.proto.api.core.PathProto
import com.google.testing.platform.proto.api.core.TestArtifactProto
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.intellij.testFramework.ProjectRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.quality.Strictness


@RunWith(JUnit4::class)
class GradleTestResultAdapterTest {

  @get:Rule val projectRule = ProjectRule()
  @get:Rule val mockitoJUnitRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

  @Mock private lateinit var mockDevice1: IDevice
  @Mock private lateinit var mockListener: AndroidTestResultListener

  @Before
  fun setup() {
    `when`(mockDevice1.serialNumber).thenReturn("mockDevice1SerialNumber")
    `when`(mockDevice1.avdName).thenReturn("mockDevice1AvdName")
    `when`(mockDevice1.version).thenReturn(AndroidVersion(29))
    `when`(mockDevice1.isEmulator).thenReturn(true)
  }

  @Test
  fun getDevice() {
    val expectedDevice = AndroidDevice(
      "mockDevice1SerialNumber",
      "mockDevice1AvdName",
      "mockDevice1AvdName",
      AndroidDeviceType.LOCAL_EMULATOR,
      AndroidVersion(29))

    val adapter = GradleTestResultAdapter(mockDevice1, "testName", mockListener)

    assertThat(adapter.device).isEqualTo(expectedDevice)
  }

  @Test
  fun runTestSuiteWithOneTestCaseAndPassed() {
    val adapter = GradleTestResultAdapter(mockDevice1, "testName", mockListener)

    verify(mockListener).onTestSuiteScheduled(eq(adapter.device))

    adapter.onTestSuiteStarted(TestSuiteResultProto.TestSuiteMetaData.newBuilder().apply {
      scheduledTestCaseCount = 1
    }.build())

    verify(mockListener).onTestSuiteStarted(eq(adapter.device), argThat {
      it.id.isNotBlank() && it.name == "testName" && it.testCaseCount == 1 && it.result == null
    })

    adapter.onTestCaseStarted(TestCaseProto.TestCase.newBuilder().apply {
      testPackage = "com.example.test"
      testClass = "ExampleTest"
      testMethod = "testExample"
    }.build())

    verify(mockListener).onTestCaseStarted(eq(adapter.device), any(), argThat {
      it.packageName == "com.example.test" && it.className == "ExampleTest" &&
      it.methodName == "testExample" && it.result == AndroidTestCaseResult.IN_PROGRESS
    })

    adapter.onTestCaseFinished(TestResultProto.TestResult.newBuilder().apply {
      testCaseBuilder.apply {
        testPackage = "com.example.test"
        testClass = "ExampleTest"
        testMethod = "testExample"
      }
      testStatus = TestStatusProto.TestStatus.PASSED
    }.build())

    verify(mockListener).onTestCaseFinished(eq(adapter.device), any(), argThat {
      it.packageName == "com.example.test" && it.className == "ExampleTest" &&
      it.methodName == "testExample" && it.result == AndroidTestCaseResult.PASSED
    })

    adapter.onTestSuiteFinished(TestSuiteResultProto.TestSuiteResult.newBuilder().apply {
      testStatus = TestStatusProto.TestStatus.PASSED
    }.build())

    verify(mockListener).onTestSuiteFinished(eq(adapter.device), argThat {
      it.id.isNotBlank() && it.name == "testName" && it.testCaseCount == 1 && it.result == AndroidTestSuiteResult.PASSED
    })
  }

  @Test
  fun runTestSuiteWithOneTestCaseAndFailed() {
    val adapter = GradleTestResultAdapter(mockDevice1, "testName", mockListener)

    verify(mockListener).onTestSuiteScheduled(eq(adapter.device))

    adapter.onTestSuiteStarted(TestSuiteResultProto.TestSuiteMetaData.newBuilder().apply {
      scheduledTestCaseCount = 1
    }.build())

    verify(mockListener).onTestSuiteStarted(eq(adapter.device), argThat {
      it.id.isNotBlank() && it.name == "testName" && it.testCaseCount == 1 && it.result == null
    })

    adapter.onTestCaseStarted(TestCaseProto.TestCase.newBuilder().apply {
      testPackage = "com.example.test"
      testClass = "ExampleTest"
      testMethod = "testExample"
    }.build())

    verify(mockListener).onTestCaseStarted(eq(adapter.device), any(), argThat {
      it.packageName == "com.example.test" && it.className == "ExampleTest" &&
      it.methodName == "testExample" && it.result == AndroidTestCaseResult.IN_PROGRESS
    })

    adapter.onTestCaseFinished(TestResultProto.TestResult.newBuilder().apply {
      testCaseBuilder.apply {
        testPackage = "com.example.test"
        testClass = "ExampleTest"
        testMethod = "testExample"
      }
      testStatus = TestStatusProto.TestStatus.FAILED
      errorBuilder.apply {
        errorMessage = "ErrorStackTrace"
      }
    }.build())

    verify(mockListener).onTestCaseFinished(eq(adapter.device), any(), argThat {
      it.packageName == "com.example.test" && it.className == "ExampleTest" &&
      it.methodName == "testExample" && it.result == AndroidTestCaseResult.FAILED &&
      it.errorStackTrace == "ErrorStackTrace"
    })

    adapter.onTestSuiteFinished(TestSuiteResultProto.TestSuiteResult.newBuilder().apply {
      testStatus = TestStatusProto.TestStatus.FAILED
    }.build())

    verify(mockListener).onTestSuiteFinished(eq(adapter.device), argThat {
      it.id.isNotBlank() && it.name == "testName" && it.testCaseCount == 1 && it.result == AndroidTestSuiteResult.FAILED
    })
  }


  @Test
  fun runTestSuiteWithRetention() {
    val adapter = GradleTestResultAdapter(mockDevice1, "testName", mockListener)
    val iceboxInfoPath = "icebox-info.pb"
    val iceboxSnapshotPath = "icebox-snapshot.tar"

    verify(mockListener).onTestSuiteScheduled(eq(adapter.device))

    adapter.onTestSuiteStarted(TestSuiteResultProto.TestSuiteMetaData.newBuilder().apply {
      scheduledTestCaseCount = 1
    }.build())

    verify(mockListener).onTestSuiteStarted(eq(adapter.device), argThat {
      it.id.isNotBlank() && it.name == "testName" && it.testCaseCount == 1 && it.result == null
    })

    adapter.onTestCaseStarted(TestCaseProto.TestCase.newBuilder().apply {
      testPackage = "com.example.test"
      testClass = "ExampleTest"
      testMethod = "testExample"
    }.build())

    verify(mockListener).onTestCaseStarted(eq(adapter.device), any(), argThat {
      it.packageName == "com.example.test" && it.className == "ExampleTest" &&
      it.methodName == "testExample" && it.result == AndroidTestCaseResult.IN_PROGRESS
    })

    adapter.onTestCaseFinished(TestResultProto.TestResult.newBuilder().apply {
      testCaseBuilder.apply {
        testPackage = "com.example.test"
        testClass = "ExampleTest"
        testMethod = "testExample"
      }
      testStatus = TestStatusProto.TestStatus.FAILED
      errorBuilder.apply {
        errorMessage = "ErrorStackTrace"
      }
      addOutputArtifact(TestArtifactProto.Artifact.newBuilder().apply {
        label = LabelProto.Label.newBuilder().apply {
          namespace = "android"
          label = "icebox.info"
        }.build()
        sourcePath = PathProto.Path.newBuilder().apply {
          path = iceboxInfoPath
        }.build()
      })
      addOutputArtifact(TestArtifactProto.Artifact.newBuilder().apply {
        label = LabelProto.Label.newBuilder().apply {
          namespace = "android"
          label = "icebox.snapshot"
        }.build()
        sourcePath = PathProto.Path.newBuilder().apply {
          path = iceboxSnapshotPath
        }.build()
      })
    }.build())

    verify(mockListener).onTestCaseFinished(eq(adapter.device), any(), argThat {
      it.packageName == "com.example.test" && it.className == "ExampleTest" &&
      it.methodName == "testExample" && it.result == AndroidTestCaseResult.FAILED &&
      it.errorStackTrace == "ErrorStackTrace" &&
      it.retentionInfo?.path == iceboxInfoPath && it.retentionSnapshot?.path == iceboxSnapshotPath
    })

    adapter.onTestSuiteFinished(TestSuiteResultProto.TestSuiteResult.newBuilder().apply {
      testStatus = TestStatusProto.TestStatus.FAILED
    }.build())

    verify(mockListener).onTestSuiteFinished(eq(adapter.device), argThat {
      it.id.isNotBlank() && it.name == "testName" && it.testCaseCount == 1 && it.result == AndroidTestSuiteResult.FAILED
    })
  }

  @Test
  fun gradleTaskFinishedOrCancelledBeforeTestSuiteFinishes() {
    GradleTestResultAdapter(mockDevice1, "testName", mockListener).apply {
      onTestSuiteStarted(TestSuiteResultProto.TestSuiteMetaData.newBuilder().apply {
        scheduledTestCaseCount = 1
      }.build())

      onTestCaseStarted(TestCaseProto.TestCase.newBuilder().apply {
        testPackage = "com.example.test"
        testClass = "ExampleTest"
        testMethod = "testExample"
      }.build())

      onGradleTaskFinished()
    }

    inOrder(mockListener).apply {
      verify(mockListener).onTestSuiteScheduled(any())
      verify(mockListener).onTestSuiteStarted(any(), any())
      verify(mockListener).onTestCaseStarted(any(), any(), any())
      verify(mockListener).onTestSuiteFinished(any(), argThat {
        it.id.isNotBlank() && it.name == "testName" && it.testCaseCount == 1 && it.result == AndroidTestSuiteResult.CANCELLED
      })
    }
  }
}
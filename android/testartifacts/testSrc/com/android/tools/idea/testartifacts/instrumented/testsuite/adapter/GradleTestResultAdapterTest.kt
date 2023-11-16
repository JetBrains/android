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
import com.android.testutils.MockitoKt.whenever
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
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.TestRun
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ProjectRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.quality.Strictness
import java.io.File

@RunWith(JUnit4::class)
class GradleTestResultAdapterTest {

  @get:Rule val projectRule = ProjectRule()
  @get:Rule val mockitoJUnitRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)
  @get:Rule val tempFolder: TemporaryFolder = TemporaryFolder()

  @Mock private lateinit var mockDevice1: IDevice
  @Mock private lateinit var mockListener: AndroidTestResultListener

  private lateinit var reportedAndroidStudioEvent: AndroidStudioEvent.Builder

  @Before
  fun setup() {
    whenever(mockDevice1.serialNumber).thenReturn("mockDevice1SerialNumber")
    whenever(mockDevice1.avdName).thenReturn("mockDevice1AvdName")
    whenever(mockDevice1.version).thenReturn(AndroidVersion(29))
    whenever(mockDevice1.isEmulator).thenReturn(true)
  }

  private fun createAdapter(): GradleTestResultAdapter {
    return GradleTestResultAdapter(mockDevice1, "testName", null, mockListener) { loggedEvent ->
      reportedAndroidStudioEvent = loggedEvent
    }
  }

  @Test
  fun getDevice() {
    val expectedDevice = AndroidDevice(
      "mockDevice1SerialNumber",
      "mockDevice1AvdName",
      "mockDevice1AvdName",
      AndroidDeviceType.LOCAL_EMULATOR,
      AndroidVersion(29),
      mutableMapOf ("SerialNumber" to "mockDevice1SerialNumber"))

    val adapter = createAdapter()

    assertThat(adapter.device).isEqualTo(expectedDevice)
  }

  @Test
  fun runTestSuiteWithOneTestCaseAndPassed() {
    val adapter = createAdapter()

    verify(mockListener).onTestSuiteScheduled(eq(adapter.device))

    assertThat(adapter.testSuiteStarted).isFalse()

    adapter.onTestSuiteStarted(TestSuiteResultProto.TestSuiteMetaData.newBuilder().apply {
      scheduledTestCaseCount = 1
    }.build())

    assertThat(adapter.testSuiteStarted).isTrue()

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

    adapter.onGradleTaskFinished()

    verify(mockListener).onTestSuiteFinished(eq(adapter.device), argThat {
      it.id.isNotBlank() && it.name == "testName" && it.testCaseCount == 1 && it.result == AndroidTestSuiteResult.PASSED
    })
  }

  @Test
  fun runTestSuiteWithOneTestCaseAndFailed() {
    val adapter = createAdapter()

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

    adapter.onGradleTaskFinished()

    verify(mockListener).onTestSuiteFinished(eq(adapter.device), argThat {
      it.id.isNotBlank() && it.name == "testName" && it.testCaseCount == 1 && it.result == AndroidTestSuiteResult.FAILED
    })
  }

  @Test
  fun runTestSuiteWithRetention() {
    val adapter = createAdapter()
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

    adapter.onGradleTaskFinished()

    verify(mockListener).onTestSuiteFinished(eq(adapter.device), argThat {
      it.id.isNotBlank() && it.name == "testName" && it.testCaseCount == 1 && it.result == AndroidTestSuiteResult.FAILED
    })
  }

  @Test
  fun runTestSuiteWithLogcat() {
    val logcatFile = tempFolder.newFile("logcat-com.example.test.ExampleTest.testExample.txt")
    val logcatPath = logcatFile.absolutePath
    logcatFile.writeText("test logs")
    val adapter = createAdapter().apply {
      onTestSuiteStarted(TestSuiteResultProto.TestSuiteMetaData.newBuilder().apply {
        scheduledTestCaseCount = 1
      }.build())
      onTestCaseStarted(TestCaseProto.TestCase.newBuilder().apply {
        testPackage = "com.example.test"
        testClass = "ExampleTest"
        testMethod = "testExample"
      }.build())
      onTestCaseFinished(TestResultProto.TestResult.newBuilder().apply {
        testCaseBuilder.apply {
          testPackage = "com.example.test"
          testClass = "ExampleTest"
          testMethod = "testExample"
        }
        testStatus = TestStatusProto.TestStatus.PASSED
        addOutputArtifact(TestArtifactProto.Artifact.newBuilder().apply {
          label = LabelProto.Label.newBuilder().apply {
            namespace = "android"
            label = "logcat"
          }.build()
          sourcePath = PathProto.Path.newBuilder().apply {
            path = logcatPath
          }.build()
        })
      }.build())
    }

    verify(mockListener).onTestCaseFinished(eq(adapter.device), any(), argThat {
      it.packageName == "com.example.test" && it.className == "ExampleTest" &&
      it.methodName == "testExample" && it.result == AndroidTestCaseResult.PASSED
      && it.logcat == "test logs"
    })
  }

  @Test
  fun runTestSuiteWithLogcatFileDoesNotExist() {
    val logcatPath = "path-to-logcat-com.example.test.ExampleTest.testExample.txt"
    val adapter = createAdapter().apply {
      onTestSuiteStarted(TestSuiteResultProto.TestSuiteMetaData.newBuilder().apply {
        scheduledTestCaseCount = 1
      }.build())
      onTestCaseStarted(TestCaseProto.TestCase.newBuilder().apply {
        testPackage = "com.example.test"
        testClass = "ExampleTest"
        testMethod = "testExample"
      }.build())
      onTestCaseFinished(TestResultProto.TestResult.newBuilder().apply {
        testCaseBuilder.apply {
          testPackage = "com.example.test"
          testClass = "ExampleTest"
          testMethod = "testExample"
        }
        testStatus = TestStatusProto.TestStatus.PASSED
        addOutputArtifact(TestArtifactProto.Artifact.newBuilder().apply {
          label = LabelProto.Label.newBuilder().apply {
            namespace = "android"
            label = "logcat"
          }.build()
          sourcePath = PathProto.Path.newBuilder().apply {
            path = logcatPath
          }.build()
        })
      }.build())
    }

    verify(mockListener).onTestCaseFinished(eq(adapter.device), any(), argThat {
      it.packageName == "com.example.test" && it.className == "ExampleTest" &&
      it.methodName == "testExample" && it.result == AndroidTestCaseResult.PASSED
      && it.logcat == ""
    })
  }

  @Test
  fun runTestSuiteWithBenchmark() {
    val benchmarkMessageFile = tempFolder.newFile("benchmarkMessage.txt").apply {
      writeText("benchmarkMessage")
    }
    val benchmarkTraceFile = tempFolder.newFile("benchmarkTraceFile.trace").apply {
      writeText("benchmarkTrace")
    }
    val adapter = createAdapter().apply {
      onTestSuiteStarted(TestSuiteResultProto.TestSuiteMetaData.newBuilder().apply {
        scheduledTestCaseCount = 1
      }.build())
      onTestCaseStarted(TestCaseProto.TestCase.newBuilder().apply {
        testPackage = "com.example.test"
        testClass = "ExampleTest"
        testMethod = "testExample"
      }.build())
      onTestCaseFinished(TestResultProto.TestResult.newBuilder().apply {
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
      }.build())
    }

    verify(mockListener).onTestCaseFinished(eq(adapter.device), any(), argThat {
      it.packageName == "com.example.test" && it.className == "ExampleTest" &&
      it.methodName == "testExample" && it.result == AndroidTestCaseResult.PASSED
      && it.benchmark == "benchmarkMessage"
    })

    assertThat(File(FileUtil.getTempDirectory() + File.separator + "benchmarkTraceFile.trace").exists()).isTrue()
  }

  @Test
  fun gradleTaskFinishedOrCancelledBeforeTestSuiteFinishes() {
    createAdapter().apply {
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
      verify(mockListener).onTestCaseFinished(any(), any(), argThat {
        it.packageName == "com.example.test" &&
        it.className == "ExampleTest" &&
        it.methodName == "testExample" &&
        it.result == AndroidTestCaseResult.CANCELLED &&
        it.startTimestampMillis != null &&
        it.endTimestampMillis != null
      })
      verify(mockListener).onTestSuiteFinished(any(), argThat {
        it.id.isNotBlank() && it.name == "testName" && it.testCaseCount == 1 && it.result == AndroidTestSuiteResult.CANCELLED
      })
    }
  }

  @Test
  fun gradleTaskFinishedOrCancelledBeforeTestSuiteStarts() {
    createAdapter().apply {
      onGradleTaskFinished()
    }

    inOrder(mockListener).apply {
      verify(mockListener).onTestSuiteScheduled(any())
      verify(mockListener).onTestSuiteStarted(any(), any())
      verify(mockListener).onTestSuiteFinished(any(), argThat {
        it.id.isNotBlank() && it.name == "testName" && it.testCaseCount == 0 && it.result == AndroidTestSuiteResult.CANCELLED
      })
    }
  }

  @Test
  fun onTestSuiteFinishedIsCalledBeforeTestSuiteEvenStarts() {
    val adapter = createAdapter().apply {
      onTestSuiteFinished(TestSuiteResultProto.TestSuiteResult.getDefaultInstance())
      onGradleTaskFinished()
    }

    inOrder(mockListener).apply {
      verify(mockListener).onTestSuiteScheduled(any())
      verify(mockListener).onTestSuiteStarted(any(), any())
      verify(mockListener).onTestSuiteFinished(any(), any())
    }

    assertThat(adapter.needRerunWithUninstallIncompatibleApkOption().needRerunWithUninstallIncompatibleApkOption).isFalse()
  }

  private fun runAndUtpFailsWithApkInstallationError(): GradleTestResultAdapter {
    return createAdapter().apply {
      onTestSuiteFinished(TestSuiteResultProto.TestSuiteResult.newBuilder().apply {
        platformErrorBuilder.apply {
          addErrorsBuilder().apply {
            causeBuilder.apply {
              summaryBuilder.apply {
                namespaceBuilder.apply {
                  namespace = "DdmlibAndroidDeviceController"
                }
                errorCode = 1
                errorName = "INSTALL_FAILED_UPDATE_INCOMPATIBLE"
              }
            }
          }
        }
      }.build())
    }
  }

  @Test
  fun onTestSuiteFinishedIsCalledBeforeTestSuiteEvenStartsDueToApkInstallationError() {
    val adapter = runAndUtpFailsWithApkInstallationError()

    assertThat(adapter.needRerunWithUninstallIncompatibleApkOption().needRerunWithUninstallIncompatibleApkOption).isTrue()
  }

  @Test
  fun showRerunWithUninstallIncompatibleApkOptionDialogAndAccept() {
    lateinit var capturedMessage: String
    val adapter = runAndUtpFailsWithApkInstallationError()

    val result = adapter.showRerunWithUninstallIncompatibleApkOptionDialog(projectRule.project) { message ->
      capturedMessage = message
      true
    }

    assertThat(result).isTrue()
    assertThat(capturedMessage).contains("The device already has an application with the same package but a different signature.")
    assertThat(capturedMessage).contains("WARNING: Uninstalling will remove the application data!")
    assertThat(capturedMessage).contains("Do you want to uninstall the existing application?")
  }

  @Test
  fun showRerunWithUninstallIncompatibleApkOptionDialogAndDecline() {
    val adapter = runAndUtpFailsWithApkInstallationError()

    val result = adapter.showRerunWithUninstallIncompatibleApkOptionDialog(projectRule.project) { false }

    assertThat(result).isFalse()
  }

  @Test
  fun testRunEventLogging() {
    createAdapter().apply {
      onTestSuiteStarted(TestSuiteResultProto.TestSuiteMetaData.newBuilder().apply {
        scheduledTestCaseCount = 1
      }.build())
      onTestCaseStarted(TestCaseProto.TestCase.newBuilder().apply {
        testPackage = "com.example.test"
        testClass = "ExampleTest"
        testMethod = "testExample"
      }.build())
      onTestCaseFinished(TestResultProto.TestResult.newBuilder().apply {
        testCaseBuilder.apply {
          testPackage = "com.example.test"
          testClass = "ExampleTest"
          testMethod = "testExample"
        }
        testStatus = TestStatusProto.TestStatus.PASSED
      }.build())
      onTestSuiteFinished(TestSuiteResultProto.TestSuiteResult.newBuilder().apply {
        testStatus = TestStatusProto.TestStatus.PASSED
      }.build())
    }

    assertThat(reportedAndroidStudioEvent.kind).isEqualTo(AndroidStudioEvent.EventKind.TEST_RUN)
    assertThat(reportedAndroidStudioEvent.category).isEqualTo(AndroidStudioEvent.EventCategory.TESTS)
    assertThat(reportedAndroidStudioEvent.hasDeviceInfo()).isTrue()
    assertThat(reportedAndroidStudioEvent.testRun.testInvocationType).isEqualTo(TestRun.TestInvocationType.ANDROID_STUDIO_THROUGH_GRADLE_TEST)
    assertThat(reportedAndroidStudioEvent.testRun.numberOfTestsExecuted).isEqualTo(1)
    assertThat(reportedAndroidStudioEvent.testRun.testKind).isEqualTo(TestRun.TestKind.INSTRUMENTATION_TEST)
    assertThat(reportedAndroidStudioEvent.testRun.testExecution).isEqualTo(TestRun.TestExecution.HOST)
  }
}
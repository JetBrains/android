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

import com.android.annotations.concurrency.UiThread
import com.android.ddmlib.IDevice
import com.android.ddmlib.testrunner.TestIdentifier
import com.android.tools.analytics.UsageTracker
import com.android.tools.deployer.AdbClient.InstallResult
import com.android.tools.deployer.ApkInstaller
import com.android.tools.deployer.InstallStatus
import com.android.tools.idea.gradle.model.IdeAndroidArtifact
import com.android.tools.idea.gradle.model.IdeTestOptions
import com.android.tools.idea.stats.AndroidStudioUsageTracker
import com.android.tools.idea.stats.findTestLibrariesVersions
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.android.tools.utp.TaskOutputProcessorListener
import com.android.tools.idea.protobuf.Timestamp
import com.google.testing.platform.proto.api.core.TestCaseProto
import com.google.testing.platform.proto.api.core.TestResultProto
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.TestRun
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.util.ClassUtil
import java.io.File
import java.util.UUID

/**
 * An adapter to parse instrumentation test result protobuf messages from AGP and forward them to AndroidTestResultListener
 */
class GradleTestResultAdapter(
  val iDevice: IDevice,
  private val testSuiteDisplayName: String,
  private val artifact: IdeAndroidArtifact?,
  private val listener: AndroidTestResultListener,
  private val logStudioEvent: (AndroidStudioEvent.Builder) -> Unit = UsageTracker::log
): TaskOutputProcessorListener {

  val device: AndroidDevice = convertIDeviceToAndroidDevice(iDevice)

  init {
    // Schedule test suite for selected devices when instrumentation tests are executed by AGP.
    listener.onTestSuiteScheduled(device)
  }

  private lateinit var myTestSuite: AndroidTestSuite
  private lateinit var myUtpTestSuiteResult: TestSuiteResultProto.TestSuiteResult

  /**
   * Returns true when the test suite started otherwise false.
   */
  val testSuiteStarted: Boolean
    get() = this::myTestSuite.isInitialized

  private val myTestCases = mutableMapOf<TestIdentifier, AndroidTestCase>()

  // This map keeps track of number of rerun of the same test method.
  // This value is used to create a unique identifier for each test case
  // yet to be able to group them together across multiple devices.
  private val myTestCaseRunCount: MutableMap<String, Int> = mutableMapOf()

  // A studio event builder to be reported for logging testing feature usage.
  // This event will be reported when a test suite finishes.
  private val myStudioEventBuilder = AndroidStudioEvent.newBuilder().apply {
    category = AndroidStudioEvent.EventCategory.TESTS
    kind = AndroidStudioEvent.EventKind.TEST_RUN
    deviceInfo = AndroidStudioUsageTracker.deviceToDeviceInfo(iDevice)
    productDetails = AndroidStudioUsageTracker.productDetails
  }

  // A test run event builder to be used for reporting testing feature usages
  // as part of a Studio event log.
  private val myTestRunEventBuilder: TestRun.Builder = myStudioEventBuilder.testRunBuilder.apply {
    testInvocationType = TestRun.TestInvocationType.ANDROID_STUDIO_THROUGH_GRADLE_TEST
    testKind = TestRun.TestKind.INSTRUMENTATION_TEST
    testExecution = artifact?.testOptions?.execution.toProtoValue()

    artifact?.let(::findTestLibrariesVersions)?.let { testLibraries = it }
  }

  override fun onTestSuiteStarted(testSuite: TestSuiteResultProto.TestSuiteMetaData) {
    myTestRunEventBuilder.numberOfTestsExecuted = testSuite.scheduledTestCaseCount

    myTestSuite = AndroidTestSuite(
      id = UUID.randomUUID().toString(),
      testSuiteDisplayName,
      testSuite.scheduledTestCaseCount)
    listener.onTestSuiteStarted(device, myTestSuite)
  }

  override fun onTestCaseStarted(testCaseProto: TestCaseProto.TestCase) {
    val testId = testCaseProto.toTestIdentifier()
    val fullyQualifiedTestMethodName = "${testId.className}#${testId.testName}"
    val testCaseRunCount = myTestCaseRunCount.compute(fullyQualifiedTestMethodName) { _, currentValue ->
      currentValue?.plus(1) ?: 0
    }
    val testCase = AndroidTestCase("${testId} - ${testCaseRunCount}",
                                   testId.testName,
                                   ClassUtil.extractClassName(testId.className),
                                   ClassUtil.extractPackageName(testId.className),
                                   AndroidTestCaseResult.IN_PROGRESS,
                                   startTimestampMillis = System.currentTimeMillis())
    myTestCases[testId] = testCase
    listener.onTestCaseStarted(device, myTestSuite, testCase)
  }

  override fun onTestCaseFinished(testCaseResult: TestResultProto.TestResult) {
    val testId = testCaseResult.testCase.toTestIdentifier()
    val testCase = myTestCases.getValue(testId)
    testCase.result = testCaseResult.testStatus.toAndroidTestCaseResult()

    if (testCaseResult.testCase.startTime.getTimestamp() != 0L && testCaseResult.testCase.endTime.getTimestamp() != 0L) {
      testCase.startTimestampMillis = testCaseResult.testCase.startTime.getTimestamp()
      testCase.endTimestampMillis = testCaseResult.testCase.endTime.getTimestamp()
    } else {
      testCase.endTimestampMillis = System.currentTimeMillis()
    }

    if (testCaseResult.error.errorMessage.isNotBlank()) {
      testCase.errorStackTrace = testCaseResult.error.errorMessage
    }

    testCaseResult.outputArtifactList.asSequence().filter { it.label.namespace == "android" }.forEach {
      when (it.label.label) {
        "icebox.info" -> testCase.retentionInfo = File(it.sourcePath.path)
        "icebox.snapshot" -> testCase.retentionSnapshot = File(it.sourcePath.path)
        "logcat" -> {
          val logcatFile = File(it.sourcePath.path)
          testCase.logcat = if (logcatFile.exists()) logcatFile.readText() else ""
        }
      }
    }
    setBenchmarkContextAndPrepareFiles(testCaseResult, testCase)

    if (testCase.result == AndroidTestCaseResult.FAILED) {
      myTestSuite.result = AndroidTestSuiteResult.FAILED
    }

    listener.onTestCaseFinished(device, myTestSuite, testCase)
  }

  private fun Timestamp.getTimestamp(): Long {
    return seconds * 1000L + nanos / 1000000L
  }

  override fun onTestSuiteFinished(testSuiteResult: TestSuiteResultProto.TestSuiteResult) {
    myUtpTestSuiteResult = testSuiteResult

    // TODO: UTP currently does not report whether if a test process is crashed or not.
    //       Once UTP supports it, check the status and set "myTestRunEventBuilder.crashed = true"
    //       before calling logStudioEvent.
    logStudioEvent(myStudioEventBuilder)

    if (!this::myTestSuite.isInitialized) {
      // Initialize test suite if it hasn't initialized yet.
      // This may happen if UTP fails in setup phase before it starts test task.
      onTestSuiteStarted(TestSuiteResultProto.TestSuiteMetaData.getDefaultInstance())
    }
    myTestSuite.result = testSuiteResult.testStatus.toAndroidTestSuiteResult()
  }

  /**
   * Called when Gradle test task has finished or cancelled.
   */
  fun onGradleTaskFinished() {
    if (!this::myTestSuite.isInitialized) {
      // Initialize test suite if it hasn't initialized yet.
      // This may happen if a build fails before it starts test task.
      onTestSuiteStarted(TestSuiteResultProto.TestSuiteMetaData.getDefaultInstance())
    }

    // There can be pending test cases remained even after the Gradle task has finished,
    // because a user may cancel the task execution or an exception is thrown from the
    // task execution. We update test results to cancelled state for those pending tests.
    for (testCase in myTestCases.values) {
      if (!testCase.result.isTerminalState) {
        testCase.result = AndroidTestCaseResult.CANCELLED
        testCase.endTimestampMillis = System.currentTimeMillis()
        listener.onTestCaseFinished(device, myTestSuite, testCase)
      }
    }

    myTestSuite.result = myTestSuite.result ?: AndroidTestSuiteResult.CANCELLED
    listener.onTestSuiteFinished(device, myTestSuite)
  }

  data class UtpInstallResult(
    val needRerunWithUninstallIncompatibleApkOption: Boolean = false,
    val lastInstallResult: InstallResult? = null,
  )

  /**
   * Returns true if the test run failed due to APK installation failure that
   * can be fixed by adding UNINSTALL_INCOMPATIBLE_APKS Gradle option.
   */
  fun needRerunWithUninstallIncompatibleApkOption(): UtpInstallResult {
    if (!this::myUtpTestSuiteResult.isInitialized) {
      return UtpInstallResult()
    }

    for (errorDetail in myUtpTestSuiteResult.platformError.errorsList) {
      errorDetail.cause.summary.let { summary ->
        if (summary.namespace.namespace == "DdmlibAndroidDeviceController" && summary.errorCode == 1) {
          val installResult = ApkInstaller.toInstallerResult(summary.errorName, summary.stackTrace)
          // This list is copied from the com.android.tools.deployer.ApkInstaller. These errors are
          // known error names that are caused by an incompatible APK installation attempt.
          return when (installResult.status) {
            InstallStatus.INSTALL_FAILED_UPDATE_INCOMPATIBLE,
            InstallStatus.INCONSISTENT_CERTIFICATES,
            InstallStatus.INSTALL_FAILED_PERMISSION_MODEL_DOWNGRADE,
            InstallStatus.INSTALL_FAILED_VERSION_DOWNGRADE,
            InstallStatus.INSTALL_FAILED_DEXOPT -> UtpInstallResult(true, installResult)
            else -> UtpInstallResult(false, installResult)
          }
        }
      }
    }

    return UtpInstallResult()
  }

  /**
   * Shows a dialog asking a user to uninstall incompatible APKs from devices.
   * Returns true if a user accepts uninstalling otherwise false.
   */
  @UiThread
  fun showRerunWithUninstallIncompatibleApkOptionDialog(
    project: Project,
    showOkCancelDialogFunc: (String) -> Boolean = { message ->
      Messages.showOkCancelDialog(
        project,
        message,
        "Application Installation Failed",
        "OK",
        "Cancel",
        null
      ) == Messages.OK
    }
  ): Boolean {
    val (needRerun, installResult) = needRerunWithUninstallIncompatibleApkOption()
    if (!needRerun || installResult == null) {
      return false
    }

    val displayMessage = """
      ${ApkInstaller.message(installResult)}
      In order to proceed, you will have to uninstall the existing application.
      
      WARNING: Uninstalling will remove the application data!
      
      Do you want to uninstall the existing application?
    """.trimIndent()
    return showOkCancelDialogFunc(displayMessage)
  }
}

private fun TestCaseProto.TestCase.toTestIdentifier(): TestIdentifier {
  return TestIdentifier("${testPackage}.${testClass}", testMethod)
}

private fun TestStatusProto.TestStatus.toAndroidTestCaseResult(): AndroidTestCaseResult {
  return when(this) {
    TestStatusProto.TestStatus.PASSED -> AndroidTestCaseResult.PASSED
    TestStatusProto.TestStatus.FAILED -> AndroidTestCaseResult.FAILED
    TestStatusProto.TestStatus.IGNORED -> AndroidTestCaseResult.SKIPPED
    TestStatusProto.TestStatus.ERROR -> AndroidTestCaseResult.FAILED
    TestStatusProto.TestStatus.ABORTED -> AndroidTestCaseResult.FAILED
    TestStatusProto.TestStatus.CANCELLED -> AndroidTestCaseResult.CANCELLED
    else -> AndroidTestCaseResult.IN_PROGRESS
  }
}

private fun TestStatusProto.TestStatus.toAndroidTestSuiteResult(): AndroidTestSuiteResult {
  return when(this) {
    TestStatusProto.TestStatus.PASSED -> AndroidTestSuiteResult.PASSED
    TestStatusProto.TestStatus.FAILED -> AndroidTestSuiteResult.FAILED
    TestStatusProto.TestStatus.IGNORED -> AndroidTestSuiteResult.PASSED
    TestStatusProto.TestStatus.ERROR -> AndroidTestSuiteResult.FAILED
    TestStatusProto.TestStatus.ABORTED -> AndroidTestSuiteResult.ABORTED
    TestStatusProto.TestStatus.CANCELLED -> AndroidTestSuiteResult.CANCELLED
    else -> AndroidTestSuiteResult.ABORTED
  }
}

private fun IdeTestOptions.Execution?.toProtoValue(): TestRun.TestExecution {
  return when (this) {
    null -> TestRun.TestExecution.HOST
    IdeTestOptions.Execution.HOST -> TestRun.TestExecution.HOST
    IdeTestOptions.Execution.ANDROID_TEST_ORCHESTRATOR -> TestRun.TestExecution.ANDROID_TEST_ORCHESTRATOR
    IdeTestOptions.Execution.ANDROIDX_TEST_ORCHESTRATOR -> TestRun.TestExecution.ANDROID_TEST_ORCHESTRATOR
  }
}

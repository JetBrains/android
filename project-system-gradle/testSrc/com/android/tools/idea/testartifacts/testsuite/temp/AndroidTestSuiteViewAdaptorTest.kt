/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.testsuite.temp

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCaseResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuiteResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.testFramework.RunsInEdt
import java.util.Base64
import org.jetbrains.plugins.gradle.execution.test.runner.events.TestEventXPPXmlView
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@RunsInEdt
class AndroidTestSuiteViewAdaptorTest {

  @get:Rule val projectRule = AndroidProjectRule.withAndroidModel().onEdt()

  @Test
  fun resultsReported_forJourneyTestsSingleDevice() {
    val runConfiguration = mock<RunConfiguration>()
    val adaptor = AndroidTestSuiteViewAdaptor(runConfiguration)
    val testSuiteView = mock<AndroidTestSuiteView>()
    val device =
      AndroidDevice(
        id = "emulator-5554",
        deviceName = "emulator-5554",
        avdName = "",
        deviceType = AndroidDeviceType.LOCAL_EMULATOR,
        version = AndroidVersion.DEFAULT,
      )
    val deviceIdStdout = "[additionalTestArtifacts]deviceId=emulator-5554"
    val encodedDeviceIdStdout =
      Base64.getEncoder().encodeToString(deviceIdStdout.encodeToByteArray())

    // Start internal Gradle test suites
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = ":app:validateDebugJourneysTest",
        parentId = "",
        name = "Gradle Test Run :app:validateDebugJourneysTest",
        displayName = "Gradle Test Run :app:validateDebugJourneysTest",
        className = "",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = "1.1",
        parentId = ":app:validateDebugJourneysTest",
        name = "Gradle Test Executor 1",
        displayName = "Gradle Test Executor 1",
        className = "",
      ),
      testSuiteView,
    )

    // Start first Journey
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = "1.2",
        parentId = "1.1",
        name = "my_first_journey",
        displayName = "My Journey (emulator-5554)",
        className = "my_first_journey",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createOnOutputXml(
        id = "1.2",
        parentId = "1.1",
        name = "my_first_journey",
        displayName = "My Journey (emulator-5554)",
        className = "my_first_journey",
        content = encodedDeviceIdStdout,
      ),
      testSuiteView,
    )

    val testSuite =
      AndroidTestSuite(
        id = "1.2",
        name = "my_first_journey",
        testCaseCount = 0,
        runConfiguration = runConfiguration,
      )
    verify(testSuiteView, times(1)).onTestSuiteScheduled(device)
    verify(testSuiteView, times(1)).onTestSuiteStarted(device, testSuite)

    // Start first Journey action
    adaptor.processEvent(
      createBeforeTestXml(
        id = "1.3",
        parentId = "1.2",
        name = "Open the navigation drawer",
        displayName = "Open the navigation drawer",
        className = "my_first_journey",
      ),
      testSuiteView,
    )

    val firstTestCase =
      AndroidTestCase(
        id = "1.3",
        methodName = "Open the navigation drawer",
        className = "my_first_journey",
        packageName = "my_first_journey",
        result = AndroidTestCaseResult.IN_PROGRESS,
      )
    verify(testSuiteView, times(1))
      .onTestCaseStarted(device, testSuite.copy(testCaseCount = 1), firstTestCase)

    // End first Journey action
    adaptor.processEvent(
      createAfterTestXml(
        id = "1.3",
        parentId = "1.2",
        name = "Open the navigation drawer",
        displayName = "Open the navigation drawer",
        className = "my_first_journey",
        resultType = "SUCCESS",
        startTime = "1748882198880",
        endTime = "1748882204981",
      ),
      testSuiteView,
    )

    verify(testSuiteView, times(1))
      .onTestCaseFinished(
        device,
        testSuite.copy(testCaseCount = 1),
        firstTestCase.copy(
          result = AndroidTestCaseResult.PASSED,
          startTimestampMillis = 1748882198880,
          endTimestampMillis = 1748882204981,
        ),
      )

    // Start second Journey action
    adaptor.processEvent(
      createBeforeTestXml(
        id = "1.4",
        parentId = "1.2",
        name = "Click on the first entry",
        displayName = "Click on the first entry",
        className = "my_first_journey",
      ),
      testSuiteView,
    )

    val secondTestCase =
      AndroidTestCase(
        id = "1.4",
        methodName = "Click on the first entry",
        className = "my_first_journey",
        packageName = "my_first_journey",
        result = AndroidTestCaseResult.IN_PROGRESS,
      )
    verify(testSuiteView, times(1))
      .onTestCaseStarted(device, testSuite.copy(testCaseCount = 2), secondTestCase)

    // End second Journey action
    adaptor.processEvent(
      createAfterTestXml(
        id = "1.4",
        parentId = "1.2",
        name = "Click on the first entry",
        displayName = "Click on the first entry",
        className = "my_first_journey",
        resultType = "SUCCESS",
        startTime = "1748882199999",
        endTime = "1748882201111",
      ),
      testSuiteView,
    )

    verify(testSuiteView, times(1))
      .onTestCaseFinished(
        device,
        testSuite.copy(testCaseCount = 2),
        secondTestCase.copy(
          result = AndroidTestCaseResult.PASSED,
          startTimestampMillis = 1748882199999,
          endTimestampMillis = 1748882201111,
        ),
      )

    // End first Journey
    adaptor.processEvent(
      createAfterSuiteXml(
        id = "1.2",
        parentId = "1.1",
        name = "my_first_journey",
        displayName = "My Journey (emulator-5554)",
        className = "my_first_journey",
        resultType = "SUCCESS",
        startTime = "1748882181802",
        endTime = "1748882207719",
      ),
      testSuiteView,
    )

    verify(testSuiteView, times(1))
      .onTestSuiteFinished(
        device,
        testSuite.copy(testCaseCount = 2, result = AndroidTestSuiteResult.PASSED),
      )

    // Start second Journey
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = "1.5",
        parentId = "1.1",
        name = "my_second_journey",
        displayName = "My Second Journey (emulator-5554)",
        className = "my_second_journey",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createOnOutputXml(
        id = "1.5",
        parentId = "1.1",
        name = "my_second_journey",
        displayName = "My Second Journey (emulator-5554)",
        className = "my_second_journey",
        content = encodedDeviceIdStdout,
      ),
      testSuiteView,
    )

    val secondTestSuite =
      AndroidTestSuite(
        id = "1.5",
        name = "my_second_journey",
        testCaseCount = 0,
        runConfiguration = runConfiguration,
      )
    verify(testSuiteView, times(2)).onTestSuiteScheduled(device)
    verify(testSuiteView, times(1)).onTestSuiteStarted(device, secondTestSuite)

    // Start second Journey action
    adaptor.processEvent(
      createBeforeTestXml(
        id = "1.6",
        parentId = "1.5",
        name = "Open the navigation drawer",
        displayName = "Open the navigation drawer",
        className = "my_second_journey",
      ),
      testSuiteView,
    )
    val firstTestCase2 =
      AndroidTestCase(
        id = "1.6",
        methodName = "Open the navigation drawer",
        className = "my_second_journey",
        packageName = "my_second_journey",
        result = AndroidTestCaseResult.IN_PROGRESS,
      )

    verify(testSuiteView, times(1))
      .onTestCaseStarted(device, secondTestSuite.copy(testCaseCount = 1), firstTestCase2)

    // End second Journey action
    adaptor.processEvent(
      createAfterTestXml(
        id = "1.6",
        parentId = "1.5",
        name = "Open the navigation drawer",
        displayName = "Open the navigation drawer",
        className = "my_second_journey",
        resultType = "SUCCESS",
        startTime = "1748882198880",
        endTime = "1748882204981",
      ),
      testSuiteView,
    )

    verify(testSuiteView, times(1))
      .onTestCaseFinished(
        device,
        secondTestSuite.copy(testCaseCount = 1),
        firstTestCase2.copy(
          result = AndroidTestCaseResult.PASSED,
          startTimestampMillis = 1748882198880,
          endTimestampMillis = 1748882204981,
        ),
      )

    // End second Journey
    adaptor.processEvent(
      createAfterSuiteXml(
        id = "1.5",
        parentId = "1.1",
        name = "my_second_journey",
        displayName = "My Second Journey (emulator-5554)",
        className = "my_second_journey",
        resultType = "SUCCESS",
        startTime = "1748882181802",
        endTime = "1748882207719",
      ),
      testSuiteView,
    )

    verify(testSuiteView, times(1))
      .onTestSuiteFinished(
        device,
        secondTestSuite.copy(testCaseCount = 1, result = AndroidTestSuiteResult.PASSED),
      )

    // End internal Gradle test suites
    adaptor.processEvent(
      createAfterSuiteXml(
        id = "1.1",
        parentId = ":app:validateDebugJourneysTest",
        name = "Gradle Test Executor 1",
        displayName = "Gradle Test Executor 1",
        className = "",
        resultType = "SUCCESS",
        startTime = "1748882181695",
        endTime = "1748882207721",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createAfterSuiteXml(
        id = ":app:validateDebugJourneysTest",
        parentId = "",
        name = "Gradle Test Run :app:validateDebugJourneysTest",
        displayName = "Gradle Test Run :app:validateDebugJourneysTest",
        className = "",
        resultType = "SUCCESS",
        startTime = "1748882181334",
        endTime = "1748882208103",
      ),
      testSuiteView,
    )

    // Verify that the suite callbacks weren't triggered for the internal Gradle test suites - only
    // the two root Journey test suites should have been reported
    verify(testSuiteView, times(2)).onTestSuiteStarted(any(), any())
    verify(testSuiteView, times(2)).onTestSuiteFinished(any(), any())
  }

  @Test
  fun resultsReported_forJourneyTestThatFails() {
    val runConfiguration = mock<RunConfiguration>()
    val adaptor = AndroidTestSuiteViewAdaptor(runConfiguration)
    val testSuiteView = mock<AndroidTestSuiteView>()
    val device =
      AndroidDevice(
        "emulator-5554",
        "emulator-5554",
        "",
        AndroidDeviceType.LOCAL_EMULATOR,
        AndroidVersion.DEFAULT,
      )
    val deviceIdStdout = "[additionalTestArtifacts]deviceId=emulator-5554"
    val encodedDeviceIdStdout =
      Base64.getEncoder().encodeToString(deviceIdStdout.encodeToByteArray())

    // Start internal Gradle test suites
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = ":app:validateDebugJourneysTest",
        parentId = "",
        name = "Gradle Test Run :app:validateDebugJourneysTest",
        displayName = "Gradle Test Run :app:validateDebugJourneysTest",
        className = "",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = "1.1",
        parentId = ":app:validateDebugJourneysTest",
        name = "Gradle Test Executor 1",
        displayName = "Gradle Test Executor 1",
        className = "",
      ),
      testSuiteView,
    )

    // Start Journey
    adaptor.processEvent(
      createBeforeSuiteXml(
        "1.2",
        "1.1",
        "failing_journey",
        "Failing Journey (emulator-5554)",
        "failing_journey",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createOnOutputXml(
        "1.2",
        "1.1",
        "failing_journey",
        "Failing Journey (emulator-5554)",
        "failing_journey",
        encodedDeviceIdStdout,
      ),
      testSuiteView,
    )

    val testSuite = AndroidTestSuite("1.2", "failing_journey", 0, null, runConfiguration)
    verify(testSuiteView).onTestSuiteScheduled(device)
    verify(testSuiteView).onTestSuiteStarted(device, testSuite)

    // Start Journey action
    adaptor.processEvent(
      createBeforeTestXml("1.3", "1.2", "step_one", "Step One", "failing_journey"),
      testSuiteView,
    )

    val testCase =
      AndroidTestCase("1.3", "step_one", "failing_journey", "failing_journey", AndroidTestCaseResult.IN_PROGRESS)
    verify(testSuiteView).onTestCaseStarted(device, testSuite.copy(testCaseCount = 1), testCase)

    // End Journey action (failed)
    adaptor.processEvent(
      createAfterTestXml(
        "1.3",
        "1.2",
        "step_one",
        "Step One",
        "failing_journey",
        "FAILURE",
        "1748882500000",
        "1748882501000",
      ),
      testSuiteView,
    )

    verify(testSuiteView)
      .onTestCaseFinished(
        device,
        testSuite.copy(testCaseCount = 1),
        testCase.copy(
          result = AndroidTestCaseResult.FAILED,
          startTimestampMillis = 1748882500000,
          endTimestampMillis = 1748882501000,
        ),
      )

    // Suite finished (failed)
    adaptor.processEvent(
      createAfterSuiteXml(
        "1.2",
        "1.1",
        "failing_journey",
        "Failing Journey (emulator-5554)",
        "failing_journey",
        "FAILURE",
        "1748882499000",
        "1748882504000",
      ),
      testSuiteView,
    )

    verify(testSuiteView)
      .onTestSuiteFinished(
        device,
        testSuite.copy(testCaseCount = 1, result = AndroidTestSuiteResult.FAILED),
      )

    // End internal Gradle test suites
    adaptor.processEvent(
      createAfterSuiteXml(
        id = "1.1",
        parentId = ":app:validateDebugJourneysTest",
        name = "Gradle Test Executor 1",
        displayName = "Gradle Test Executor 1",
        className = "",
        resultType = "SUCCESS",
        startTime = "1748882181695",
        endTime = "1748882207721",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createAfterSuiteXml(
        id = ":app:validateDebugJourneysTest",
        parentId = "",
        name = "Gradle Test Run :app:validateDebugJourneysTest",
        displayName = "Gradle Test Run :app:validateDebugJourneysTest",
        className = "",
        resultType = "SUCCESS",
        startTime = "1748882181334",
        endTime = "1748882208103",
      ),
      testSuiteView,
    )

    // Verify final counts
    verify(testSuiteView, times(1)).onTestSuiteStarted(any(), any())
    verify(testSuiteView, times(1)).onTestCaseStarted(any(), any(), any())
    verify(testSuiteView, times(1)).onTestCaseFinished(any(), any(), any())
    verify(testSuiteView, times(1)).onTestSuiteFinished(any(), any())
  }

  @Test
  fun resultsReported_forJourneyTestThatRunsOnMultipleDevices() {
    val runConfiguration = mock<RunConfiguration>()
    val adaptor = AndroidTestSuiteViewAdaptor(runConfiguration)
    val testSuiteView = mock<AndroidTestSuiteView>()
    val device1 =
      AndroidDevice(
        id = "emulator-5554",
        deviceName = "emulator-5554",
        avdName = "",
        deviceType = AndroidDeviceType.LOCAL_EMULATOR,
        version = AndroidVersion.DEFAULT,
      )
    val deviceId1Stdout = "[additionalTestArtifacts]deviceId=emulator-5554"
    val encodedDeviceId1Stdout =
      Base64.getEncoder().encodeToString(deviceId1Stdout.encodeToByteArray())

    val device2 =
      AndroidDevice(
        id = "emulator-5556",
        deviceName = "emulator-5556",
        avdName = "",
        deviceType = AndroidDeviceType.LOCAL_EMULATOR,
        version = AndroidVersion.DEFAULT,
      )
    val deviceId2Stdout = "[additionalTestArtifacts]deviceId=emulator-5556"
    val encodedDeviceId2Stdout =
      Base64.getEncoder().encodeToString(deviceId2Stdout.encodeToByteArray())

    // Start internal Gradle test suites
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = ":app:validateDebugJourneysTest",
        parentId = "",
        name = "Gradle Test Run :app:validateDebugJourneysTest",
        displayName = "Gradle Test Run :app:validateDebugJourneysTest",
        className = "",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = "1.1",
        parentId = ":app:validateDebugJourneysTest",
        name = "Gradle Test Executor 1",
        displayName = "Gradle Test Executor 1",
        className = "",
      ),
      testSuiteView,
    )

    // Start Journey on device 1
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = "1.2",
        parentId = "1.1",
        name = "my_first_journey",
        displayName = "My Journey (emulator-5554)",
        className = "my_first_journey",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createOnOutputXml(
        id = "1.2",
        parentId = "1.1",
        name = "my_first_journey",
        displayName = "My Journey (emulator-5554)",
        className = "my_first_journey",
        content = encodedDeviceId1Stdout,
      ),
      testSuiteView,
    )

    val testSuite =
      AndroidTestSuite(
        id = "1.2",
        name = "my_first_journey",
        testCaseCount = 0,
        runConfiguration = runConfiguration,
      )
    verify(testSuiteView, times(1)).onTestSuiteScheduled(device1)
    verify(testSuiteView, times(1)).onTestSuiteStarted(device1, testSuite)

    // Start Journey on device 2
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = "1.4",
        parentId = "1.1",
        name = "my_first_journey",
        displayName = "My Journey (emulator-5556)",
        className = "my_first_journey",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createOnOutputXml(
        id = "1.4",
        parentId = "1.1",
        name = "my_first_journey",
        displayName = "My Journey (emulator-5556)",
        className = "my_first_journey",
        content = encodedDeviceId2Stdout,
      ),
      testSuiteView,
    )

    val testSuite2 =
      AndroidTestSuite(
        id = "1.4",
        name = "my_first_journey",
        testCaseCount = 0,
        runConfiguration = runConfiguration,
      )
    verify(testSuiteView, times(1)).onTestSuiteScheduled(device2)
    verify(testSuiteView, times(1)).onTestSuiteStarted(device2, testSuite2)

    // Start first Journey action on device 1
    adaptor.processEvent(
      createBeforeTestXml(
        id = "1.3",
        parentId = "1.2",
        name = "Open the navigation drawer",
        displayName = "Open the navigation drawer",
        className = "my_first_journey",
      ),
      testSuiteView,
    )

    val firstTestCase =
      AndroidTestCase(
        id = "1.3",
        methodName = "Open the navigation drawer",
        className = "my_first_journey",
        packageName = "my_first_journey",
        result = AndroidTestCaseResult.IN_PROGRESS,
      )
    verify(testSuiteView, times(1))
      .onTestCaseStarted(device1, testSuite.copy(testCaseCount = 1), firstTestCase)

    // End first Journey action on device 1
    adaptor.processEvent(
      createAfterTestXml(
        id = "1.3",
        parentId = "1.2",
        name = "Open the navigation drawer",
        displayName = "Open the navigation drawer",
        className = "my_first_journey",
        resultType = "SUCCESS",
        startTime = "1748882198880",
        endTime = "1748882204981",
      ),
      testSuiteView,
    )

    verify(testSuiteView, times(1))
      .onTestCaseFinished(
        device1,
        testSuite.copy(testCaseCount = 1),
        firstTestCase.copy(
          result = AndroidTestCaseResult.PASSED,
          startTimestampMillis = 1748882198880,
          endTimestampMillis = 1748882204981,
        ),
      )

    // Start first Journey action on device 2
    adaptor.processEvent(
      createBeforeTestXml(
        id = "1.5",
        parentId = "1.4",
        name = "Open the navigation drawer",
        displayName = "Open the navigation drawer",
        className = "my_first_journey",
      ),
      testSuiteView,
    )

    val testCase2 =
      AndroidTestCase(
        id = "1.5",
        methodName = "Open the navigation drawer",
        className = "my_first_journey",
        packageName = "my_first_journey",
        result = AndroidTestCaseResult.IN_PROGRESS,
      )
    verify(testSuiteView, times(1))
      .onTestCaseStarted(device2, testSuite2.copy(testCaseCount = 1), testCase2)

    // End Journey action on device 2
    adaptor.processEvent(
      createAfterTestXml(
        id = "1.5",
        parentId = "1.4",
        name = "Open the navigation drawer",
        displayName = "Open the navigation drawer",
        className = "my_first_journey",
        resultType = "SUCCESS",
        startTime = "1748882198880",
        endTime = "1748882204981",
      ),
      testSuiteView,
    )

    verify(testSuiteView, times(1))
      .onTestCaseFinished(
        device2,
        testSuite2.copy(testCaseCount = 1),
        testCase2.copy(
          result = AndroidTestCaseResult.PASSED,
          startTimestampMillis = 1748882198880,
          endTimestampMillis = 1748882204981,
        ),
      )

    // End Journey on device 1
    adaptor.processEvent(
      createAfterSuiteXml(
        id = "1.2",
        parentId = "1.1",
        name = "my_first_journey",
        displayName = "My Journey (emulator-5554)",
        className = "my_first_journey",
        resultType = "SUCCESS",
        startTime = "1748882181802",
        endTime = "1748882207719",
      ),
      testSuiteView,
    )

    verify(testSuiteView, times(1))
      .onTestSuiteFinished(
        device1,
        testSuite.copy(testCaseCount = 1, result = AndroidTestSuiteResult.PASSED),
      )

    // End Journey on device 2
    adaptor.processEvent(
      createAfterSuiteXml(
        id = "1.4",
        parentId = "1.1",
        name = "my_first_journey",
        displayName = "My Journey (emulator-5556)",
        className = "my_first_journey",
        resultType = "SUCCESS",
        startTime = "1748882181802",
        endTime = "1748882207719",
      ),
      testSuiteView,
    )

    verify(testSuiteView, times(1))
      .onTestSuiteFinished(
        device2,
        testSuite2.copy(testCaseCount = 1, result = AndroidTestSuiteResult.PASSED),
      )

    // End internal Gradle test suites
    adaptor.processEvent(
      createAfterSuiteXml(
        id = "1.1",
        parentId = ":app:validateDebugJourneysTest",
        name = "Gradle Test Executor 1",
        displayName = "Gradle Test Executor 1",
        className = "",
        resultType = "SUCCESS",
        startTime = "1748882181695",
        endTime = "1748882207721",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createAfterSuiteXml(
        id = ":app:validateDebugJourneysTest",
        parentId = "",
        name = "Gradle Test Run :app:validateDebugJourneysTest",
        displayName = "Gradle Test Run :app:validateDebugJourneysTest",
        className = "",
        resultType = "SUCCESS",
        startTime = "1748882181334",
        endTime = "1748882208103",
      ),
      testSuiteView,
    )

    // Verify that the internal Gradle test suites weren't reported - only the two Journey test
    // suites should have been.
    verify(testSuiteView, times(2)).onTestSuiteStarted(any(), any())
    verify(testSuiteView, times(2)).onTestSuiteFinished(any(), any())
  }

  @Test
  fun resultsIgnored_forJUnitTestThatDoesntReportDeviceId() {
    val runConfiguration = mock<RunConfiguration>()
    val adaptor = AndroidTestSuiteViewAdaptor(runConfiguration)
    val testSuiteView = mock<AndroidTestSuiteView>()

    val testEvents =
      listOf(
        createBeforeSuiteXml(
          ":app:testDebugUnitTest",
          "",
          name = "Gradle Test Run :app:testDebugUnitTest",
          displayName = "Gradle Test Run :app:testDebugUnitTest",
          className = "",
        ),
        createBeforeSuiteXml(
          id = "3.1",
          parentId = ":app:testDebugUnitTest",
          name = "Gradle Test Executor 3",
          displayName = "Gradle Test Executor 3",
          className = "",
        ),
        createBeforeSuiteXml(
          id = "3.2",
          parentId = "3.1",
          name = "com.example.journeysuxrdemo.ExampleUnitTest",
          displayName = "ExampleUnitTest",
          className = "com.example.journeysuxrdemo.ExampleUnitTest",
        ),
        createBeforeTestXml(
          id = "3.3",
          parentId = "3.2",
          name = "addition_isCorrect",
          displayName = "addition_isCorrect",
          className = "com.example.journeysuxrdemo.ExampleUnitTest",
        ),
        createAfterTestXml(
          id = "3.3",
          parentId = "3.2",
          name = "addition_isCorrect",
          displayName = "addition_isCorrect",
          className = "com.example.journeysuxrdemo.ExampleUnitTest",
          resultType = "SUCCESS",
          startTime = "1748882351266",
          endTime = "1748882355271",
        ),
        createAfterSuiteXml(
          id = "3.2",
          parentId = "3.1",
          name = "com.example.journeysuxrdemo.ExampleUnitTest",
          displayName = "ExampleUnitTest",
          className = "com.example.journeysuxrdemo.ExampleUnitTest",
          resultType = "SUCCESS",
          startTime = "1748882351246",
          endTime = "1748882355271",
        ),
        createAfterSuiteXml(
          id = "3.1",
          parentId = ":app:testDebugUnitTest",
          name = "Gradle Test Executor 3",
          displayName = "Gradle Test Executor 3",
          className = "",
          resultType = "SUCCESS",
          startTime = "1748882351244",
          endTime = "1748882355271",
        ),
        createAfterSuiteXml(
          id = ":app:testDebugUnitTest",
          parentId = "",
          name = "Gradle Test Run :app:testDebugUnitTest",
          displayName = "Gradle Test Run :app:testDebugUnitTest",
          className = "",
          resultType = "SUCCESS",
          startTime = "1748882350999",
          endTime = "1748882355277",
        ),
      )
    for (test in testEvents) {
      adaptor.processEvent(test, testSuiteView)
    }

    // There should be no interactions with the test view as the device id wasn't published
    verify(testSuiteView, times(0)).onTestSuiteScheduled(any())
    verify(testSuiteView, times(0)).onTestSuiteStarted(any(), any())
    verify(testSuiteView, times(0)).onTestCaseStarted(any(), any(), any())
    verify(testSuiteView, times(0)).onTestCaseFinished(any(), any(), any())
    verify(testSuiteView, times(0)).onTestSuiteFinished(any(), any())
  }

  @Test
  fun resultsReported_forScreenshotPreviewTestsWithNestedTestSuites() {
    val runConfiguration = mock<RunConfiguration>()
    val adaptor = AndroidTestSuiteViewAdaptor(runConfiguration)
    val testSuiteView = mock<AndroidTestSuiteView>()
    val expectedDevice =
      AndroidDevice(
        id = "Preview",
        deviceName = "Preview",
        avdName = "",
        deviceType = AndroidDeviceType.LOCAL_EMULATOR,
        version = AndroidVersion.DEFAULT,
      )
    val expectedTestSuite =
      AndroidTestSuite(
        id = "1.1",
        name = "Gradle Test Executor 1",
        testCaseCount = 0,
        result = null,
        runConfiguration = runConfiguration,
      )

    // Start internal Gradle test suites
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = ":app:validateDebugScreenshotTest",
        parentId = "",
        name = "Gradle Test Run :app:validateDebugScreenshotTest",
        displayName = "Gradle Test Run :app:validateDebugScreenshotTest",
        className = "",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = "1.1",
        parentId = ":app:validateDebugScreenshotTest",
        name = "Gradle Test Executor 1",
        displayName = "Gradle Test Executor 1",
        className = "",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createOnOutputXml(
        id = "1.1",
        parentId = ":app:validateDebugScreenshotTest",
        name = "Gradle Test Executor 1",
        displayName = "Gradle Test Executor 1",
        className = "",
        content =
          "W2FkZGl0aW9uYWxUZXN0QXJ0aWZhY3RzXWRldmljZUlkPVByZXZpZXcK", // [additionalTestArtifacts]deviceId=Preview
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createOnOutputXml(
        id = "1.1",
        parentId = ":app:validateDebugScreenshotTest",
        name = "Gradle Test Executor 1",
        displayName = "Gradle Test Executor 1",
        className = "",
        content =
          "W2FkZGl0aW9uYWxUZXN0QXJ0aWZhY3RzXWRldmljZURpc3BsYXlOYW1lPVByZXZpZXcK", // [additionalTestArtifacts]deviceDisplayName=Preview
      ),
      testSuiteView,
    )

    verify(testSuiteView, times(1)).onTestSuiteScheduled(expectedDevice)

    // Start nested test suites
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = "1.2",
        parentId = "1.1",
        name = "com.example.screenshottesting.ExamplePreviewsScreenshots",
        displayName = "com.example.screenshottesting.ExamplePreviewsScreenshots",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = "1.3",
        parentId = "1.2",
        name = "GreetingPreview",
        displayName = "GreetingPreview",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = "1.4",
        parentId = "1.3",
        name = "GreetingPreview",
        displayName = "GreetingPreview",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots",
      ),
      testSuiteView,
    )

    verify(testSuiteView, times(1)).onTestSuiteStarted(expectedDevice, expectedTestSuite)

    // Start first test
    adaptor.processEvent(
      createBeforeTestXml(
        id = "1.5",
        parentId = "1.4",
        name = "GreetingPreview",
        displayName = "GreetingPreview",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots",
      ),
      testSuiteView,
    )

    val expectedFirstTestCase =
      AndroidTestCase(
        id = "1.5",
        methodName = "GreetingPreview",
        className = "ExamplePreviewsScreenshots",
        packageName = "com.example.screenshottesting",
        result = AndroidTestCaseResult.IN_PROGRESS,
      )
    verify(testSuiteView, times(1))
      .onTestCaseStarted(
        expectedDevice,
        expectedTestSuite.copy(testCaseCount = 1),
        expectedFirstTestCase,
      )

    // Report artifacts
    adaptor.processEvent(
      createOnOutputXml(
        id = "1.5",
        parentId = "1.4",
        name = "GreetingPreview",
        displayName = "GreetingPreview",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots",
        content =
          Base64.getEncoder()
            .encodeToString(
              "[additionalTestArtifacts]PreviewScreenshot.newImagePath=ExamplePreviewsScreenshots/GreetingPreview_748aa731_0.png"
                .toByteArray()
            ),
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createOnOutputXml(
        id = "1.5",
        parentId = "1.4",
        name = "GreetingPreview",
        displayName = "GreetingPreview",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots",
        content =
          Base64.getEncoder()
            .encodeToString(
              "[additionalTestArtifacts]PreviewScreenshot.refImagePath=ExamplePreviewsScreenshots/GreetingPreview_238a7281_0.png"
                .toByteArray()
            ),
      ),
      testSuiteView,
    )

    // End first test
    adaptor.processEvent(
      createAfterTestXml(
        id = "1.5",
        parentId = "1.4",
        name = "GreetingPreview",
        displayName = "GreetingPreview",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots",
        resultType = "SUCCESS",
        startTime = "1749122532921",
        endTime = "1749122532948",
      ),
      testSuiteView,
    )

    verify(testSuiteView, times(1))
      .onTestCaseFinished(
        expectedDevice,
        expectedTestSuite.copy(testCaseCount = 1),
        expectedFirstTestCase.copy(
          result = AndroidTestCaseResult.PASSED,
          startTimestampMillis = 1749122532921,
          endTimestampMillis = 1749122532948,
          additionalTestArtifacts =
            mutableMapOf(
              "PreviewScreenshot.newImagePath" to
                "ExamplePreviewsScreenshots/GreetingPreview_748aa731_0.png",
              "PreviewScreenshot.refImagePath" to
                "ExamplePreviewsScreenshots/GreetingPreview_238a7281_0.png",
            ),
        ),
      )

    // End nested test suites
    adaptor.processEvent(
      createAfterSuiteXml(
        id = "1.4",
        parentId = "1.3",
        name = "GreetingPreview",
        displayName = "GreetingPreview",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots",
        resultType = "SUCCESS",
        startTime = "1749122530884",
        endTime = "1749122532949",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createAfterSuiteXml(
        id = "1.3",
        parentId = "1.2",
        name = "GreetingPreview",
        displayName = "GreetingPreview",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots",
        resultType = "SUCCESS",
        startTime = "1749122530882",
        endTime = "1749122532949",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createAfterSuiteXml(
        id = "1.2",
        parentId = "1.1",
        name = "com.example.screenshottesting.ExamplePreviewsScreenshots",
        displayName = "com.example.screenshottesting.ExamplePreviewsScreenshots",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots",
        resultType = "SUCCESS",
        startTime = "1749122530881",
        endTime = "1749122532949",
      ),
      testSuiteView,
    )

    // Start second set of nested test suites
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = "1.6",
        parentId = "1.1",
        name = "com.example.screenshottesting.ExamplePreviewsScreenshots2",
        displayName = "com.example.screenshottesting.ExamplePreviewsScreenshots2",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots2",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = "1.7",
        parentId = "1.6",
        name = "GreetingPreview",
        displayName = "GreetingPreview",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots2",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createBeforeSuiteXml(
        id = "1.8",
        parentId = "1.7",
        name = "GreetingPreview",
        displayName = "GreetingPreview",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots2",
      ),
      testSuiteView,
    )

    // Start second test
    adaptor.processEvent(
      createBeforeTestXml(
        id = "1.9",
        parentId = "1.8",
        name = "GreetingPreview",
        displayName = "GreetingPreview",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots2",
      ),
      testSuiteView,
    )

    val expectedSecondTestCase =
      AndroidTestCase(
        id = "1.9",
        methodName = "GreetingPreview",
        className = "ExamplePreviewsScreenshots2",
        packageName = "com.example.screenshottesting",
        result = AndroidTestCaseResult.IN_PROGRESS,
      )
    verify(testSuiteView, times(1))
      .onTestCaseStarted(
        expectedDevice,
        expectedTestSuite.copy(testCaseCount = 2),
        expectedSecondTestCase,
      )

    // Report artifacts
    adaptor.processEvent(
      createOnOutputXml(
        id = "1.9",
        parentId = "1.8",
        name = "GreetingPreview",
        displayName = "GreetingPreview",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots2",
        content =
          Base64.getEncoder()
            .encodeToString(
              "[additionalTestArtifacts]PreviewScreenshot.newImagePath=ExamplePreviewsScreenshots2/GreetingPreview_748aa731_0.png"
                .toByteArray()
            ),
      ),
      testSuiteView,
    )

    // End second test
    adaptor.processEvent(
      createAfterTestXml(
        id = "1.9",
        parentId = "1.8",
        name = "GreetingPreview",
        displayName = "GreetingPreview",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots2",
        resultType = "FAILURE",
        startTime = "1749122532994",
        endTime = "1749122533001",
      ),
      testSuiteView,
    )

    verify(testSuiteView, times(1))
      .onTestCaseFinished(
        expectedDevice,
        expectedTestSuite.copy(testCaseCount = 2),
        expectedSecondTestCase.copy(
          result = AndroidTestCaseResult.FAILED,
          startTimestampMillis = 1749122532994,
          endTimestampMillis = 1749122533001,
          additionalTestArtifacts =
            mutableMapOf(
              "PreviewScreenshot.newImagePath" to
                "ExamplePreviewsScreenshots2/GreetingPreview_748aa731_0.png"
            ),
        ),
      )

    // End second set of nested test suites
    adaptor.processEvent(
      createAfterSuiteXml(
        id = "1.8",
        parentId = "1.7",
        name = "GreetingPreview",
        displayName = "GreetingPreview",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots2",
        resultType = "FAILURE",
        startTime = "1749122532951",
        endTime = "1749122533001",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createAfterSuiteXml(
        id = "1.7",
        parentId = "1.6",
        name = "GreetingPreview",
        displayName = "GreetingPreview",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots2",
        resultType = "FAILURE",
        startTime = "1749122532950",
        endTime = "1749122533001",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createAfterSuiteXml(
        id = "1.6",
        parentId = "1.1",
        name = "com.example.screenshottesting.ExamplePreviewsScreenshots2",
        displayName = "com.example.screenshottesting.ExamplePreviewsScreenshots2",
        className = "com.example.screenshottesting.ExamplePreviewsScreenshots2",
        resultType = "FAILURE",
        startTime = "1749122532950",
        endTime = "1749122533001",
      ),
      testSuiteView,
    )

    // End internal Gradle test suites
    adaptor.processEvent(
      createAfterSuiteXml(
        id = "1.1",
        parentId = ":app:validateDebugScreenshotTest",
        name = "Gradle Test Executor 1",
        displayName = "Gradle Test Executor 1",
        className = "",
        resultType = "FAILURE",
        startTime = "1749122530285",
        endTime = "1749122533057",
      ),
      testSuiteView,
    )
    adaptor.processEvent(
      createAfterSuiteXml(
        id = ":app:validateDebugScreenshotTest",
        parentId = "",
        name = "Gradle Test Run :app:validateDebugScreenshotTest",
        displayName = "Gradle Test Run :app:validateDebugScreenshotTest",
        className = "",
        resultType = "FAILURE",
        startTime = "1749122529964",
        endTime = "1749122533463",
      ),
      testSuiteView,
    )

    verify(testSuiteView, times(1))
      .onTestSuiteFinished(
        expectedDevice,
        expectedTestSuite.copy(testCaseCount = 2, result = AndroidTestSuiteResult.FAILED),
      )

    // Both tests share the same root test suite, so the test suite callbacks should have only been
    // called once
    verify(testSuiteView, times(1)).onTestSuiteStarted(eq(expectedDevice), any())
    verify(testSuiteView, times(1)).onTestSuiteFinished(eq(expectedDevice), any())
  }

  private fun createBeforeSuiteXml(
    id: String,
    parentId: String,
    name: String,
    displayName: String,
    className: String,
  ): TestEventXPPXmlView {
    return TestEventXPPXmlView(
      """
        <ijLog>
            <event type="beforeSuite">
                <ijLogEol/>
                <test id="$id" parentId="$parentId">
                    <ijLogEol/>
                    <descriptor className="$className" displayName="$displayName" name="$name"/>
                    <ijLogEol/>
                </test>
                <ijLogEol/>
            </event>
        </ijLog>
      """
        .trimIndent()
    )
  }

  private fun createOnOutputXml(
    id: String,
    parentId: String,
    name: String,
    displayName: String,
    className: String,
    content: String,
  ): TestEventXPPXmlView {
    return TestEventXPPXmlView(
      """
        <ijLog>
            <event type='onOutput'>
                <ijLogEol/>
                <test id='$id' parentId='$parentId'>
                    <ijLogEol/>
                    <descriptor name='$name' displayName='$displayName' className='$className' />
                    <ijLogEol/>
                    <event destination='StdOut'><![CDATA[$content]]></event>
                    <ijLogEol/>
                </test>
                <ijLogEol/>
            </event>
        </ijLog>
      """
        .trimIndent()
    )
  }

  private fun createBeforeTestXml(
    id: String,
    parentId: String,
    name: String,
    displayName: String,
    className: String,
  ): TestEventXPPXmlView {
    return TestEventXPPXmlView(
      """
        <ijLog>
            <event type="beforeTest">
                <ijLogEol/>
                <test id="$id" parentId="$parentId">
                    <ijLogEol/>
                    <descriptor className="$className" displayName="$displayName" name="$name"/>
                    <ijLogEol/>
                </test>
                <ijLogEol/>
            </event>
        </ijLog>
      """
        .trimIndent()
    )
  }

  private fun createAfterTestXml(
    id: String,
    parentId: String,
    name: String,
    displayName: String,
    className: String,
    resultType: String,
    startTime: String,
    endTime: String,
  ): TestEventXPPXmlView {
    return TestEventXPPXmlView(
      """
        <ijLog>
            <event type="afterTest">
                <ijLogEol/>
                <test id="$id" parentId="$parentId">
                    <ijLogEol/>
                    <descriptor className="$className" displayName="$displayName" name="$name"/>
                    <ijLogEol/>
                    <result endTime="$endTime" resultType="$resultType" startTime="$startTime">
                        <ijLogEol/>
                        <failureType>error</failureType>
                        <ijLogEol/>
                    </result>
                    <ijLogEol/>
                </test>
                <ijLogEol/>
            </event>
        </ijLog>
      """
        .trimIndent()
    )
  }

  private fun createAfterSuiteXml(
    id: String,
    parentId: String,
    name: String,
    displayName: String,
    className: String,
    resultType: String,
    startTime: String,
    endTime: String,
  ): TestEventXPPXmlView {
    return TestEventXPPXmlView(
      """
        <ijLog>
            <event type='afterSuite'>
                <ijLogEol/>
                <test id='$id' parentId='$parentId'>
                    <ijLogEol/>
                    <descriptor name='$name' displayName='$displayName' className='$className' />
                    <ijLogEol/>
                    <result resultType='$resultType' startTime='$startTime' endTime='$endTime'>
                        <ijLogEol/>
                        <failureType>error</failureType>
                        <ijLogEol/>
                    </result>
                    <ijLogEol/>
                </test>
                <ijLogEol/>
            </event>
        </ijLog>
      """
        .trimIndent()
    )
  }
}

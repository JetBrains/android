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

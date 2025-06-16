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

import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestCase
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidTestSuite
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.mockito.AdditionalAnswers.delegatesTo
import org.mockito.Mockito.doAnswer
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for [GradleAndroidTestsExecutionConsoleOutputProcessor].
 */
@RunsInEdt
class GradleAndroidTestsExecutionConsoleOutputProcessorTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModel().onEdt()

  @Test
  fun processOutput() {
    val userDataHolder = UserDataHolderBase()
    val mockAndroidTestSuite = mock<AndroidTestSuiteView>()
    doAnswer(delegatesTo<UserDataHolderEx>(userDataHolder)).`when`(mockAndroidTestSuite).getUserData<Any>(any())
    doAnswer(delegatesTo<UserDataHolderEx>(userDataHolder))
      .`when`(mockAndroidTestSuite).putUserDataIfAbsent<Any>(any(), any())

    exampleIJLogMessages.forEach { text ->
      GradleAndroidTestsExecutionConsoleOutputProcessor.onOutput(
        mockAndroidTestSuite,
        text,
        ProcessOutputTypes.STDOUT
      )
    }

    val device = argumentCaptor<AndroidDevice>()
    val testCaseResult = argumentCaptor<AndroidTestCase>()
    val testResult = argumentCaptor<AndroidTestSuite>()

    mockAndroidTestSuite.inOrder {
      verify(mockAndroidTestSuite).onTestSuiteScheduled(any())
      verify(mockAndroidTestSuite).onTestSuiteStarted(any(), any())
      verify(mockAndroidTestSuite).onTestCaseStarted(any(), any(), any())
      verify(mockAndroidTestSuite).onTestCaseFinished(any(), any(), testCaseResult.capture())
      verify(mockAndroidTestSuite).onTestSuiteFinished(device.capture(), testResult.capture())
    }

    assertThat(device.firstValue.deviceName).isEqualTo("Preview")
    assertThat(testCaseResult.firstValue.methodName).isEqualTo("GreetingPreview1")
    assertThat(testCaseResult.firstValue.additionalTestArtifacts).containsKey("PreviewScreenshot.newImagePath")
    assertThat(testResult.firstValue.testCaseCount).isEqualTo(1)
  }

  @Test
  fun processOutput_usesExtensionPointAdaptorWhenAvailable() {
    // Register a test provider as an extension that returns mock adaptor
    val mockRunConfiguration = mock<RunConfiguration>()
    val mockAdaptor = mock<TestSuiteViewAdaptor>()
    val testProvider = object : TestSuiteViewAdaptorProvider {
      override fun getAdaptor(runConfiguration: RunConfiguration?): TestSuiteViewAdaptor? {
        return if (runConfiguration === mockRunConfiguration) mockAdaptor else null
      }
    }

    val ep = ExtensionPointName.create<TestSuiteViewAdaptorProvider>(TestSuiteViewAdaptorProvider.ADAPTOR_PROVIDER_EP_NAME.name)
    ep.point.registerExtension(testProvider, projectRule.fixture.testRootDisposable)

    val userDataHolder = UserDataHolderBase()
    val mockAndroidTestSuite = mock<AndroidTestSuiteView>()
    doAnswer(delegatesTo<UserDataHolderEx>(userDataHolder)).`when`(mockAndroidTestSuite).getUserData<Any>(any())
    doAnswer(delegatesTo<UserDataHolderEx>(userDataHolder))
      .`when`(mockAndroidTestSuite).putUserDataIfAbsent<Any>(any(), any())
    whenever(mockAndroidTestSuite.runConfiguration).thenReturn(mockRunConfiguration)

    GradleAndroidTestsExecutionConsoleOutputProcessor.onOutput(
      mockAndroidTestSuite,
      exampleIJLogMessages.first(),
      ProcessOutputTypes.STDOUT
    )

    // Verify that the extension-provided mock adaptor was stored in UserData
    val storedAdaptor = userDataHolder.getUserData(GradleAndroidTestsExecutionConsoleOutputProcessor.ADAPTOR_KEY)
    assertThat(storedAdaptor).isEqualTo(mockAdaptor)

    // Verify that our extension-provided mock adaptor was the one used
    verify(mockAdaptor).processEvent(any(), any())
  }

  @Test
  fun processOutput_usesDefaultAdaptorWhenNoProviderMatches() {
    // Register a provider that will not match the run configuration
    val mockRunConfiguration = mock<RunConfiguration>()
    val nonMatchingProvider = object : TestSuiteViewAdaptorProvider {
      override fun getAdaptor(runConfiguration: RunConfiguration?): TestSuiteViewAdaptor? {
        return null
      }
    }

    val ep = ExtensionPointName.create<TestSuiteViewAdaptorProvider>(TestSuiteViewAdaptorProvider.ADAPTOR_PROVIDER_EP_NAME.name)
    ep.point.registerExtension(nonMatchingProvider, projectRule.fixture.testRootDisposable)

    val userDataHolder = UserDataHolderBase()
    val mockAndroidTestSuite = mock<AndroidTestSuiteView>()
    doAnswer(delegatesTo<UserDataHolderEx>(userDataHolder)).`when`(mockAndroidTestSuite).getUserData<Any>(any())
    doAnswer(delegatesTo<UserDataHolderEx>(userDataHolder))
      .`when`(mockAndroidTestSuite).putUserDataIfAbsent<Any>(any(), any())
    whenever(mockAndroidTestSuite.runConfiguration).thenReturn(mockRunConfiguration)

    GradleAndroidTestsExecutionConsoleOutputProcessor.onOutput(
      mockAndroidTestSuite,
      exampleIJLogMessages.first(),
      ProcessOutputTypes.STDOUT
    )

    // Retrieve the adaptor that was stored in UserData and verify it's the default
    val storedAdaptor = userDataHolder.getUserData(GradleAndroidTestsExecutionConsoleOutputProcessor.ADAPTOR_KEY)
    assertThat(storedAdaptor).isNotNull()
    assertThat(storedAdaptor).isInstanceOf(AndroidTestSuiteViewAdaptor::class.java)
  }

  @Test
  fun processOutput_usesCachedAdaptorIfAvailable() {
    val mockAdaptor = mock<TestSuiteViewAdaptor>()
    val userDataHolder = UserDataHolderBase()
    // Pre-cache the adaptor in the user data. This simulates it being set by a previous call
    userDataHolder.putUserData(GradleAndroidTestsExecutionConsoleOutputProcessor.ADAPTOR_KEY, mockAdaptor)

    val mockAndroidTestSuite = mock<AndroidTestSuiteView>()
    doAnswer(delegatesTo<UserDataHolderEx>(userDataHolder)).`when`(mockAndroidTestSuite).getUserData<Any>(any())
    doAnswer(delegatesTo<UserDataHolderEx>(userDataHolder))
      .`when`(mockAndroidTestSuite).putUserDataIfAbsent<Any>(any(), any())

    GradleAndroidTestsExecutionConsoleOutputProcessor.onOutput(
      mockAndroidTestSuite,
      exampleIJLogMessages.first(),
      ProcessOutputTypes.STDOUT
    )

    // Verify that our pre-cached adaptor was the one used
    verify(mockAdaptor).processEvent(any(), any())
  }


  private val exampleIJLogMessages = listOf(
    """
      <ijLog>
        <event type='beforeSuite'>
          <ijLogEol/>
          <test id=':app:validateDebugScreenshotTest' parentId=''>
            <ijLogEol/>
            <descriptor name='Gradle Test Run :app:validateDebugScreenshotTest' displayName='Gradle Test Run :app:validateDebugScreenshotTest'
                        className=''/>
            <ijLogEol/>
          </test>
          <ijLogEol/>
        </event>
      </ijLog>
    """,
    """
      <ijLog>
        <event type='beforeSuite'>
          <ijLogEol/>
          <test id='1.1' parentId=':app:validateDebugScreenshotTest'>
            <ijLogEol/>
            <descriptor name='Gradle Test Executor 1' displayName='Gradle Test Executor 1' className=''/>
            <ijLogEol/>
          </test>
          <ijLogEol/>
        </event>
      </ijLog>
    """,
    """
      <ijLog>
        <event type='onOutput'>
          <ijLogEol/>
          <test id='1.1' parentId=':app:validateDebugScreenshotTest'>
            <ijLogEol/>
            <descriptor name='Gradle Test Executor 1' displayName='Gradle Test Executor 1' className=''/>
            <ijLogEol/>
            <event destination='StdOut'><![CDATA[W2FkZGl0aW9uYWxUZXN0QXJ0aWZhY3RzXWRldmljZUlkPVByZXZpZXcK]]></event>
            <ijLogEol/>
          </test>
          <ijLogEol/>
        </event>
      </ijLog>
    """,
    """
      <ijLog>
        <event type='onOutput'>
          <ijLogEol/>
          <test id='1.1' parentId=':app:validateDebugScreenshotTest'>
            <ijLogEol/>
            <descriptor name='Gradle Test Executor 1' displayName='Gradle Test Executor 1' className=''/>
            <ijLogEol/>
            <event destination='StdOut'><![CDATA[W2FkZGl0aW9uYWxUZXN0QXJ0aWZhY3RzXWRldmljZURpc3BsYXlOYW1lPVByZXZpZXcK]]></event>
            <ijLogEol/>
          </test>
          <ijLogEol/>
        </event>
      </ijLog>
    """,
    """
      <ijLog>
        <event type='beforeSuite'>
          <ijLogEol/>
          <test id='1.2' parentId='1.1'>
            <ijLogEol/>
            <descriptor name='com.example.mysimplepreviewscreenshotexample.PreviewTestKt'
                        displayName='com.example.mysimplepreviewscreenshotexample.PreviewTestKt'
                        className='com.example.mysimplepreviewscreenshotexample.PreviewTestKt'/>
            <ijLogEol/>
          </test>
          <ijLogEol/>
        </event>
      </ijLog>
    """,
    """
      <ijLog>
        <event type='beforeSuite'>
          <ijLogEol/>
          <test id='1.3' parentId='1.2'>
            <ijLogEol/>
            <descriptor name='GreetingPreview1' displayName='GreetingPreview1'
                        className='com.example.mysimplepreviewscreenshotexample.PreviewTestKt'/>
            <ijLogEol/>
          </test>
          <ijLogEol/>
        </event>
      </ijLog>
    """,
    """
      <ijLog>
        <event type='beforeSuite'>
          <ijLogEol/>
          <test id='1.4' parentId='1.3'>
            <ijLogEol/>
            <descriptor name='GreetingPreview1' displayName='GreetingPreview1'
                        className='com.example.mysimplepreviewscreenshotexample.PreviewTestKt'/>
            <ijLogEol/>
          </test>
          <ijLogEol/>
        </event>
      </ijLog>
    """,
    """
      <ijLog>
        <event type='beforeTest'>
          <ijLogEol/>
          <test id='1.5' parentId='1.4'>
            <ijLogEol/>
            <descriptor name='GreetingPreview1'
                        displayName='GreetingPreview1'
                        className='com.example.mysimplepreviewscreenshotexample.PreviewTestKt' />
            <ijLogEol/>
          </test>
          <ijLogEol/>
        </event>
      </ijLog>
    """,
    """
      <ijLog>
        <event type='onOutput'>
          <ijLogEol/>
          <test id='1.5' parentId='1.4'>
            <ijLogEol/>
            <descriptor name='GreetingPreview1' displayName='GreetingPreview1'
                        className='com.example.mysimplepreviewscreenshotexample.PreviewTestKt'/>
            <ijLogEol/>
            <event destination='StdOut'>
              <![CDATA[W2FkZGl0aW9uYWxUZXN0QXJ0aWZhY3RzXVByZXZpZXdTY3JlZW5zaG90Lm5ld0ltYWdlUGF0aD0vdXNyL2xvY2FsL2dvb2dsZS9ob21lL2h1bW1lci9BbmRyb2lkU3R1ZGlvUHJvamVjdHMvTXlTaW1wbGVQcmV2aWV3U2NyZWVuc2hvdEV4YW1wbGUvYXBwL2J1aWxkL291dHB1dHMvc2NyZWVuc2hvdFRlc3QtcmVzdWx0cy9wcmV2aWV3L2RlYnVnL3JlbmRlcmVkL2NvbS9leGFtcGxlL215c2ltcGxlcHJldmlld3NjcmVlbnNob3RleGFtcGxlL1ByZXZpZXdUZXN0S3QvR3JlZXRpbmdQcmV2aWV3MV8wLnBuZwo=]]></event>
            <ijLogEol/>
          </test>
          <ijLogEol/>
        </event>
      </ijLog>
    """,
    """
      <ijLog>
        <event type='onOutput'>
          <ijLogEol/>
          <test id='1.5' parentId='1.4'>
            <ijLogEol/>
            <descriptor name='GreetingPreview1' displayName='GreetingPreview1'
                        className='com.example.mysimplepreviewscreenshotexample.PreviewTestKt'/>
            <ijLogEol/>
            <event destination='StdOut'>
              <![CDATA[W2FkZGl0aW9uYWxUZXN0QXJ0aWZhY3RzXVByZXZpZXdTY3JlZW5zaG90LnJlZkltYWdlUGF0aD0vdXNyL2xvY2FsL2dvb2dsZS9ob21lL2h1bW1lci9BbmRyb2lkU3R1ZGlvUHJvamVjdHMvTXlTaW1wbGVQcmV2aWV3U2NyZWVuc2hvdEV4YW1wbGUvYXBwL3NyYy9kZWJ1Z1NjcmVlbnNob3RUZXN0L3JlZmVyZW5jZS9jb20vZXhhbXBsZS9teXNpbXBsZXByZXZpZXdzY3JlZW5zaG90ZXhhbXBsZS9QcmV2aWV3VGVzdEt0L0dyZWV0aW5nUHJldmlldzFfMC5wbmcK]]></event>
            <ijLogEol/>
          </test>
          <ijLogEol/>
        </event>
      </ijLog>
    """,
    """
      <ijLog>
        <event type='afterTest'>
          <ijLogEol/>
          <test id='1.5' parentId='1.4'>
            <ijLogEol/>
            <descriptor name='GreetingPreview1' displayName='GreetingPreview1'
                        className='com.example.mysimplepreviewscreenshotexample.PreviewTestKt'/>
            <ijLogEol/>
            <result resultType='SUCCESS' startTime='1741904110893' endTime='1741904110918'>
              <ijLogEol/>
              <failureType>error</failureType>
              <ijLogEol/>
            </result>
            <ijLogEol/>
          </test>
          <ijLogEol/>
        </event>
      </ijLog>
    """,
    """
      <ijLog>
        <event type='afterSuite'>
          <ijLogEol/>
          <test id='1.4' parentId='1.3'>
            <ijLogEol/>
            <descriptor name='GreetingPreview1' displayName='GreetingPreview1'
                        className='com.example.mysimplepreviewscreenshotexample.PreviewTestKt'/>
            <ijLogEol/>
            <result resultType='SUCCESS' startTime='1741904109151' endTime='1741904110919'>
              <ijLogEol/>
              <failureType>error</failureType>
              <ijLogEol/>
            </result>
            <ijLogEol/>
          </test>
          <ijLogEol/>
        </event>
      </ijLog>
    """,
    """
      <ijLog>
        <event type='afterSuite'>
          <ijLogEol/>
          <test id='1.3' parentId='1.2'>
            <ijLogEol/>
            <descriptor name='GreetingPreview1' displayName='GreetingPreview1'
                        className='com.example.mysimplepreviewscreenshotexample.PreviewTestKt'/>
            <ijLogEol/>
            <result resultType='SUCCESS' startTime='1741904109148' endTime='1741904110920'>
              <ijLogEol/>
              <failureType>error</failureType>
              <ijLogEol/>
            </result>
            <ijLogEol/>
          </test>
          <ijLogEol/>
        </event>
      </ijLog>
    """,
    """
      <ijLog>
        <event type='afterSuite'>
          <ijLogEol/>
          <test id='1.2' parentId='1.1'>
            <ijLogEol/>
            <descriptor name='com.example.mysimplepreviewscreenshotexample.PreviewTestKt'
                        displayName='com.example.mysimplepreviewscreenshotexample.PreviewTestKt'
                        className='com.example.mysimplepreviewscreenshotexample.PreviewTestKt'/>
            <ijLogEol/>
            <result resultType='SUCCESS' startTime='1741904109148' endTime='1741904110920'>
              <ijLogEol/>
              <failureType>error</failureType>
              <ijLogEol/>
            </result>
            <ijLogEol/>
          </test>
          <ijLogEol/>
        </event>
      </ijLog>
    """,
    """
      <ijLog>
        <event type='afterSuite'>
          <ijLogEol/>
          <test id='1.1' parentId=':app:validateDebugScreenshotTest'>
            <ijLogEol/>
            <descriptor name='Gradle Test Executor 1' displayName='Gradle Test Executor 1' className=''/>
            <ijLogEol/>
            <result resultType='SUCCESS' startTime='1741904109148' endTime='1741904110920'>
              <ijLogEol/>
              <failureType>error</failureType>
              <ijLogEol/>
            </result>
            <ijLogEol/>
          </test>
          <ijLogEol/>
        </event>
      </ijLog>
    """,
    """
      <ijLog>
        <event type='afterSuite'>
          <ijLogEol/>
          <test id=':app:validateDebugScreenshotTest' parentId=''>
            <ijLogEol/>
            <descriptor name='Gradle Test Run :app:validateDebugScreenshotTest' displayName='Gradle Test Run :app:validateDebugScreenshotTest'
                        className=''/>
            <ijLogEol/>
            <result resultType='SUCCESS' startTime='1741904109148' endTime='1741904110920'>
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
  )
}
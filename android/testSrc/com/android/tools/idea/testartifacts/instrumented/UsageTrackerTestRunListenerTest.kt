/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented

import com.android.ddmlib.IDevice
import com.android.ddmlib.testrunner.InstrumentationResultParser
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.model.IdeTestOptions
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.stats.AnonymizerUtil
import com.android.tools.idea.stats.UsageTrackerTestRunListener
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.setupTestProjectFromAndroidModel
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.TestLibraries
import com.google.wireless.android.sdk.stats.TestRun
import com.intellij.testFramework.PlatformTestCase
import org.mockito.Mockito.mock

class UsageTrackerTestRunListenerTest : PlatformTestCase() {
  private val serial = "my serial"

  private fun checkLoggedEvent(instrumentationOutput: String, block: (AndroidStudioEvent) -> Unit) {
    val tracker = TestUsageTracker(VirtualTimeScheduler())
    UsageTracker.setWriterForTest(tracker)
    try {
      setupTestProjectFromAndroidModel(
        project,
        Projects.getBaseDirPath(project),
        true,
        JavaModuleModelBuilder.rootModuleBuilder,
        AndroidModuleModelBuilder(":moduleName", "debug", AndroidProjectBuilder())
      )

      val module = project.gradleModule(":moduleName")

      val listener = UsageTrackerTestRunListener(
        module!!.getModuleSystem().getTestLibrariesInUse(),
        AndroidModel.get(module)!!.testOptions!!.executionOption,
        mock(IDevice::class.java)!!.also {
          whenever(it.serialNumber).thenReturn(serial)
        }
      )

      InstrumentationResultParser(UsageTrackerTestRunListener::class.qualifiedName, listener).run {
        processNewLines(instrumentationOutput.lines().toTypedArray())
        done()
      }

      block.invoke(tracker.usages.last().studioEvent)
    } finally {
      UsageTracker.cleanAfterTesting()
    }
  }

  fun testNormalRun() {
    checkLoggedEvent("""
        INSTRUMENTATION_STATUS: numtests=2
        INSTRUMENTATION_STATUS: stream=
        com.example.bendowski.androidplayground.ExampleInstrumentedTest:
        INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
        INSTRUMENTATION_STATUS: test=outerTest2
        INSTRUMENTATION_STATUS: class=com.example.bendowski.androidplayground.ExampleInstrumentedTest
        INSTRUMENTATION_STATUS: current=1
        INSTRUMENTATION_STATUS_CODE: 1
        INSTRUMENTATION_STATUS: numtests=2
        INSTRUMENTATION_STATUS: stream=.
        INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
        INSTRUMENTATION_STATUS: test=outerTest2
        INSTRUMENTATION_STATUS: class=com.example.bendowski.androidplayground.ExampleInstrumentedTest
        INSTRUMENTATION_STATUS: current=1
        INSTRUMENTATION_STATUS_CODE: 0
        INSTRUMENTATION_STATUS: numtests=2
        INSTRUMENTATION_STATUS: stream=
        INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
        INSTRUMENTATION_STATUS: test=outerTest
        INSTRUMENTATION_STATUS: class=com.example.bendowski.androidplayground.ExampleInstrumentedTest
        INSTRUMENTATION_STATUS: current=2
        INSTRUMENTATION_STATUS_CODE: 1
        INSTRUMENTATION_STATUS: numtests=2
        INSTRUMENTATION_STATUS: stream=.
        INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
        INSTRUMENTATION_STATUS: test=outerTest
        INSTRUMENTATION_STATUS: class=com.example.bendowski.androidplayground.ExampleInstrumentedTest
        INSTRUMENTATION_STATUS: current=2
        INSTRUMENTATION_STATUS_CODE: 0
        INSTRUMENTATION_RESULT: stream=

        Time: 0.012

        OK (2 tests)


        INSTRUMENTATION_CODE: -1
        """.trimIndent()
    ) { event ->
      assertThat(event.category).isEqualTo(AndroidStudioEvent.EventCategory.TESTS)
      assertThat(event.deviceInfo.anonymizedSerialNumber).isEqualTo(AnonymizerUtil.anonymizeUtf8(serial))
      assertThat(event.hasProductDetails()).isTrue()
      assertThat(event.testRun).isEqualTo(TestRun.newBuilder().run {
        numberOfTestsExecuted = 2
        testExecution = TestRun.TestExecution.HOST
        testKind = TestRun.TestKind.INSTRUMENTATION_TEST
        testInvocationType = TestRun.TestInvocationType.ANDROID_STUDIO_TEST
        testLibraries = TestLibraries.getDefaultInstance()
        build()
      })
    }
  }

  fun testInstrumentationFailed() {
    checkLoggedEvent("""
        android.util.AndroidException: INSTRUMENTATION_FAILED: com.example.bendowski.androidplayground.test/android.support.test.runner.AndroidJUnitRunner
                at com.android.commands.am.Instrument.run(Instrument.java:410)
                at com.android.commands.am.Am.runInstrument(Am.java:232)
                at com.android.commands.am.Am.onRun(Am.java:125)
                at com.android.internal.os.BaseCommand.run(BaseCommand.java:54)
                at com.android.commands.am.Am.main(Am.java:95)
                at com.android.internal.os.RuntimeInit.nativeFinishInit(Native Method)
                at com.android.internal.os.RuntimeInit.main(RuntimeInit.java:284)
        INSTRUMENTATION_STATUS: id=ActivityManagerService
        INSTRUMENTATION_STATUS: Error=Unable to find instrumentation target package: com.example.bendowski.androidplayground
        INSTRUMENTATION_STATUS_CODE: -1
      """.trimIndent()
    ) { event ->
      assertThat(event.testRun).isEqualTo(TestRun.newBuilder().run {
        numberOfTestsExecuted = 0
        testExecution = TestRun.TestExecution.HOST
        testKind = TestRun.TestKind.INSTRUMENTATION_TEST
        testInvocationType = TestRun.TestInvocationType.ANDROID_STUDIO_TEST
        crashed = true
        testLibraries = TestLibraries.getDefaultInstance()
        build()
      })
    }
  }
}

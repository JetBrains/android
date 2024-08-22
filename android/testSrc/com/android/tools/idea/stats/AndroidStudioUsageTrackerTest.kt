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
package com.android.tools.idea.stats

import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.analytics.AnalyticsSettings.optedIn
import com.android.tools.analytics.AnalyticsSettings.userId
import com.android.tools.analytics.AnalyticsSettingsData
import com.android.tools.analytics.HostData.graphicsEnvironment
import com.android.tools.analytics.HostData.osBean
import com.android.tools.analytics.deviceToDeviceInfo
import com.android.tools.analytics.stubs.StubDateProvider
import com.android.tools.analytics.stubs.StubGraphicsDevice.Companion.withBounds
import com.android.tools.analytics.stubs.StubGraphicsEnvironment
import com.android.tools.analytics.stubs.StubOperatingSystemMXBean
import com.android.tools.idea.mendel.MendelFlagsProvider
import com.android.tools.idea.stats.AndroidStudioUsageTracker.buildActiveExperimentList
import com.android.tools.idea.stats.AndroidStudioUsageTracker.deviceToDeviceInfoApiLevelOnly
import com.android.tools.idea.stats.AndroidStudioUsageTracker.getMachineDetails
import com.android.tools.idea.stats.AndroidStudioUsageTracker.shouldRequestUserSentiment
import com.android.tools.idea.stats.FeatureSurveys.shouldInvokeFeatureSurvey
import com.android.utils.DateProvider
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.DeviceInfo
import com.google.wireless.android.sdk.stats.DisplayDetails
import com.google.wireless.android.sdk.stats.MachineDetails
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.registerExtension
import junit.framework.TestCase
import org.junit.Assert
import java.awt.GraphicsDevice
import java.io.File
import java.time.ZoneOffset
import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone
import java.util.Calendar

class AndroidStudioUsageTrackerTest : BasePlatformTestCase() {
  private val mockMendelFlagsProvider: MendelFlagsProvider = mock()

  override fun setUp() {
    super.setUp()
    ApplicationManager.getApplication().registerExtension(
      MendelFlagsProvider.EP_NAME, mockMendelFlagsProvider, testRootDisposable
    )
  }

  fun testDeviceToDeviceInfo() {
    val info = deviceToDeviceInfo(createMockDevice())
    assertEquals(info.anonymizedSerialNumber, AnonymizerUtil.anonymizeUtf8("serial"))
    assertEquals(info.buildTags, "release-keys")
    assertEquals(info.buildType, "userdebug")
    assertEquals(info.buildVersionRelease, "5.1.1")
    assertEquals(info.buildApiLevelFull, "24")
    assertEquals(info.cpuAbi, DeviceInfo.ApplicationBinaryInterface.X86_ABI)
    assertEquals(info.manufacturer, "Samsung")
    assertTrue(info.deviceType == DeviceInfo.DeviceType.LOCAL_PHYSICAL)
    assertEquals(info.model, "pixel")
    assertEquals(info.characteristicsList, listOf("emulator", "watch"))
  }

  fun testDeviceToDeviceInfoApiLevelOnly() {
    val info = deviceToDeviceInfoApiLevelOnly(createMockDevice())
    // Test only Api Level is set
    assertEquals(info.buildApiLevelFull, "24")
    assertEquals(info.anonymizedSerialNumber, "")
  }

  fun testGetMachineDetails() {
    // Use the file root to get a consistent disk size
    // (we normally use the studio install path).
    val root = File(File.separator)
    // Stub out the Operating System MX Bean to get consistent system info in the test.
    osBean = object : StubOperatingSystemMXBean() {
      override fun getAvailableProcessors(): Int {
        return 16
      }

      override fun getTotalPhysicalMemorySize(): Long {
        return 16L * 1024 * 1024 * 1024
      }
    }

    // Stub out the Graphics Environment to get consistent screen sizes in the test.
    graphicsEnvironment = object : StubGraphicsEnvironment() {
      override fun getScreenDevices(): Array<GraphicsDevice> {
        return arrayOf(
          withBounds(640, 480),
          withBounds(1024, 768)
        )
      }

      override fun isHeadlessInstance(): Boolean {
        return false
      }
    }
    try {
      val expected = MachineDetails.newBuilder()
        .setAvailableProcessors(16)
        .setTotalRam(16L * 1024 * 1024 * 1024)
        .setTotalDisk(root.totalSpace)
        .addDisplay(DisplayDetails.newBuilder().setWidth(640).setHeight(480).setSystemScale(1.0f))
        .addDisplay(DisplayDetails.newBuilder().setWidth(1024).setHeight(768).setSystemScale(1.0f))
        .build()
      val result = getMachineDetails(root)
      Assert.assertEquals(expected, result)
    }
    finally {
      // undo the stubbing of Operating System MX Bean.
      osBean = null
      // undo the stubbing of Graphics Environment.
      graphicsEnvironment = null
    }
  }

  fun testBuildActiveExperimentListContainsStudioExperiments() {
    try {
      System.setProperty(AndroidStudioUsageTracker.STUDIO_EXPERIMENTS_OVERRIDE, "")
      Truth.assertThat(buildActiveExperimentList()).hasSize(0)
      System.setProperty(AndroidStudioUsageTracker.STUDIO_EXPERIMENTS_OVERRIDE, "single")
      Truth.assertThat(buildActiveExperimentList()).containsExactly("single")
      System.setProperty(AndroidStudioUsageTracker.STUDIO_EXPERIMENTS_OVERRIDE, "one,two")
      Truth.assertThat(buildActiveExperimentList()).containsExactly("one", "two")
    }
    finally {
      System.setProperty(AndroidStudioUsageTracker.STUDIO_EXPERIMENTS_OVERRIDE, "")
    }
  }

  fun testBuildActiveExperimentListContainsMendelExperiments() {
    try {
      whenever(mockMendelFlagsProvider.getActiveExperimentIds()).thenReturn(emptyList())
      Truth.assertThat(buildActiveExperimentList()).hasSize(0)

      whenever(mockMendelFlagsProvider.getActiveExperimentIds()).thenReturn(listOf(1))
      Truth.assertThat(buildActiveExperimentList()).containsExactly("1")

      whenever(mockMendelFlagsProvider.getActiveExperimentIds()).thenReturn(listOf(1, 2))
      Truth.assertThat(buildActiveExperimentList()).containsExactly("1", "2")
    }
    finally {
      whenever(mockMendelFlagsProvider.getActiveExperimentIds()).thenReturn(emptyList())
    }
  }

  fun testBuildActiveExperimentListContainsBoth() {
    try {
      whenever(mockMendelFlagsProvider.getActiveExperimentIds()).thenReturn(listOf(1))
      System.setProperty(AndroidStudioUsageTracker.STUDIO_EXPERIMENTS_OVERRIDE, "single")
      Truth.assertThat(buildActiveExperimentList()).containsExactly("1", "single")
    }
    finally {
      whenever(mockMendelFlagsProvider.getActiveExperimentIds()).thenReturn(emptyList())
      System.setProperty(AndroidStudioUsageTracker.STUDIO_EXPERIMENTS_OVERRIDE, "")
    }
  }

  fun testShouldRequestUserSentiment() {
    try {
      // opted out user should not request user sentiment
      AnalyticsSettings.dateProvider = StubDateProvider(2016, 4, 18)
      AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
        userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
        optedIn = false
      })
      Assert.assertFalse(shouldRequestUserSentiment())

      // opted in user who never was asked should be asked on matching day
      AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
        userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
        optedIn = true
      })
      Assert.assertTrue(shouldRequestUserSentiment())

      // opted in user who was asked more than a year ago should be asked on matching day
      AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
        userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
        optedIn = true
        lastSentimentAnswerDate = Date(115, 4, 17)
      })
      Assert.assertTrue(shouldRequestUserSentiment())

      // 91 days ago
      val calendar: Calendar = Calendar.getInstance()
      calendar.setTime(AnalyticsSettings.dateProvider.now())
      calendar.add(Calendar.DAY_OF_YEAR, -91)

      // opted in user who was asked more than the frequency set
      AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
        userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
        optedIn = true
        popSentimentQuestionFrequency=90
        lastSentimentAnswerDate = calendar.getTime()
        lastSentimentQuestionDate = calendar.getTime()
      })
      Assert.assertTrue(shouldRequestUserSentiment())

      // opted in user who was asked less than the frequency set
      AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
        userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
        optedIn = true
        popSentimentQuestionFrequency=90
        lastSentimentAnswerDate = AnalyticsSettings.dateProvider.now()
        lastSentimentQuestionDate = AnalyticsSettings.dateProvider.now()
      })
      Assert.assertFalse(shouldRequestUserSentiment())

      // opted in user who was asked less than a year ago should not be asked
      AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
        userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
        optedIn = true
        lastSentimentAnswerDate = Date(115, 4, 20)
      })
      Assert.assertFalse(shouldRequestUserSentiment())

      // opted in user who was asked more than a year ago should not be asked on non-matching day
      AnalyticsSettings.dateProvider = StubDateProvider(2016, 5, 19)
      AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
        userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
        optedIn = true
        lastSentimentAnswerDate = Date(115, 4, 18)
      })
      Assert.assertFalse(shouldRequestUserSentiment())

      // opted in user who was asked today but didn't answer, should not be prompted again today
      AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
        userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
        optedIn = true
        lastSentimentQuestionDate = Date(116, 5, 19)
      })
      Assert.assertFalse(shouldRequestUserSentiment())

      // opted in user who was asked recently but didn't answer, should be prompted again on a later date
      AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
        userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
        optedIn = true
        lastSentimentQuestionDate = Date(116, 5, 11)
      })
      Assert.assertTrue(shouldRequestUserSentiment())

      // opted in user who was asked recently but didn't answer, should be not be prompted until 7 days passed.
      AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
        userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
        optedIn = true
        lastSentimentQuestionDate = Date(116, 5, 18)
        lastSentimentAnswerDate = Date(115, 5, 10)
      })
      Assert.assertFalse(shouldRequestUserSentiment())

      // opted in user who was asked recently but didn't answer, should be prompted again on a later date, when also answered last year
      AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
        userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
        optedIn = true
        lastSentimentQuestionDate = Date(116, 5, 11)
        lastSentimentAnswerDate = Date(115, 5, 10)
      })
      Assert.assertTrue(shouldRequestUserSentiment())

      // opted in user who was got asked and answered recently should not be asked again
      AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
        userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
        optedIn = true
        lastSentimentQuestionDate = Date(116, 5, 19)
        lastSentimentAnswerDate = Date(116, 5, 19)
      })
      Assert.assertFalse(shouldRequestUserSentiment())

    }
    finally {
      AnalyticsSettings.dateProvider = DateProvider.SYSTEM
    }
  }

  fun testShouldInvokeFeatureSurvey() {
    // opted out user should not request user sentiment
    AnalyticsSettings.dateProvider = StubDateProvider(2020, 4, 18)
    AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
      userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
      optedIn = false
    })
    assertFalse(shouldInvokeFeatureSurvey("featureSurvey"))

    //  shouldInvokeFeatureSurvey should return false before general interval elapses
    AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
      userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
      optedIn = true
      nextFeatureSurveyDate = Date(120, 4, 19)
      nextFeatureSurveyDateMap = mutableMapOf("featureSurvey" to Date(120, 4, 17))
    })
    assertFalse(shouldInvokeFeatureSurvey("featureSurvey"))

    //  shouldInvokeFeatureSurvey should return false before specific interval elapses
    AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
      userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
      optedIn = true
      nextFeatureSurveyDate = Date(120, 4, 17)
      nextFeatureSurveyDateMap = mutableMapOf("featureSurvey" to Date(120, 4, 19))
    })
    assertFalse(shouldInvokeFeatureSurvey("featureSurvey"))

    // shouldInvokeFeatureSurvey should return true after both intervals elapse
    AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
      userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
      optedIn = true
      nextFeatureSurveyDate = Date(120, 4, 17)
      nextFeatureSurveyDateMap = mutableMapOf("featureSurvey" to Date(120, 4, 17))
    })
    assertTrue(shouldInvokeFeatureSurvey("featureSurvey"))
    FeatureSurveys.FeatureSurveyChoiceLogger.log("featureSurvey", 0)
  }

  fun testFeatureSurveyResponded() {
    AnalyticsSettings.dateProvider = StubDateProvider(2020, 4, 18)
    AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
      userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
      optedIn = true
    })

    assertTrue(shouldInvokeFeatureSurvey("featureSurvey"))
    FeatureSurveys.FeatureSurveyChoiceLogger.log("featureSurvey", 0)
    assertFalse(shouldInvokeFeatureSurvey("featureSurvey"))

    var calendar = GregorianCalendar(2020, 4, 18 + DEFAULT_FEATURE_SURVEY_CONFIG.generalIntervalCompleted)
    calendar.timeZone = TimeZone.getTimeZone(ZoneOffset.UTC)
    assertEquals(calendar.time, AnalyticsSettings.nextFeatureSurveyDate)

    calendar = GregorianCalendar(2020, 4, 18 + DEFAULT_FEATURE_SURVEY_CONFIG.specificIntervalCompleted)
    calendar.timeZone = TimeZone.getTimeZone(ZoneOffset.UTC)
    assertEquals(calendar.time, AnalyticsSettings.nextFeatureSurveyDateMap?.let { it["featureSurvey"] })
  }

  fun testFeatureSurveyCancelled() {
    AnalyticsSettings.dateProvider = StubDateProvider(2020, 4, 18)
    AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
      userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
      optedIn = true
    })

    assertTrue(shouldInvokeFeatureSurvey("featureSurvey"))
    FeatureSurveys.FeatureSurveyChoiceLogger.cancel("featureSurvey")
    assertFalse(shouldInvokeFeatureSurvey("featureSurvey"))

    var calendar = GregorianCalendar(2020, 4, 18 + DEFAULT_FEATURE_SURVEY_CONFIG.generalIntervalCancelled)
    calendar.timeZone = TimeZone.getTimeZone(ZoneOffset.UTC)
    assertEquals(calendar.time, AnalyticsSettings.nextFeatureSurveyDate)

    calendar = GregorianCalendar(2020, 4, 18 + DEFAULT_FEATURE_SURVEY_CONFIG.specificIntervalCancelled)
    calendar.timeZone = TimeZone.getTimeZone(ZoneOffset.UTC)
    assertEquals(calendar.time, AnalyticsSettings.nextFeatureSurveyDateMap?.let { it["featureSurvey"] })
  }

  fun testHasUserBeenPromptedForOptin() {
    AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
      userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
      lastOptinPromptVersion = null
    })

    assertFalse(AnalyticsSettings.hasUserBeenPromptedForOptin("2021", "2"))

    AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
      userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
      lastOptinPromptVersion = "invalid"
    })

    assertFalse(AnalyticsSettings.hasUserBeenPromptedForOptin("2021", "2"))

    AnalyticsSettings.setInstanceForTest(AnalyticsSettingsData().apply {
      userId = "db3dd15b-053a-4066-ac93-04c50585edc2"
      lastOptinPromptVersion = "2021.1"
    })

    assertTrue(AnalyticsSettings.hasUserBeenPromptedForOptin("2021", "0"))
    assertTrue(AnalyticsSettings.hasUserBeenPromptedForOptin("2021", "1"))
    assertFalse(AnalyticsSettings.hasUserBeenPromptedForOptin("2021", "2"))
    assertFalse(AnalyticsSettings.hasUserBeenPromptedForOptin("2022", "0"))
  }

  companion object {
    fun createMockDevice(): IDevice {
      val mockDevice = mock<IDevice>()
      whenever(mockDevice.serialNumber).thenReturn("serial")
      whenever(mockDevice.getProperty(IDevice.PROP_BUILD_TAGS)).thenReturn("release-keys")
      whenever(mockDevice.getProperty(IDevice.PROP_BUILD_TYPE)).thenReturn("userdebug")
      whenever(mockDevice.getProperty(IDevice.PROP_BUILD_VERSION)).thenReturn("5.1.1")
      whenever(mockDevice.getProperty(IDevice.PROP_BUILD_API_LEVEL)).thenReturn("24")
      whenever(mockDevice.getProperty(IDevice.PROP_DEVICE_CPU_ABI)).thenReturn("x86")
      whenever(mockDevice.getProperty(IDevice.PROP_DEVICE_MANUFACTURER)).thenReturn("Samsung")
      whenever(mockDevice.isEmulator).thenReturn(java.lang.Boolean.FALSE)
      whenever(mockDevice.getProperty(IDevice.PROP_DEVICE_MODEL)).thenReturn("pixel")
      whenever(mockDevice.getHardwareCharacteristics()).thenReturn(setOf("emulator", "watch"))
      return mockDevice
    }
  }
}
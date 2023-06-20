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
package com.android.tools.idea.run.activity.launch

import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.tools.deployer.model.App
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.activity.ActivityLocator
import com.android.tools.idea.run.configuration.execution.createApp
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

class DefaultActivityLaunchTest : AndroidTestCase() {
  lateinit var apk: String
  lateinit var state: DefaultActivityLaunch.State
  lateinit var device: IDevice
  lateinit var app: App

  override fun setUp() {
    super.setUp()
    // apkWithDefaultActivity.apk contains simple project with basic activity `com.example.myapplication.MainActivity`.
    apk = "${myFixture.testDataPath}/configurations/activity/apkWithDefaultActivity.apk"
    state = DefaultActivityLaunch.State()
    device = Mockito.mock(IDevice::class.java)
    app = createApp(device, "com.example.myapplication", emptyList(), ArrayList(setOf("com.example.myapplication.MainActivity")))
  }


  fun testLaunch() {
    state.launch(device, app, TestApksProvider(apk, "com.example.myapplication"), false, "", EmptyTestConsoleView())

    Mockito.verify(device).executeShellCommand(
      ArgumentMatchers.eq(
        "am start -n com.example.myapplication/com.example.myapplication.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"),
      ArgumentMatchers.any(IShellOutputReceiver::class.java),
      ArgumentMatchers.eq(15L),
      ArgumentMatchers.eq(TimeUnit.SECONDS))
  }

  fun testLaunchWithMultipleApks() {
    val multiApkProvider = TestApksProvider(listOf(
      ApkInfo(File("non-existent-apk-irrelevant-for-test"), "com.example.other.application"),
      ApkInfo(File(apk), "com.example.myapplication"),
    ))

    state.launch(device, app, multiApkProvider, false, "", EmptyTestConsoleView())

    Mockito.verify(device).executeShellCommand(
      ArgumentMatchers.eq(
        "am start -n com.example.myapplication/com.example.myapplication.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"),
      ArgumentMatchers.any(IShellOutputReceiver::class.java),
      ArgumentMatchers.eq(15L),
      ArgumentMatchers.eq(TimeUnit.SECONDS))
  }

  fun testLaunchWithNoMatchingApks() {
    val multiApkProvider = TestApksProvider(listOf(
      ApkInfo(File("non-existent-apk-1-irrelevant-for-test"), "com.example.other.application1"),
      ApkInfo(File("non-existent-apk-2-irrelevant-for-test"), "com.example.other.application2"),
    ))

    val exception = assertFailsWith<IllegalStateException> {
      state.launch(device, app, multiApkProvider, false, "", EmptyTestConsoleView())
    }

    assertThat(exception.message).isEqualTo("No matching APK for application: com.example.myapplication\n")
  }

  fun testLaunchWithMultipleMatchingApks() {
    val multiApkProvider = TestApksProvider(listOf(
      ApkInfo(File("non-existent-apk-1-irrelevant-for-test"), "com.example.myapplication"),
      ApkInfo(File("non-existent-apk-2-irrelevant-for-test"), "com.example.myapplication"),
    ))

    val exception = assertFailsWith<IllegalStateException> {
      state.launch(device, app, multiApkProvider, false, "", EmptyTestConsoleView())
    }

    assertThat(exception.message).isEqualTo(
      """Multiple APKs present for application: com.example.myapplication
Projects:
  com.example.myapplication containing :
    non-existent-apk-1-irrelevant-for-test
  com.example.myapplication containing :
    non-existent-apk-2-irrelevant-for-test
""")
  }


  fun testLaunchWithNoApks() {
    val emptyApkProvider = TestApksProvider(emptyList())

    val exception = assertFailsWith<ActivityLocator.ActivityLocatorException> {
      state.launch(device, app, emptyApkProvider, false, "", EmptyTestConsoleView())
    }

    assertThat(exception.message).isEqualTo("No APKs provided. Unable to extract default activity")
  }

  private class TestApksProvider(private val apks: Collection<ApkInfo>) : ApkProvider {
    constructor(apkFile: String, appId: String) :
      this(listOf(ApkInfo(File(apkFile), appId)))

    override fun getApks(device: IDevice) = apks
  }
}
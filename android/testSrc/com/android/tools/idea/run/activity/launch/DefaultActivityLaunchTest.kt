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
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.run.configuration.execution.createApp
import org.jetbrains.android.AndroidTestCase
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.io.File
import java.util.concurrent.TimeUnit

class DefaultActivityLaunchTest : AndroidTestCase() {
  fun testLaunch() {
    val state = DefaultActivityLaunch.State()
    val device = Mockito.mock(IDevice::class.java)
    val config = Mockito.spy(
      AndroidRunConfigurationType.getInstance().factory.createTemplateConfiguration(project) as AndroidRunConfiguration)

    /**
     * apkWithDefaultActivity.apk contains simple project with basic activity `com.example.myapplication.MainActivity`.
     */
    val apk = "${myFixture.testDataPath}/configurations/activity/apkWithDefaultActivity.apk"

    Mockito.doReturn(TestApksProvider(apk, "com.example.myapplication")).`when`(config).apkProvider

    val app = createApp(device, "com.example.myapplication", emptyList(), ArrayList(setOf("com.example.myapplication.MainActivity")))
    state.launch(device, app, config, false, "", EmptyTestConsoleView())
    Mockito.verify(device).executeShellCommand(
      ArgumentMatchers.eq(
        "am start -n com.example.myapplication/com.example.myapplication.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"),
      ArgumentMatchers.any(IShellOutputReceiver::class.java),
      ArgumentMatchers.eq(15L),
      ArgumentMatchers.eq(TimeUnit.SECONDS))
  }

  private class TestApksProvider(private val apkFile: String, private val appId: String) : ApkProvider {
    @Throws(ApkProvisionException::class)
    override fun getApks(device: IDevice): Collection<ApkInfo> {
      return listOf(ApkInfo(File(apkFile), appId))
    }

    override fun validate(): List<ValidationError> {
      return ArrayList()
    }
  }
}
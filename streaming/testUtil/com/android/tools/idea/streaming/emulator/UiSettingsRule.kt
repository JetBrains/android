/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator

import com.android.adblib.DeviceSelector
import com.android.adblib.testing.FakeAdbDeviceServices
import com.android.adblib.testing.FakeAdbSession
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.adblib.testing.TestAdbLibService
import com.android.tools.idea.testing.ProjectServiceRule
import com.android.tools.idea.testing.disposable
import com.intellij.testFramework.ProjectRule
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Supplies fakes for UI settings tests
 */
class UiSettingsRule(emulatorPort: Int) : ExternalResource() {
  private val projectRule = ProjectRule()
  private val serviceRule = ProjectServiceRule(projectRule, AdbLibService::class.java, TestAdbLibService(FakeAdbSession()))

  val project
    get() = projectRule.project

  val testRootDisposable
    get() = projectRule.disposable

  val adb: FakeAdbDeviceServices
    get() = AdbLibService.getSession(project).deviceServices as FakeAdbDeviceServices

  val issuedChangeCommands: List<String>
    get() = adb.shellV2Requests.map { it.command }

  val emulatorSerialNumber = "emulator-$emulatorPort"
  val deviceSelector = DeviceSelector.fromSerialNumber(emulatorSerialNumber)

  override fun before() {
    configureAdbShellCommands()
  }

  private fun configureAdbShellCommands() {
    adb.configureShellCommand(deviceSelector, "cmd uimode night", "Night mode: no")
  }

  override fun apply(base: Statement, description: Description): Statement =
    projectRule.apply(serviceRule.apply(super.apply(base, description), description), description)
}

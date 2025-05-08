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
package com.android.tools.idea.adb

import com.android.tools.idea.adblib.AdbLibApplicationService
import org.junit.rules.ExternalResource

/**
 * TestRule that works in conjunction with [com.intellij.testFramework.ProjectRule] to force
 * `AndroidDebugBridge.preInit` method call to be performed earlier in a test lifecycle.
 *
 * [AdbLibApplicationService] sets up a correct version of `AndroidDebugBridgeDelegate` by calling
 * `AndroidDebugBridge.preInit` method. This call needs to happen before `FakeAdbRule` is used to
 * set up fake test devices.
 *
 * Note that this rule is not needed when relying on
 * `com.android.tools.idea.testing.AndroidProjectRule` as it initializes [AdbLibApplicationService]
 * early through [AdbLibApplicationService.MyStartupActivity].
 */
class PreInitAndroidDebugBridgeRule : ExternalResource() {
  override fun before() {
    AdbLibApplicationService.instance
  }
}

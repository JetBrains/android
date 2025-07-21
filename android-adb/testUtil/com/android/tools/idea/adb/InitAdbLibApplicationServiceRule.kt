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

import com.android.ddmlib.AdbInitOptions
import com.android.ddmlib.AndroidDebugBridge
import com.android.tools.idea.adblib.AdbLibApplicationService
import java.util.concurrent.TimeUnit
import org.junit.rules.ExternalResource

/**
 * This test rule addresses two scenarios:
 * 1. **Early `AndroidDebugBridge.preInit` Call:** When used with
 *    `com.intellij.testFramework.ProjectRule`, this rule forces `AndroidDebugBridge.preInit` to be
 *    called earlier in the test lifecycle. This is crucial because `AdbLibApplicationService` sets
 *    up the correct `AndroidDebugBridgeDelegate` via `preInit`, and this setup must occur before
 *    `FakeAdbRule` is used to establish fake test devices. Note that this rule is unnecessary when
 *    using `com.android.tools.idea.testing.AndroidProjectRule`, as it initializes
 *    `AdbLibApplicationService` early through `AdbLibApplicationService.MyStartupActivity`.
 * 2. **`createBridge` for Real ADB Server:** In unit tests we need to setup `AndroidDebugBridge`
 *    through a call to `createBridge`. Usually this is done by one of the FakeAdb rules, but you
 *    need to use this rule when a real adb server process was started, and we need to make the rest
 *    of adblib use it.
 */
class InitAdbLibApplicationServiceRule : ExternalResource() {

  override fun before() {
    // Instantiate `AdbLibApplicationService`, which initializes adblib's application-level
    // components such as `AdbSession` and `ChannelProvider`.
    AdbLibApplicationService.instance
    AndroidDebugBridge.enableFakeAdbServerMode(5037)

    val adbInitOptions = AdbInitOptions.builder().setClientSupportEnabled(true)
    AndroidDebugBridge.init(adbInitOptions.build())

    AndroidDebugBridge.createBridge(10, TimeUnit.SECONDS)
      ?: error("TestRule could not create ADB bridge ")
  }

  override fun after() {
    AndroidDebugBridge.disconnectBridge()
    AndroidDebugBridge.terminate()
  }
}

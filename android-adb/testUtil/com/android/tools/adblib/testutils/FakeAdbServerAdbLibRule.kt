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
package com.android.tools.adblib.testutils

import com.android.adblib.ddmlibcompatibility.testutils.InitAndroidDebugBridgeRule
import com.android.adblib.testingutils.FakeAdbServerRule
import com.android.fakeadbserver.FakeAdbServer
import com.android.tools.idea.adblib.AdbLibApplicationService
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * The test rule that combines [FakeAdbServerRule] and [InitAndroidDebugBridgeRule] to allow
 * `AdbLibApplicationService` to be used in tests.
 */
class FakeAdbServerAdbLibRule(configure: (FakeAdbServer.Builder.() -> Unit)? = null) :
  FakeAdbServerRule(configure) {

  override fun before() {
    super.before()
    // Instantiate `AdbLibApplicationService`, which initializes adblib's application-level
    // components such as `AdbSession` and `ChannelProvider`.
    AdbLibApplicationService.instance
  }

  override fun apply(base: Statement, description: Description): Statement {
    return super.apply(
      InitAndroidDebugBridgeRule(alsoCreateBridge = true) { adbServer.port }
        .apply(base, description),
      description,
    )
  }
}

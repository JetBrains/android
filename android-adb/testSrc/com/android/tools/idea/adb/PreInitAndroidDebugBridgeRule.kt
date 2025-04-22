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

class PreInitAndroidDebugBridgeRule() : ExternalResource() {
  override fun before() {
    // Simply accessing `AdbLibApplicationService.instance` early enough triggers the creation on
    // `AdbServerController` and `AdbLibAndroidDebugBridge` if
    // `ADBLIB_MIGRATION_DDMLIB_ADB_DELEGATE` flag is on.
    AdbLibApplicationService.instance
  }
}

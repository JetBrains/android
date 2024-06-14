/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

sealed interface TestComponentType {
  val type: String

  sealed interface HostTest : TestComponentType
  sealed interface DeviceTest : TestComponentType
}

sealed interface CommonTestType : TestComponentType {
  /** Do not use this class directly: use the [TestComponentType.HostTest] interface or the [Companion] fields. */
  enum class HostTest(override val type: String) : CommonTestType, TestComponentType.HostTest {
    UNIT_TEST("_unit_test_"),
    SCREENSHOT_TEST("_screenshot_test_")
  }
  /** Do not use this class directly: use the [TestComponentType.DeviceTest] interface or the [Companion] fields. */
  enum class DeviceTest(override val type: String) : CommonTestType, TestComponentType.DeviceTest {
    ANDROID_TEST("_android_test_")
  }

  companion object {
    @JvmField val UNIT_TEST = HostTest.UNIT_TEST
    @JvmField val SCREENSHOT_TEST = HostTest.SCREENSHOT_TEST
    @JvmField val ANDROID_TEST = DeviceTest.ANDROID_TEST
  }
}

fun TestComponentType.scopeTypeByName(): ScopeType {
  return when (this) {
    CommonTestType.ANDROID_TEST -> ScopeType.ANDROID_TEST
    CommonTestType.UNIT_TEST -> ScopeType.UNIT_TEST
    CommonTestType.SCREENSHOT_TEST -> ScopeType.SCREENSHOT_TEST
    else -> error("unknown scope type $this")
  }
}
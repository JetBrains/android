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
}

enum class CommonTestType(override val type: String) : TestComponentType {
  UNIT_TEST("_unit_test_"),
  ANDROID_TEST("_android_test_"),
  SCREENSHOT_TEST("_screenshot_test_"),
}

data class CustomTestType(override val type: String) : TestComponentType {
  init {
    require(CommonTestType.values().none { it.type == type }) {
      "CustomTestType cannot be one of the CommonTestType"
    }
  }
  override fun toString(): String = "$type Test Artifact"
}

fun TestComponentType.scopeTypeByName(): ScopeType {
  return when (this) {
    CommonTestType.ANDROID_TEST -> ScopeType.ANDROID_TEST
    CommonTestType.UNIT_TEST -> ScopeType.UNIT_TEST
    CommonTestType.SCREENSHOT_TEST -> ScopeType.SCREENSHOT_TEST
    else -> error("unknown scope type $this")
  }
}
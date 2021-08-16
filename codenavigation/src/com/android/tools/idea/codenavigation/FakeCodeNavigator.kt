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
package com.android.tools.idea.codenavigation

/**
 * A test-only [CodeNavigator] that says every [CodeLocation] is navigable but does nothing when
 * asked to navigate to a [CodeLocation]. If a test actually cares about acting on navigation
 * requests, it should add a listener via [addListener].
 */
class FakeCodeNavigator : CodeNavigator() {
  override fun isNavigatable(location: CodeLocation): Boolean {
    return true
  }

  override fun handleNavigate(location: CodeLocation) {
    // No-op
  }
}
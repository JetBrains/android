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
package com.android.tools.idea.compose.preview.animation

import org.junit.Assert.*
import org.junit.Test

class AnimationClockTest {

  @Test
  fun animationClockWrapsComposeClockViaReflection() {
    val animationClock = AnimationClock(TestClock())
    // Check that we can find a couple of methods from TestClock
    assertNotNull(animationClock.getAnimatedPropertiesFunction)
    assertNotNull(animationClock.updateAnimatedVisibilityStateFunction)
    // getAnimatedVisibilityState is mangled in TestClock, but we should be able to find it.
    assertNotNull(animationClock.getAnimatedVisibilityStateFunction)

    try {
      // We should throw an Exception if we can't find the given function in the underlying clock,
      // and it's up to the caller to handle this.
      animationClock.findClockFunction("unknownFunction")
      fail("Expected to fail, as `unknownFunction` is not a function of TestClock.")
    } catch (ignored: NullPointerException) {}

    // getAnimatedVisibilityState is a supported function, but its name is mangled. We should find
    // it when looking for the function without
    // the hash suffix, not when we specify it.
    assertNotNull(animationClock.findClockFunction("getAnimatedVisibilityState"))
    try {
      animationClock.findClockFunction("getAnimatedVisibilityState-xga21d")
      fail(
        "Expected to fail, as `getAnimatedVisibilityState-xga21d` should not be found when looking for the mangled name."
      )
    } catch (ignored: NullPointerException) {}
  }
}

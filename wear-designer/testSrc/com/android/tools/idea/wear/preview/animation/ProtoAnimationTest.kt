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
package com.android.tools.idea.wear.preview.animation

import junit.framework.TestCase.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtoAnimationTest {

  @Test
  fun testValidAnimator() {
    val validAnimator =
      TestDynamicTypeAnimator() // Create an instance that implements the interface
    // Should not throw an exception
    ProtoAnimation(validAnimator)
  }

  @Test
  fun testInvalidAnimator() {
    val invalidAnimator = String() // Create an instance that DOES NOT implement the interface
    // Should throw an IllegalArgumentException
    val exception =
      assertThrows(IllegalArgumentException::class.java) { ProtoAnimation(invalidAnimator) }
    assertEquals("Animator must implement DynamicTypeAnimator interface", exception.message)
  }
}
